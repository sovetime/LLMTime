package cn.hollis.llm.mentor.rag.controller;

import cn.hollis.llm.mentor.rag.embedding.EmbeddingService;
import cn.hollis.llm.mentor.rag.es.ElasticSearchService;
import cn.hollis.llm.mentor.rag.es.EsDocumentChunk;
import cn.hollis.llm.mentor.rag.reader.DocumentReaderFactory;
import cn.hollis.llm.mentor.rag.rerank.RerankUtil;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.Map;


//rag 混合检索
@RestController
@RequestMapping("/rag/hybrid")
@Slf4j
public class RagHybridController {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private DocumentReaderFactory selector;

    @Autowired
    private ElasticSearchService elasticSearchService;

    @Autowired
    private ChatModel chatModel;

    @RequestMapping("write")
    public String write(String filePath) throws Exception {
        // 加载文档
        List<Document> documents = selector.read(new File(filePath));

        // 文档分片
        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(
                // 每块最大字符数
                100,
                // 块之间重叠 100 字符
                new String[]{"。"}
        );

        List<Document> spllittedDocuments = splitter.apply(documents);

        for (Document doc : spllittedDocuments) {
            System.out.println(doc.getText());
            System.out.println(doc.getMetadata());
            System.out.println("--------------------------------------------------");
        }

        //存储到 ES
        List<EsDocumentChunk> esDocs = spllittedDocuments.stream().map(doc -> {
            EsDocumentChunk es = new EsDocumentChunk();
            es.setId(doc.getId());
            es.setContent(doc.getText());
            es.setMetadata(doc.getMetadata());
            return es;
        }).toList();

        //批量写入文档
        elasticSearchService.bulkIndex(esDocs);
        //向量化并存储
        embeddingService.embedAndStore(spllittedDocuments);

        return "success";
    }

    //关键词检索
    @RequestMapping("searchFromEs")
    public List<EsDocumentChunk> searchFromEs(String keyword) throws Exception {
        return elasticSearchService.searchByKeyword(keyword);
    }

    //向量检索
    @RequestMapping("searchFromVector")
    public List<Document> searchFromVector(String keyword) throws Exception {
        return embeddingService.similaritySearch(keyword);
    }

    //混合检索
    @RequestMapping("searchFromHybrid")
    public List<String> searchFromHybrid(String keyword) throws Exception {
        // 关键词检索
        List<EsDocumentChunk> esDocs = elasticSearchService.searchByKeyword(keyword);
        log.info("esDocs: {}", esDocs);

        // 向量检索
        List<Document> vectorDocs = embeddingService.similaritySearch(keyword);
        log.info("vectorDocs: {}", vectorDocs);

        // 重排序
        List<String> result = RerankUtil.rerankFusion(vectorDocs, esDocs, keyword, 5);
        log.info("result: {}", result);
        return result;
    }

    @RequestMapping("chatToHybrid")
    public String chatToHybrid(String keyword) throws Exception {

        String newQuestion = chatModel.call(
                """
                        你是一个问题改写大师，请改写用户的问题，使其更具体、更详细。
                        如果其中有错别字，请你直接做修改。
                        用户问题：
                        
                        """ + keyword
        );
        log.info("newQuestion: {}", newQuestion);

        //混合检索
        List<EsDocumentChunk> esDocs = elasticSearchService.searchByKeyword(newQuestion);
        List<Document> vectorDocs = embeddingService.similaritySearch(newQuestion);
        List<String> result = RerankUtil.rerankFusion(vectorDocs, esDocs, newQuestion, 5);

        String prompt = """
                请根据以下文档内容，回答用户的问题。
                注意，你只能参考文档内容回答，不要自己做推理。
                文档内容：
                {contents}
                
                用户问题:
                {question}
                """;

        return chatModel.call(new PromptTemplate(prompt).create(Map.of("contents", result, "question", newQuestion))).getResult().getOutput().getText();
    }
}
