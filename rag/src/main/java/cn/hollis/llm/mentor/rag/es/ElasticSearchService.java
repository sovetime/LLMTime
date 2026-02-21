package cn.hollis.llm.mentor.rag.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ElasticSearch 文档管理服务
 *
 * 负责 RAG（检索增强生成）场景下文档数据的索引创建、写入与检索，
 * 核心功能包括：
 * 1. 服务启动时自动检测并初始化索引
 * 2. 支持单条 / 批量文档写入
 * 3. 基于 IK 中文分词器的全文关键词检索
 *
 * 采用ik分词器进行分词，写入使用最大粒度分词，查询使用智能分词
 *
 */
@Service
@Slf4j
public class ElasticSearchService {

    @Autowired
    private RestHighLevelClient client;

    //JSON 序列化工具
    private final ObjectMapper mapper = new ObjectMapper();

    //默认索引名
    private static final String INDEX_NAME = "rag_docs";

    //文本内容字段名
    private static final String FIELD_CONTENT = "content";

    //服务启动后自动检查索引是否存在，不存在则创建
    @PostConstruct
    public void init() {
        try {
            if (!indexExists(INDEX_NAME)) {
                createIndex();
                log.info("ES index [{}] created with IK analyzer!", INDEX_NAME);
            } else {
                log.info("ES index [{}] already exists, skip creation.", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to create ES index: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建索引
     * 分词器使用 IK 并叠加停用词与 lowercase 过滤
     */
    public void createIndex() throws Exception {
        CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);

        // 设置索引参数与分析器
        request.settings(Settings.builder()
                .put("number_of_shards", 1)
                .put("number_of_replicas", 0)
                // 过滤中文常见虚词
                .put("analysis.filter.my_stop_filter.type", "stop")
                .put("analysis.filter.my_stop_filter.stopwords", "_chinese_")
                // ：ik_max（最大粒度分词，用于写入，提升召回）
                .put("analysis.analyzer.ik_max.type", "custom")
                .put("analysis.analyzer.ik_max.tokenizer", "ik_max_word")
                .putList("analysis.analyzer.ik_max.filter", "lowercase", "my_stop_filter")
                // ik_smart（智能分词，用于查询，提升精准）
                .put("analysis.analyzer.ik_smart.type", "custom")
                .put("analysis.analyzer.ik_smart.tokenizer", "ik_smart")
                .putList("analysis.analyzer.ik_smart.filter", "lowercase", "my_stop_filter")
        );

        // 设置字段映射
        // content: 文本内容，使用ik_max分词，同时添加smart字段使用ik_smart分词，查询都是ik_smart
        String mappingJson = """
                {
                  "properties": {
                    "id": { "type": "keyword" },
                    "content": {
                      "type": "text",
                      "analyzer": "ik_max",
                      "search_analyzer": "ik_smart",
                      "fields": {
                        "smart": {
                          "type": "text",
                          "analyzer": "ik_smart",
                          "search_analyzer": "ik_smart"
                        }
                      }
                    },
                    "metadata": {
                      "type": "object",
                      "properties": {
                        "source": { "type": "keyword" },
                        "category": { "type": "keyword" },
                        "orderId": { "type": "keyword" }
                      }
                    }
                  }
                }
                """;
        request.mapping(mappingJson, XContentType.JSON);

        // 执行创建
        client.indices().create(request, RequestOptions.DEFAULT);
    }

    //单条写入文档
    public void indexSingle(EsDocumentChunk doc) throws Exception {
        if (doc == null || doc.getId() == null) {
            throw new IllegalArgumentException("Document or ID cannot be null");
        }

        IndexRequest request = new IndexRequest(INDEX_NAME)
                .id(doc.getId())
                .source(mapper.writeValueAsString(doc), XContentType.JSON)
                .setRefreshPolicy("true");

        client.index(request, RequestOptions.DEFAULT);
        log.debug("Indexed doc id={}", doc.getId());
    }

    //批量写入文档
    public void bulkIndex(List<EsDocumentChunk> docs) throws Exception {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        BulkRequest bulkRequest = new BulkRequest();

        for (EsDocumentChunk doc : docs) {
            // 使用业务 ID 作为 ES 文档 ID
            bulkRequest.add(new IndexRequest(INDEX_NAME)
                    .id(doc.getId())
                    .source(mapper.writeValueAsString(doc), XContentType.JSON));
        }

        bulkRequest.setRefreshPolicy("true");

        BulkResponse response = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (response.hasFailures()) {
            log.error("Bulk indexing completed with failures: {}", response.buildFailureMessage());
        } else {
            log.info("Successfully indexed {} documents", docs.size());
        }
    }

    //判断索引是否存在
    public boolean indexExists(String indexName) throws IOException {
        return client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    }

    /**
     * 中文关键词检索
     * 默认返回 5 条并使用 content 字段
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword) throws Exception {
        return searchByKeyword(keyword, 5, false);
    }

    /**
     * 中文关键词检索
     * 可切换 content 与 content.smart 字段
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword, int size, boolean useSmartAnalyzer) throws Exception {
        SearchRequest request = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder builder = new SearchSourceBuilder();

        // smart 字段使用 ik_smart 分词
        String field = useSmartAnalyzer ? FIELD_CONTENT + ".smart" : FIELD_CONTENT;
        builder.query(QueryBuilders.matchQuery(field, keyword));
        builder.size(size);

        request.source(builder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        List<EsDocumentChunk> result = new ArrayList<>();
        response.getHits().forEach(hit -> {
            try {
                result.add(mapper.readValue(hit.getSourceAsString(), EsDocumentChunk.class));
            } catch (Exception e) {
                log.warn("Failed to convert ES hit to EsDocumentChunk", e);
            }
        });

        return result;
    }
}
