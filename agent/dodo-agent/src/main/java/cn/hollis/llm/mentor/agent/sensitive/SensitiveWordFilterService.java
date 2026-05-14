package cn.hollis.llm.mentor.agent.sensitive;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 基于 AC 自动机的敏感词过滤服务
 *
 * <p>词库热加载时先构建新的不可变自动机，再通过 volatile 一次性发布，避免过滤中的请求读到半初始化状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordFilterService {

    private static final char DEFAULT_MASK_CHAR = '*';

    private final SensitiveWordProperties properties;

    /** 当前生效的自动机 */
    private volatile AcAutomaton automaton = AcAutomaton.empty();

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("敏感词过滤未开启");
            return;
        }
        reload();
    }

    /**
     * 从 classpath 重新加载词库
     */
    public synchronized void reload() {
        List<String> words = loadWords();
        this.automaton = AcAutomaton.build(words);
        log.info("敏感词词库加载完成, 数量: {}", words.size());
    }

    /**
     * 检测文本中的敏感词，并生成用于日志排查的掩码文本
     *
     * @param text 待检测文本
     * @return 检测结果
     */
    public SensitiveWordFilterResult filter(String text) {
        if (!properties.isEnabled() || !StringUtils.hasText(text)) {
            return noHit(text);
        }

        AcAutomaton snapshot = automaton;
        if (snapshot.isEmpty()) {
            return noHit(text);
        }

        List<Hit> hits = findHits(snapshot, text);
        if (hits.isEmpty()) {
            return noHit(text);
        }

        return buildResult(text, hits);
    }

    private SensitiveWordFilterResult noHit(String text) {
        return new SensitiveWordFilterResult(text, text, false, List.of());
    }

    private List<Hit> findHits(AcAutomaton snapshot, String text) {
        String skipChars = properties.getSkipChars();
        if (!StringUtils.hasText(skipChars)) {
            return snapshot.match(text);
        }
        return matchWithSkipChars(snapshot, text, SkipCharMatcher.of(skipChars));
    }

    private SensitiveWordFilterResult buildResult(String text, List<Hit> hits) {
        boolean[] mask = new boolean[text.length()];
        Set<String> hitWords = new LinkedHashSet<>();

        for (Hit hit : hits) {
            hitWords.add(hit.word());
            for (int i = hit.start(); i < hit.end(); i++) {
                mask[i] = true;
            }
        }

        StringBuilder filtered = new StringBuilder(text);
        char maskChar = resolveMaskChar();
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                filtered.setCharAt(i, maskChar);
            }
        }

        return new SensitiveWordFilterResult(text, filtered.toString(), true, List.copyOf(hitWords));
    }

    private char resolveMaskChar() {
        String maskChar = properties.getMaskChar();
        if (!StringUtils.hasText(maskChar)) {
            return DEFAULT_MASK_CHAR;
        }
        return maskChar.charAt(0);
    }

    /**
     * 支持跳字的匹配
     *
     * <pre>
     * 词库=["赌博"], 文本="赌 博", skipChars=" "
     * 纯净文本="赌博", 命中坐标映射回原文为 [0, 3)
     * </pre>
     */
    private List<Hit> matchWithSkipChars(AcAutomaton snapshot, String text, SkipCharMatcher skipCharMatcher) {
        StringBuilder cleanText = new StringBuilder(text.length());
        List<Integer> originalPositions = new ArrayList<>(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipCharMatcher.isSkip(c)) {
                continue;
            }
            originalPositions.add(i);
            cleanText.append(c);
        }

        if (cleanText.isEmpty()) {
            return List.of();
        }

        List<Hit> cleanHits = snapshot.match(cleanText);
        if (cleanHits.isEmpty()) {
            return List.of();
        }

        List<Hit> hits = new ArrayList<>(cleanHits.size());
        for (Hit cleanHit : cleanHits) {
            int originalStart = originalPositions.get(cleanHit.start());
            int originalEnd = originalPositions.get(cleanHit.end() - 1) + 1;
            hits.add(new Hit(originalStart, originalEnd, cleanHit.word()));
        }
        return hits;
    }

    /**
     * 加载并清洗词库
     */
    private List<String> loadWords() {
        String dictionaryPath = properties.getDictionaryPath();
        if (!StringUtils.hasText(dictionaryPath)) {
            log.warn("敏感词词库路径为空");
            return List.of();
        }

        ClassPathResource resource = new ClassPathResource(dictionaryPath);
        if (!resource.exists()) {
            log.warn("敏感词词库不存在: {}", dictionaryPath);
            return List.of();
        }

        Set<String> words = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (StringUtils.hasText(word) && !word.startsWith("#")) {
                    words.add(word);
                }
            }
        } catch (Exception e) {
            log.error("加载敏感词词库失败", e);
        }

        return List.copyOf(words);
    }

    record Hit(int start, int end, String word) {
    }

    private static final class SkipCharMatcher {

        private final Set<Character> skipChars;

        private SkipCharMatcher(Set<Character> skipChars) {
            this.skipChars = skipChars;
        }

        static SkipCharMatcher of(String chars) {
            Set<Character> skipChars = new HashSet<>(chars.length());
            for (int i = 0; i < chars.length(); i++) {
                skipChars.add(chars.charAt(i));
            }
            return new SkipCharMatcher(skipChars);
        }

        boolean isSkip(char c) {
            return skipChars.contains(c);
        }
    }

    /**
     * 构建完成后只读的 AC 自动机
     */
    static final class AcAutomaton {

        private final List<Node> nodes;

        private AcAutomaton(List<Node> nodes) {
            this.nodes = nodes;
        }

        static AcAutomaton empty() {
            return new AcAutomaton(List.of(new Node()));
        }

        static AcAutomaton build(List<String> words) {
            List<Node> nodes = new ArrayList<>();
            nodes.add(new Node());

            for (String word : words) {
                if (StringUtils.hasText(word)) {
                    insert(nodes, word);
                }
            }

            buildFailPointers(nodes);
            return new AcAutomaton(List.copyOf(nodes));
        }

        boolean isEmpty() {
            return nodes.size() == 1 && nodes.get(0).outputs.isEmpty();
        }

        List<Hit> match(CharSequence text) {
            if (isEmpty() || text.isEmpty()) {
                return List.of();
            }

            List<Hit> hits = new ArrayList<>();
            int current = 0;

            for (int pos = 0; pos < text.length(); pos++) {
                char c = text.charAt(pos);
                current = nextState(current, c);
                Node node = nodes.get(current);
                if (!node.outputs.isEmpty()) {
                    addHits(hits, node.outputs, pos);
                }
            }

            return hits;
        }

        private int nextState(int current, char c) {
            while (current != 0 && !nodes.get(current).children.containsKey(c)) {
                current = nodes.get(current).fail;
            }
            return nodes.get(current).children.getOrDefault(c, 0);
        }

        private static void insert(List<Node> nodes, String word) {
            int current = 0;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                Node node = nodes.get(current);
                Integer next = node.children.get(c);
                if (next == null) {
                    next = nodes.size();
                    node.children.put(c, next);
                    nodes.add(new Node());
                }
                current = next;
            }
            nodes.get(current).outputs.add(word);
        }

        private static void buildFailPointers(List<Node> nodes) {
            Queue<Integer> queue = new ArrayDeque<>();

            for (int child : nodes.get(0).children.values()) {
                nodes.get(child).fail = 0;
                queue.add(child);
            }

            while (!queue.isEmpty()) {
                int current = queue.poll();
                Node currentNode = nodes.get(current);

                for (Map.Entry<Character, Integer> entry : currentNode.children.entrySet()) {
                    char c = entry.getKey();
                    int child = entry.getValue();
                    int fail = currentNode.fail;

                    while (fail != 0 && !nodes.get(fail).children.containsKey(c)) {
                        fail = nodes.get(fail).fail;
                    }

                    nodes.get(child).fail = nodes.get(fail).children.getOrDefault(c, 0);
                    nodes.get(child).outputs.addAll(nodes.get(nodes.get(child).fail).outputs);
                    queue.add(child);
                }
            }
        }

        private static void addHits(List<Hit> hits, List<String> outputs, int pos) {
            for (String word : outputs) {
                hits.add(new Hit(pos - word.length() + 1, pos + 1, word));
            }
        }

        private static final class Node {
            private final Map<Character, Integer> children = new HashMap<>(4);
            private final List<String> outputs = new ArrayList<>(1);
            private int fail;
        }
    }
}
