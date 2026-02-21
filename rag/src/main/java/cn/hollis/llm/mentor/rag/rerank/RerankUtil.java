package cn.hollis.llm.mentor.rag.rerank;

import cn.hollis.llm.mentor.rag.es.EsDocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

//重排序
@Slf4j
public class RerankUtil {

    /**
     * RRF 算法融合向量检索和关键词检索结果
     * 公式：RRF Score = Σ(1/(k + rank_i))，其中 k 为常数（通常取60），rank_i 为文档在第i个检索结果中的排名
     */
    public static List<String> rrfFusion(List<Document> vectorDocs, List<EsDocumentChunk> keywordDocs, int topK) {
        // 常数 k，控制低排名文档的权重
        final int K = 60;
        // 存储每个文档ID的RRF得分
        Map<String, Double> rrfScores = new HashMap<>();
        // 存储文档ID到chunkId的映射
        Map<String, String> idToChunkId = new HashMap<>();

        // 处理向量检索结果（排名从1开始）
        for (int i = 0; i < vectorDocs.size(); i++) {
            Document doc = vectorDocs.get(i);
            String docId = doc.getId();
            // 获取元数据中的chunkId
            String chunkId = doc.getMetadata().getOrDefault("chunkId", "unknown").toString();
            idToChunkId.put(docId, chunkId);
            // 排名从1开始
            int rank = i + 1;
            double score = 1.0 / (K + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // 处理关键词检索结果（排名从1开始）
        for (int i = 0; i < keywordDocs.size(); i++) {
            EsDocumentChunk doc = keywordDocs.get(i);
            String docId = doc.getId();
            // 获取元数据中的chunkId
            String chunkId = doc.getMetadata().getOrDefault("chunkId", "unknown").toString();
            idToChunkId.put(docId, chunkId);
            // 排名从1开始
            int rank = i + 1;
            double score = 1.0 / (K + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // 收集所有文档ID并按RRF得分降序排序，同时限制返回topK条
        List<String> sortedDocIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(topK)
                .collect(Collectors.toList());

        // 打印每个文本块的chunkId和分数
        String scoresLog = sortedDocIds.stream()
                .map(docId -> {
                    String chunkId = idToChunkId.getOrDefault(docId, "unknown");
                    double score = rrfScores.getOrDefault(docId, 0.0);
                    return String.format("chunkId: %s, RRF Score: %.4f", chunkId, score);
                })
                .collect(Collectors.joining("; "));

        log.info("RRF融合后top{}结果：{}", topK, scoresLog);

        // 构建文档ID到内容的映射
        Map<String, String> idToContent = new HashMap<>();
        vectorDocs.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getText()));
        keywordDocs.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getContent()));

        // 按排序后的ID提取文档内容
        return sortedDocIds.stream()
                .map(idToContent::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 使用外部重排序模型对融合后的候选文档做二次排序
     * 主要流程
     * 1.先合并向量检索和关键词检索结果并去重
     * 2.调用 DashScope 文本重排序接口获取排序结果
     * 3.按返回顺序提取文本并记录 chunkId 与分数日志
     */
    public static List<String> rerankFusion(List<Document> vectorDocs, List<EsDocumentChunk> keywordDocs, String query, int topK) throws Exception {
        // 维护 文档ID -> 文本内容 的映射
        // 使用 LinkedHashMap 保留插入顺序 便于与请求 documents 顺序一致
        Map<String, String> idToContent = new LinkedHashMap<>();
        // 维护 文档ID -> chunkId 的映射 用于重排序后输出可追踪日志
        Map<String, String> idToChunkId = new HashMap<>();

        // 合并向量检索结果
        // putIfAbsent 的目的是防止同一个 docId 被后续重复覆盖
        vectorDocs.forEach(doc -> {
            String docId = doc.getId();
            idToContent.putIfAbsent(docId, doc.getText());
            // chunkId 优先取元数据 若不存在则回退到 docId
            String chunkId = doc.getMetadata().getOrDefault("chunkId", docId).toString();
            idToChunkId.putIfAbsent(docId, chunkId);
        });

        // 合并关键词检索结果
        // 与向量检索共享同一套映射 达到跨检索源去重
        keywordDocs.forEach(doc -> {
            String docId = doc.getId();
            idToContent.putIfAbsent(docId, doc.getContent());
            // chunkId 优先取元数据 若不存在则回退到 docId
            String chunkId = doc.getMetadata().getOrDefault("chunkId", docId).toString();
            idToChunkId.putIfAbsent(docId, chunkId);
        });

        // 将去重后的文本内容转成模型入参列表
        List<String> documents = new ArrayList<>(idToContent.values());
        // 无候选文档时直接短路返回 避免无意义外部调用
        if (documents.isEmpty()) {
            log.info("没有检索到任何文档，无需重排序");
            return Collections.emptyList();
        }

        // DashScope 重排序接口地址
        String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        HttpHeaders headers = new HttpHeaders();
        // 鉴权头 当前示例为固定 token 生产建议使用配置中心或环境变量注入
        headers.set("Authorization", "Bearer sk-8ef405c4686e456e91f6698272253126");
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 组装请求体根节点
        Map<String, Object> requestBody = new HashMap<>();
        // 指定重排序模型
        requestBody.put("model", "gte-rerank-v2");

        // input 节点包含查询词和候选文档列表
        Map<String, Object> input = new HashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        requestBody.put("input", input);

        // parameters 节点控制返回策略
        Map<String, Object> parameters = new HashMap<>();
        // 返回文档文本 否则结果中可能只有索引或分值
        parameters.put("return_documents", true);
        // 仅返回 topK 条
        parameters.put("top_n", topK);
        // instruct 用于补充任务语义 提示模型按问答相关性排序
        parameters.put("instruct", "Given a web search query, retrieve relevant passages that answer the query.");
        requestBody.put("parameters", parameters);

        // 构建 HTTP 请求对象
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();
        // 设置连接超时和读取超时 避免外部接口长时间阻塞
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5000);
            setReadTimeout(10000);
        }});

        // 发起 POST 请求并以 Map 形式接收 JSON
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        // 先校验 HTTP 状态码 非 2xx 直接抛错并带上响应体便于定位
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("重排序API调用失败: " + response.getStatusCode() + "，响应: " + response.getBody());
        }

        // 校验响应结构 顶层必须有 output 字段
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("output")) {
            throw new RuntimeException("API响应格式异常，缺少output字段: " + responseBody);
        }

        // 解析 output.results 列表 每个元素代表一个排序结果
        Map<String, Object> output = (Map<String, Object>) responseBody.get("output");
        List<Map<String, Object>> rerankedResults = (List<Map<String, Object>>) output.get("results");
        // 返回空结果时记录告警并短路返回
        if (rerankedResults == null || rerankedResults.isEmpty()) {
            log.warn("重排序返回空结果: {}", output);
            return Collections.emptyList();
        }

        // 结果文本列表 作为最终返回值
        List<String> result = new ArrayList<>();
        // 排名日志列表 统一拼接后输出
        List<String> rankLogs = new ArrayList<>();

        // 按接口返回顺序遍历 该顺序就是重排序后的顺序
        for (int i = 0; i < rerankedResults.size(); i++) {
            Map<String, Object> item = rerankedResults.get(i);
            // return_documents=true 时 文档正文位于 document.text
            String text = (String) ((Map<String, Object>) item.get("document")).get("text");
            Double score = null;
            // 不同模型或版本分数字段可能不同 兼容 relevance_score 与 score
            if (item.containsKey("relevance_score")) {
                score = ((Number) item.get("relevance_score")).doubleValue();
            } else if (item.containsKey("score")) {
                score = ((Number) item.get("score")).doubleValue();
            }

            // 只处理有文本的结果 避免空文本进入下游
            if (text != null) {
                result.add(text);

                // 通过文本反查原始 docId 进而拿到 chunkId 便于定位来源
                String matchedChunkId = "unknown";
                for (Map.Entry<String, String> entry : idToContent.entrySet()) {
                    if (entry.getValue().equals(text)) {
                        matchedChunkId = idToChunkId.getOrDefault(entry.getKey(), "unknown");
                        break;
                    }
                }

                // 记录每一条排序日志 若分数缺失则使用 0 兜底打印
                rankLogs.add(String.format("排名 %d: chunkId=%s, 分数=%.4f",
                        i + 1, matchedChunkId, score != null ? score : 0.0));
            }
        }

        // 输出完整排名日志和统计信息 便于排障与调参
        log.info("qwen3-rerank重排序结果：{}", String.join("; ", rankLogs));
        log.info("重排序后返回{}条文档，原始合并{}条", result.size(), documents.size());

        // 返回按重排序后顺序排列的文本列表
        return result;
    }

}
