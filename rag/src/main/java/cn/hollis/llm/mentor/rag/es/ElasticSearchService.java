package cn.hollis.llm.mentor.rag.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
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

@Service
@Slf4j
public class ElasticSearchService {

    @Autowired
    private RestHighLevelClient client;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String INDEX_NAME = "rag_docs";

    private static final String FIELD_CONTENT = "content";

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
     * 创建索引（IK 分词 + 停用词 + lowercase）
     */
    public void createIndex() throws Exception {
        CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);

        // 1. 设置索引配置（settings）
        request.settings(Settings.builder()
                .put("number_of_shards", 1)
                .put("number_of_replicas", 0)
                // 分析器配置
                .put("analysis.filter.my_stop_filter.type", "stop")
                .put("analysis.filter.my_stop_filter.stopwords", "_chinese_")
                .put("analysis.analyzer.ik_max.type", "custom")
                .put("analysis.analyzer.ik_max.tokenizer", "ik_max_word")
                .putList("analysis.analyzer.ik_max.filter", "lowercase", "my_stop_filter")
                .put("analysis.analyzer.ik_smart.type", "custom")
                .put("analysis.analyzer.ik_smart.tokenizer", "ik_smart")
                .putList("analysis.analyzer.ik_smart.filter", "lowercase", "my_stop_filter")
        );

        // 2. 设置 mapping
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

        // 3. 创建索引
        client.indices().create(request, RequestOptions.DEFAULT);
    }

    /**
     * 单条存储
     */
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

    /**
     * 批量存储
     */
    public void bulkIndex(List<EsDocumentChunk> docs) throws Exception {
        if (docs == null || docs.isEmpty()) return;

        BulkRequest bulkRequest = new BulkRequest();

        for (EsDocumentChunk doc : docs) {
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

    public boolean indexExists(String indexName) throws IOException {
        return client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    }

    /**
     * 中文检索 - ik_max_word 建库 + ik_smart 检索
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword) throws Exception {
        return searchByKeyword(keyword, 5, false);
    }

    /**
     * 中文检索：ik_max_word / ik_smart 切换
     */
    public List<EsDocumentChunk> searchByKeyword(String keyword, int size, boolean useSmartAnalyzer) throws Exception {
        SearchRequest request = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder builder = new SearchSourceBuilder();

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
                log.warn("⚠️ Failed to convert ES hit to EsDocumentChunk", e);
            }
        });

        return result;
    }
}
