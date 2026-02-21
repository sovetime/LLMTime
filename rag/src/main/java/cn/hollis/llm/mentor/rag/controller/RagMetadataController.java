package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.embedding.EmbeddingService;
import cn.hollis.llm.mentor.rag.reader.DocumentReaderFactory;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;

//rag元数据查询
@RestController
@RequestMapping("/rag/metadata")
public class RagMetadataController implements InitializingBean {

    @Autowired
    private DocumentReaderFactory documentReaderFactory;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private PgVectorStore vectorStore;

    @Autowired
    private ChatModel chatModel;

    private ChatClient chatClient;


    // 为文档添加元数据并写入向量库
    // 为了演示方便，就直接把指定的文件名传进去了，实际环境中，这个文件名的提取工作也是需要大模型来进行参数抽取的
    @GetMapping("/embedding")
    public String embedding(String filePath, String fileName) {
        List<Document> documents;
        try {
            // 将文件解析为 Document 列表
            documents = documentReaderFactory.read(new File(filePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 为每个文档打上 fileName 元数据标签
        for (Document document : documents) {
            document.getMetadata().put("fileName", fileName);
        }

        // 执行向量化并写入向量库
        embeddingService.embedAndStore(documents);

        return "success";
    }

    // 调试工具，确认向量库有没有存进内容
    // 按元数据过滤执行相似度检索
    // 构造带元数据过滤条件的检索请求，只检索 fileName 匹配的向量分片
    @GetMapping("/retrieveMetadata")
    public String retrieveMetadata(String query, String fileName) {

        SearchRequest searchRequest = SearchRequest.builder().query(query)
                .filterExpression("fileName == '" + fileName + "'").build();

        return embeddingService.similaritySearch(searchRequest).toString();
    }

    // 通过 Advisor 注入元数据过滤条件后执行问答
    @GetMapping("/retrieveAdvisorWithMetadata")
    public String retrieveAdvisorWithMetadata(String query, String fileName) {
        return chatClient.prompt(query)
                /// 动态传入元数据过滤表达式，Advisor 会在检索时自动应用
                .advisors(advisorSpec -> advisorSpec.param("qa_filter_expression", "fileName == '" + fileName + "'"))
                .call().content();
    }


    @Override
    public void afterPropertiesSet() throws Exception {

        // 自定义问答 Prompt 模板
        PromptTemplate promptTemplate = new PromptTemplate("""
                请基于以下提供的参考文档内容，回答用户的问题
                如果参考文档中没有相关信息，请直接说明\"没有找到相关信息\"，不要编造内容

                参考文档内容:
                {question_answer_context}

                用户问题: {query}
                """);

        // 构建 QuestionAnswerAdvisor：
        // - similarityThreshold(0.5)：过滤相似度低于 0.5 的噪声文档
        // - topK(5)：每次检索返回最相关的 5 个文档分片
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.5).topK(5).build())
                .promptTemplate(promptTemplate).build();

        this.chatClient = ChatClient.builder(chatModel)
                // 注册问答检索增强 Advisor
                .defaultAdvisors(questionAnswerAdvisor)
                // 设置默认模型参数
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.7)
                                .build()
                ).build();
    }
}
