package cn.hollis.llm.mentor.agent.tool;

import cn.hollis.llm.mentor.agent.entity.record.SearchResult;
import cn.hollis.llm.mentor.agent.utils.HttpClientUtil;
import cn.hollis.llm.mentor.agent.utils.SearchResultParser;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 秘塔网页搜索工具
 */
@Service
@Slf4j
public class MetasoSearchService {

    private final String apiKey;
    private final String searchUrl;
    private final String scope;
    private final int size;
    private final boolean includeSummary;
    private final boolean includeRawContent;
    private final boolean conciseSnippet;

    public MetasoSearchService(@Value("${metaso.api-key:}") String apiKey,
                               @Value("${metaso.search-url:https://metaso.cn/api/v1/search}") String searchUrl,
                               @Value("${metaso.scope:webpage}") String scope,
                               @Value("${metaso.size:10}") int size,
                               @Value("${metaso.include-summary:false}") boolean includeSummary,
                               @Value("${metaso.include-raw-content:false}") boolean includeRawContent,
                               @Value("${metaso.concise-snippet:false}") boolean conciseSnippet) {
        this.apiKey = apiKey;
        this.searchUrl = searchUrl;
        this.scope = scope;
        this.size = size;
        this.includeSummary = includeSummary;
        this.includeRawContent = includeRawContent;
        this.conciseSnippet = conciseSnippet;
    }

    /**
     * 使用秘塔搜索最新网页信息
     *
     * @param query 搜索关键词
     * @return 标准化后的搜索结果
     */
    @Tool(description = "搜索互联网网页信息，适合查询最新资讯、公开资料、背景信息和事实依据，返回标题、链接和摘要")
    public String searchWeb(@ToolParam(description = "搜索关键词，内容要具体明确") String query) {
        log.info("EXECUTE Tool: searchWeb: query={}", query);

        if (query == null || query.isBlank()) {
            return buildErrorResponse("", "搜索关键词不能为空");
        }

        if (apiKey == null || apiKey.isBlank()) {
            return buildErrorResponse(query, "未配置秘塔 API Key");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("q", query.trim());
        requestBody.put("scope", scope);
        requestBody.put("includeSummary", includeSummary);
        requestBody.put("size", String.valueOf(size));
        requestBody.put("includeRawContent", includeRawContent);
        requestBody.put("conciseSnippet", conciseSnippet);

        try {
            String responseBody = HttpClientUtil.doPost(searchUrl, headers, requestBody);
            List<SearchResult> results = SearchResultParser.parse(responseBody);

            JSONObject normalizedResponse = new JSONObject(new LinkedHashMap<>());
            normalizedResponse.put("query", query.trim());
            normalizedResponse.put("engine", "metaso");
            normalizedResponse.put("resultCount", results.size());
            normalizedResponse.put("results", results);

            if (results.isEmpty()) {
                normalizedResponse.put("message", "秘塔接口已返回响应，但未解析到标准搜索结果");
                normalizedResponse.put("rawResponse", tryParseJson(responseBody));
            }

            return normalizedResponse.toJSONString();
        } catch (Exception e) {
            log.error("秘塔搜索失败: query={}", query, e);
            return buildErrorResponse(query, "秘塔搜索失败: " + e.getMessage());
        }
    }

    private String buildErrorResponse(String query, String errorMessage) {
        JSONObject response = new JSONObject(new LinkedHashMap<>());
        response.put("query", query);
        response.put("engine", "metaso");
        response.put("resultCount", 0);
        response.put("results", List.of());
        response.put("error", errorMessage);
        return response.toJSONString();
    }

    private Object tryParseJson(String responseBody) {
        try {
            return JSON.parse(responseBody);
        } catch (Exception e) {
            return responseBody;
        }
    }
}
