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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 DFA 的敏感词过滤服务
 * 启动时加载词库并构建前缀树
 * 运行时先做最长匹配再替换为掩码字符
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DfaSensitiveWordService {

    /**
     * 敏感词过滤配置
     */
    private final DfaSensitiveWordProperties properties;

    /**
     * DFA 根节点
     * key 是首字符
     */
    private volatile Map<Character, DfaNode> root = new ConcurrentHashMap<>();

    /**
     * 初始化词库
     * 容器启动后加载敏感词并构建 DFA
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("敏感词过滤未开启");
            return;
        }

        List<String> words = loadWords();
        buildTrie(words);
        log.info("敏感词词库加载完成, 数量: {}", words.size());
    }

    /**
     * 过滤文本中的敏感词
     * 使用最长匹配策略避免短词覆盖长词
     *
     * @param text 待过滤文本
     * @return 过滤结果
     */
    public SensitiveWordFilterResult filter(String text) {
        if (!properties.isEnabled() || !StringUtils.hasText(text)) {
            return new SensitiveWordFilterResult(text, text, false, List.of());
        }

        List<String> hitWords = new ArrayList<>();
        StringBuilder filteredText = new StringBuilder(text);
        char maskChar = properties.getMaskChar().charAt(0);

        int index = 0;
        while (index < text.length()) {
            MatchResult matchResult = findLongestMatch(text, index);
            if (!matchResult.hit()) {
                index++;
                continue;
            }

            hitWords.add(matchResult.word());
            for (int i = index; i < matchResult.endIndex(); i++) {
                filteredText.setCharAt(i, maskChar);
            }
            index = matchResult.endIndex();
        }

        List<String> distinctHitWords = new ArrayList<>(new LinkedHashSet<>(hitWords));
        return new SensitiveWordFilterResult(
                text,
                filteredText.toString(),
                !distinctHitWords.isEmpty(),
                distinctHitWords
        );
    }

    /**
     * 从指定位置开始查找最长命中的敏感词
     *
     * @param text 待匹配文本
     * @param startIndex 起始下标
     * @return 匹配结果
     */
    private MatchResult findLongestMatch(String text, int startIndex) {
        DfaNode currentNode = root.get(text.charAt(startIndex));
        if (currentNode == null) {
            return MatchResult.notHit();
        }

        int endIndex = -1;
        for (int i = startIndex; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            currentNode = i == startIndex ? currentNode : currentNode.children().get(currentChar);
            if (currentNode == null) {
                break;
            }
            if (currentNode.isWordEnd()) {
                endIndex = i + 1;
            }
        }

        if (endIndex == -1) {
            return MatchResult.notHit();
        }

        return new MatchResult(true, text.substring(startIndex, endIndex), endIndex);
    }

    /**
     * 从 classpath 读取敏感词词库
     * 支持跳过空行和注释行
     *
     * @return 敏感词列表
     */
    private List<String> loadWords() {
        List<String> words = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(properties.getDictionaryPath());
        if (!resource.exists()) {
            log.warn("敏感词词库不存在: {}", properties.getDictionaryPath());
            return words;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!StringUtils.hasText(word) || word.startsWith("#")) {
                    continue;
                }
                words.add(word);
            }
        } catch (Exception e) {
            log.error("加载敏感词词库失败", e);
        }

        return words;
    }

    /**
     * 根据词库构建 DFA 前缀树
     *
     * @param words 敏感词列表
     */
    private void buildTrie(List<String> words) {
        Map<Character, DfaNode> newRoot = new ConcurrentHashMap<>();
        for (String word : words) {
            if (!StringUtils.hasText(word)) {
                continue;
            }

            Map<Character, DfaNode> currentLevel = newRoot;
            DfaNode currentNode = null;
            for (char currentChar : word.toCharArray()) {
                currentNode = currentLevel.computeIfAbsent(currentChar, key -> new DfaNode());
                currentLevel = currentNode.children();
            }

            if (currentNode != null) {
                currentNode.setWordEnd(true);
            }
        }
        this.root = newRoot;
    }

    /**
     * 单次匹配结果
     *
     * @param hit 是否命中
     * @param word 命中的词
     * @param endIndex 命中结束位置
     */
    private record MatchResult(boolean hit, String word, int endIndex) {
        private static MatchResult notHit() {
            return new MatchResult(false, null, -1);
        }
    }

    /**
     * DFA 节点
     * children 保存下一个字符的分支
     * wordEnd 表示当前路径是否能组成一个完整敏感词
     */
    private static final class DfaNode {
        private final Map<Character, DfaNode> children = new ConcurrentHashMap<>();
        private boolean wordEnd;

        public Map<Character, DfaNode> children() {
            return children;
        }

        public boolean isWordEnd() {
            return wordEnd;
        }

        public void setWordEnd(boolean wordEnd) {
            this.wordEnd = wordEnd;
        }
    }
}
