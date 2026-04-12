package cn.hollis.llm.mentor.agent.utils;

import cn.hollis.llm.mentor.agent.entity.record.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索结果解析器
 * 兼容多种网页搜索返回结构
 */
@Slf4j
public final class SearchResultParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SearchResultParser() {
    }

    /**
     * 解析搜索结果并归一化为统一结构
     *
     * @param resultJson 工具返回的 JSON 字符串
     * @return 标准化后的搜索结果列表
     */
    public static List<SearchResult> parse(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = MAPPER.readTree(resultJson);
            JsonNode resultsNode = findResultsNode(root);
            if (resultsNode == null || !resultsNode.isArray()) {
                return Collections.emptyList();
            }

            Map<String, SearchResult> uniqueResults = new LinkedHashMap<>();
            for (JsonNode item : resultsNode) {
                if (item == null || item.isNull() || !item.isObject()) {
                    continue;
                }

                String url = firstNonBlank(item, "url", "link", "uri", "sourceUrl", "source_url");
                if (isBlank(url)) {
                    JsonNode sourceNode = item.get("source");
                    if (sourceNode != null && sourceNode.isObject()) {
                        url = firstNonBlank(sourceNode, "url", "link");
                    }
                }

                if (isBlank(url)) {
                    continue;
                }

                String title = firstNonBlank(item, "title", "name");
                String content = firstNonBlank(item, "content", "snippet", "summary", "description",
                        "text", "rawContent", "raw_content", "abstract");

                uniqueResults.putIfAbsent(url, new SearchResult(url, title, content));
            }

            return new ArrayList<>(uniqueResults.values());
        } catch (Exception e) {
            log.warn("解析搜索结果失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static JsonNode findResultsNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isArray()) {
            if (node.isEmpty()) {
                return null;
            }

            if (looksLikeResultArray(node)) {
                return node;
            }

            for (JsonNode item : node) {
                JsonNode resultsNode = findResultsNode(item);
                if (resultsNode != null) {
                    return resultsNode;
                }
            }
            return null;
        }

        if (!node.isObject()) {
            return null;
        }

        for (String field : List.of("results", "items", "list", "pages", "webpages", "webPages", "documents")) {
            JsonNode candidate = node.get(field);
            if (candidate != null && candidate.isArray()) {
                return candidate;
            }
        }

        JsonNode textNode = node.get("text");
        JsonNode parsedTextNode = parseEmbeddedJson(textNode);
        if (parsedTextNode != null) {
            JsonNode resultsNode = findResultsNode(parsedTextNode);
            if (resultsNode != null) {
                return resultsNode;
            }
        }

        for (String field : List.of("data", "result", "response")) {
            JsonNode candidate = node.get(field);
            JsonNode resultsNode = findResultsNode(candidate);
            if (resultsNode != null) {
                return resultsNode;
            }
        }

        return looksLikeResultObject(node) ? MAPPER.createArrayNode().add(node) : null;
    }

    private static JsonNode parseEmbeddedJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isTextual()) {
            return node;
        }

        String text = node.asText();
        if (isBlank(text)) {
            return null;
        }

        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean looksLikeResultArray(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return false;
        }
        return looksLikeResultObject(node.get(0));
    }

    private static boolean looksLikeResultObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        return hasAnyField(node, "url", "link", "uri", "sourceUrl", "source_url")
                || hasAnyField(node, "title", "name")
                || hasAnyField(node, "content", "snippet", "summary", "description", "text", "abstract");
    }

    private static boolean hasAnyField(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = value.asText();
            if (!isBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
