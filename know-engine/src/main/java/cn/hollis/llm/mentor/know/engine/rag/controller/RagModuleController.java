package cn.hollis.llm.mentor.know.engine.rag.controller;

import cn.hollis.llm.mentor.know.engine.ai.model.IntentRecognitionResult;
import cn.hollis.llm.mentor.know.engine.ai.service.IntentRecognitionService;
import cn.hollis.llm.mentor.know.engine.ai.service.PromptService;
import cn.hollis.llm.mentor.know.engine.chat.service.ChatMessageService;
import cn.hollis.llm.mentor.know.engine.document.service.KnowledgeSegmentService;
import cn.hollis.llm.mentor.know.engine.rag.modules.KnowEngineElasticsearchContentRetriever;
import cn.hollis.llm.mentor.know.engine.rag.modules.KnowEngineQueryRouter;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraph;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jText2CypherRetriever;
import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import jakarta.annotation.PostConstruct;
import org.elasticsearch.client.RestClient;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static cn.hollis.llm.mentor.know.engine.rag.config.ElasticSearchConfiguration.INDEX_NAME;

/**
 * 用于ai的各个模块的功能测试
 */
@RestController
@RequestMapping("/ai/module")
public class RagModuleController {


    @Autowired
    private ChatModel chatModel;

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Autowired
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    @Autowired
    private RestClient restClient;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Driver neo4jDriver;

    @Autowired
    private KnowledgeSegmentService knowledgeSegmentService;

    @Autowired
    private PromptService promptService;

    @Autowired
    private ChatMessageService chatMessageService;

    private ElasticsearchContentRetriever fullTextRetriever;

    private SqlDatabaseContentRetriever sqlRetriever;

    private Neo4jText2CypherRetriever neo4jRetriever;

    private static final int MAX_RESULT = 5;

    private static final double MIN_SCORE = 0.5;


    @PostConstruct
    public void init() throws IOException {

        this.fullTextRetriever = ElasticsearchContentRetriever.builder()
                .restClient(restClient)
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .maxResults(MAX_RESULT)
                .indexName(INDEX_NAME)
                .minScore(MIN_SCORE)
                .build();

        this.sqlRetriever = SqlDatabaseContentRetriever.builder().dataSource(dataSource)
                .chatModel(chatModel)
                .build();

        this.neo4jRetriever = Neo4jText2CypherRetriever.builder()
                .graph(Neo4jGraph.builder()
                        .driver(neo4jDriver)
                        .build())
                .chatModel(chatModel)
                .build();
    }

    @GetMapping("/router")
    public String testRouter(String query) {

        KnowEngineElasticsearchContentRetriever embeddingRetriever = KnowEngineElasticsearchContentRetriever.builder()
                .restClient(restClient)
                .embeddingModel(openAiEmbeddingModel)
                .configuration(ElasticsearchConfigurationKnn.builder().build())
                .maxResults(MAX_RESULT)
                .indexName(INDEX_NAME)
                .minScore(MIN_SCORE)
                .stringRedisTemplate(knowledgeSegmentService)
                .build();

        KnowEngineQueryRouter knowEngineQueryRouter = new KnowEngineQueryRouter(List.of(embeddingRetriever, fullTextRetriever, sqlRetriever, neo4jRetriever), chatModel);
        Collection<ContentRetriever> contentRetrievers = knowEngineQueryRouter.route(new Query(query));
        return contentRetrievers.toString();
    }


    @GetMapping("testTransformer")
    public String testTransformer() {

        return "testTransformer";
    }

    @GetMapping("testRetriever")
    public String testRetriever() {
        return "testRetriever";
    }

    @GetMapping("testPromptRouter")
    public String testPromptRouter(String query) {
        IntentRecognitionResult intentRecognitionResult = AiServices.builder(IntentRecognitionService.class).chatModel(chatModel).build().chat(query);
        return promptService.getPrompt(intentRecognitionResult);
    }
}
