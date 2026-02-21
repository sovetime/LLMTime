package cn.hollis.llm.mentor.rag.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 将文档内容转换为向量
     */
    public List<float[]> embed(List<Document> documents) {
        return documents.stream().map(document -> embeddingModel.embed(document.getText())).collect(Collectors.toList());
    }

    /**
     * 分批写入向量库
     */
    public void embedAndStore(List<Document> documents) {
        // 按固定批次写入，避免单次提交过大
        for (int i = 0; i < documents.size(); i += 9) {
            List<Document> batches = documents.subList(i, Math.min(i + 9, documents.size()));
            vectorStore.add(batches);
        }
    }

    // 返回文档数量
    private static final int DEFAULT_TOP_K = 5;

    // 相似度阈值
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    /**
     * 自定义向量检索
     */
    public List<Document> similaritySearch(String query) {
        return vectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .build());
    }

    /**
     *  封装好的向量检索
     */
    public List<Document> similaritySearch(SearchRequest searchRequest) {
        return vectorStore.similaritySearch(searchRequest);
    }
}
