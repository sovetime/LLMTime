package cn.hollis.llm.mentor.know.engine.rag.config;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Elasticsearch 向量存储配置类
 * 用于 RAG（检索增强生成）场景，配置向量嵌入模型和向量数据库
 */
@Configuration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchConfiguration {

    @Autowired
    private ElasticSearchProperties properties;

    /**
     * 向量索引名称
     * 存储文档的向量嵌入数据
     */
    public static final String INDEX_NAME = "know-engine-vector";

    /**
     * OpenAI 嵌入模型
     * 将文本转换为向量表示，用于语义检索
     *
     * @return OpenAI 嵌入模型实例
     */
    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .modelName(properties.getModelName())           // 模型名称（如 text-embedding-3-small）
                .dimensions(properties.getDimensions())         // 向量维度（如 1536）
                .baseUrl(properties.getBaseUrl())               // API 地址（支持自定义或代理）
                .maxSegmentsPerBatch(9)                         // 单次请求最大文本段数，控制批处理大小
                .apiKey(properties.getApiKey())                 // API 密钥
                .build();
    }

    /**
     * Elasticsearch REST 客户端
     * 用于与 ES 集群通信
     *
     * @return RestClient 实例，应用关闭时自动调用 close() 方法
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient restClient() {
        return RestClient
                .builder(HttpHost.create(properties.getHost()))
                .build();
    }

    /**
     * Elasticsearch 向量存储
     * LangChain4j 的向量数据库实现，用于存储和检索文档向量
     *
     * @param restClient ES 客户端
     * @return 向量存储实例
     */
    @Primary
    @ConditionalOnMissingBean
    @Bean
    public ElasticsearchEmbeddingStore elasticsearchEmbeddingStore(RestClient restClient) {
        return ElasticsearchEmbeddingStore.builder()
                .restClient(restClient)
                .indexName(INDEX_NAME)
                .build();
    }
}
