package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.service.LangChainAiService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

@RestController
@RequestMapping("/langchain4j/rag")
public class LangChainRagController {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    @Autowired
    OpenAiChatModel chatModel;

    @GetMapping("retriever")
    public String retriever(String query, String filePath) {

        //1.加载文档
        Document document = loadDocument(filePath, new TextDocumentParser());

        //2.分割文档
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(300, 50);
        List<TextSegment> segmentList = splitter.split(document);

        //3.把文档做向量化
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .modelName("text-embedding-v4")  // 阿里云 DashScope 的 embedding 模型名称
                .dimensions(768)  // text-embedding-v4 支持 768 维度
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .maxSegmentsPerBatch(9)
                .apiKey(openAiApiKey).build();

        List<Embedding> embeddings = embeddingModel.embedAll(segmentList).content();

        //4.把向量存入向量数据库
        EmbeddingStore embeddingStore = new InMemoryEmbeddingStore();
        embeddingStore.addAll(embeddings);

        //5.创建检索器
        EmbeddingStoreContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(embeddingStore, embeddingModel);

        ContentInjector contentInjector = DefaultContentInjector.builder().promptTemplate(new PromptTemplate("""
                ## 角色定位
                 你是一位专业的RAG问答助手。请根据提供的上下文信息，详细、准确地回答用户的问题。如果参考文档没有内容，请务必不要胡编乱造，请直接说明"没有找到相关信息"。
                
                 ## 任务要求：
                 1. 请基于以下提供的参考文档内容，回答用户的问题。
                 2. 如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                 3. 如果有了参考文档内容，请务必尽量回答问题。有可能用户的输入比较随意，你可以先尝试回答用户的问题，猜测他的实际需求，先给出回复，你需要尽量去贴合用户的问题需求。
                
                 ## 格式要求：
                 1. 你的所有回答必须使用Markdown格式进行排版。
                 2. 上下文信息中包含了图片描述标签，格式为：`<image src="URL" description="多模态描述"></image>`。
                 3. 如果图片与用户提问高度相关，请将此标签转换为标准的Markdown图片格式 `![图片](URL)`。
                 4. 仅在必要时包含图片，请注意千万不要输出重复的内容和图片，图片确保最终生成的URL不要重复。
                
                 ## 参考文档:
                {{contents}}
                
                 ## 用户问题:
                 {{userMessage}}
                
                 注意：如果参考文档下面的内容为空，请直接回答“没有找到相关信息”。
                
                """)).build();

        //6.创建检索增强器
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentInjector(contentInjector)
                .build();

        LangChainAiService aiService = AiServices.builder(LangChainAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor).build();

        return aiService.chat(query);
    }
}
