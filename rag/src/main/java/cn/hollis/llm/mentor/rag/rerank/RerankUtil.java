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

/**
 * RAG 管道重排序工具类
 *
 * <p><b>生产注意事项：</b></p>
 * <ul>
 *   <li>当前 {@code rerankFusion} 方法内 Authorization Token 为硬编码示例，
 *       生产环境必须改为配置中心注入（如 Nacos / Apollo），避免密钥泄漏。</li>
 *   <li>{@link RestTemplate} 实例建议提取为 Spring Bean 复用，避免每次调用创建新实例。</li>
 *   <li>调用外部 Rerank API 时建议配合熔断降级（如 Sentinel / Resilience4j），
 *       降级策略可回退到 RRF 融合，保障主链路可用性。</li>
 * </ul>
 */
@Slf4j
public class RerankUtil {

    /**
     * 基于 RRF（Reciprocal Rank Fusion）算法融合两路召回结果
     * <pre>
     *   RRF_Score(d) = Σ [ 1 / (k + rank_i(d)) ]
     * </pre>
     * <ul>
     *   <li>{@code k}：平滑常数，默认取 60（论文推荐值）。
     *       增大 k 可压缩高排名文档的优势，避免某一路召回强烈主导最终排序。</li>
     *   <li>{@code rank_i(d)}：文档 d 在第 i 路召回列表中的排名，从 1 开始计数。
     *       若文档只出现在某一路召回中，则仅累加该路的贡献分。</li>
     * </ul>
     *
     * <h3>融合逻辑</h3>
     * <ol>
     *   <li>遍历向量召回结果，按排名计算每个文档的 RRF 贡献并累加到 {@code rrfScores}。</li>
     *   <li>遍历关键词召回结果，同样按排名累加（同一文档若两路都命中，则两次分数叠加，天然提权）。</li>
     *   <li>按融合分数降序排列，截取 topK 条结果。</li>
     *   <li>将文档 ID 映射回文本内容后返回。</li>
     * </ol>
     *
     * @param vectorDocs  向量检索召回的文档列表（Spring AI {@link Document}），按相似度降序排列
     * @param keywordDocs 关键词（BM25）检索召回的文档列表（ES {@link EsDocumentChunk}），按 BM25 分降序排列
     * @param topK        融合后保留的最大文档数量
     * @return 按 RRF 分数降序排列的文本内容列表，长度不超过 topK
     */
    public static List<String> rrfFusion(List<Document> vectorDocs, List<EsDocumentChunk> keywordDocs, int topK) {
        // RRF 平滑常数 k=60 为学术论文推荐默认值
        // 调大 k 可降低排名靠前文档的权重优势；调小 k 则放大头部排名的影响
        final int K = 60;

        // 文档 ID -> RRF 累计分数（同一文档在多路召回中分数叠加）
        Map<String, Double> rrfScores = new HashMap<>();
        // 文档 ID -> chunkId，仅用于日志追踪，不参与排序逻辑
        Map<String, String> idToChunkId = new HashMap<>();

        // ---- 第一路：处理向量召回结果 ----
        // rank 从 1 开始，排名越靠前，1/(K+rank) 越大，贡献分越高
        for (int i = 0; i < vectorDocs.size(); i++) {
            Document doc = vectorDocs.get(i);
            String docId = doc.getId();
            // chunkId 由索引阶段写入 metadata，用于追踪原始文档片段来源
            String chunkId = doc.getMetadata().getOrDefault("chunkId", "unknown").toString();
            idToChunkId.put(docId, chunkId);

            int rank = i + 1;
            double score = 1.0 / (K + rank);
            // getOrDefault + put 等价于 merge，此处保持显式写法便于理解
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // ---- 第二路：处理关键词（BM25）召回结果 ----
        // 逻辑与向量召回完全对称；两路都命中的文档将获得双倍累加，相当于隐式 Boost
        for (int i = 0; i < keywordDocs.size(); i++) {
            EsDocumentChunk doc = keywordDocs.get(i);
            String docId = doc.getId();
            String chunkId = doc.getMetadata().getOrDefault("chunkId", "unknown").toString();
            idToChunkId.put(docId, chunkId);

            int rank = i + 1;
            double score = 1.0 / (K + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // ---- 排序 & 截断 ----
        // 按融合分数降序，取 topK 条文档 ID
        List<String> sortedDocIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(topK)
                .collect(Collectors.toList());

        // 打印每条结果的 chunkId 与 RRF 分数，便于离线分析召回/融合效果
        String scoresLog = sortedDocIds.stream()
                .map(docId -> {
                    String chunkId = idToChunkId.getOrDefault(docId, "unknown");
                    double score = rrfScores.getOrDefault(docId, 0.0);
                    return String.format("chunkId: %s, RRF Score: %.4f", chunkId, score);
                })
                .collect(Collectors.joining("; "));
        log.info("RRF融合后top{}结果：{}", topK, scoresLog);

        // ---- 映射 ID -> 文本内容 ----
        // putIfAbsent 保证同一 docId 的内容以向量召回侧为准（向量侧先被放入）
        Map<String, String> idToContent = new HashMap<>();
        vectorDocs.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getText()));
        keywordDocs.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getContent()));

        // 按排序后的 ID 顺序提取文本，过滤掉极少数 ID 无对应内容的异常情况
        return sortedDocIds.stream()
                .map(idToContent::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 调用阿里云 DashScope gte-rerank-v2 模型对两路召回候选集做二次精排
     *
     * <h3>与 RRF 的对比</h3>
     * <ul>
     *   <li>RRF 只利用排名位置，不感知 query 语义；Rerank 模型以 query 为输入，
     *       对每个候选文档计算 query-document 相关性分数，精度更高。</li>
     *   <li>Rerank 引入外部 HTTP 调用，存在网络延迟和依赖风险；
     *       生产建议配置熔断降级，失败时回退至 RRF 策略。</li>
     * </ul>
     *
     * <h3>主流程</h3>
     * <ol>
     *   <li>合并两路召回结果，按 docId 去重（向量侧内容优先保留）。</li>
     *   <li>将合并后的文本列表连同 query 一起发送至 DashScope 重排序接口。</li>
     *   <li>接口按相关性分数降序返回 top_n 条结果。</li>
     *   <li>解析响应，提取文本内容，同时通过文本反查 chunkId 用于日志记录。</li>
     * </ol>
     *
     * <h3>接口说明</h3>
     * <ul>
     *   <li>模型：{@code gte-rerank-v2}，支持中英文混合文档重排序。</li>
     *   <li>参数 {@code return_documents=true}：要求接口在响应中原样返回文档文本，
     *       避免只返回下标导致需要二次映射。</li>
     *   <li>参数 {@code instruct}：传入指令提示，引导模型以"网页搜索"场景理解 query，
     *       提升检索类任务的排序精度。</li>
     * </ul>
     *
     * @param vectorDocs  向量检索召回的文档列表，按相似度降序排列
     * @param keywordDocs 关键词检索召回的文档列表，按 BM25 分降序排列
     * @param query       用户原始查询语句，作为 Rerank 模型的参考上下文
     * @param topK        重排序后保留的最大文档数量，对应接口参数 {@code top_n}
     * @return 按 Rerank 模型相关性分数降序排列的文本内容列表，长度不超过 topK
     * @throws Exception 网络超时、HTTP 非 2xx 响应或响应结构异常时抛出
     */
    @SuppressWarnings("unchecked")
    public static List<String> rerankFusion(List<Document> vectorDocs, List<EsDocumentChunk> keywordDocs, String query, int topK) throws Exception {
        // 使用 LinkedHashMap 保持合并时的插入顺序，便于后续通过文本反查 chunkId
        // Key: docId（ES/向量库中的唯一标识），Value: 文档文本内容
        Map<String, String> idToContent = new LinkedHashMap<>();
        // Key: docId，Value: chunkId（原始文档分片 ID），仅用于日志定位，不影响排序
        Map<String, String> idToChunkId = new HashMap<>();

        // ---- 合并向量召回结果 ----
        // putIfAbsent 保证相同 docId 以先放入的向量侧内容为准
        vectorDocs.forEach(doc -> {
            String docId = doc.getId();
            idToContent.putIfAbsent(docId, doc.getText());
            String chunkId = doc.getMetadata().getOrDefault("chunkId", docId).toString();
            idToChunkId.putIfAbsent(docId, chunkId);
        });

        // ---- 合并关键词召回结果 ----
        // 与向量侧相同 docId 的内容已被 putIfAbsent 跳过，实现去重
        keywordDocs.forEach(doc -> {
            String docId = doc.getId();
            idToContent.putIfAbsent(docId, doc.getContent());
            String chunkId = doc.getMetadata().getOrDefault("chunkId", docId).toString();
            idToChunkId.putIfAbsent(docId, chunkId);
        });

        // 候选集为空时短路返回，避免无效的外部 API 调用
        List<String> documents = new ArrayList<>(idToContent.values());
        if (documents.isEmpty()) {
            log.info("没有检索到任何文档，无需调用重排序模型");
            return Collections.emptyList();
        }

        // ---- 构造 HTTP 请求 ----
        // DashScope 文本重排序 REST 接口地址
        String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        HttpHeaders headers = new HttpHeaders();
        // TODO: 生产环境必须将 API Key 从代码中移出，通过配置中心（Nacos/Apollo）或环境变量注入
        //       当前硬编码值仅供本地调试使用，提交代码前务必替换
        headers.set("Authorization", "Bearer sk-8ef405c4686e456e91f6698272253126");
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 构造请求体，遵循 DashScope Rerank API 接口规范
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gte-rerank-v2");  // 指定模型版本，支持多语言重排序

        // input 字段包含 query 和候选文档列表
        Map<String, Object> input = new HashMap<>();
        input.put("query", query);
        input.put("documents", documents);  // 文档列表顺序对接口无影响，模型会独立打分
        requestBody.put("input", input);

        // parameters 字段控制接口行为
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("return_documents", true);   // 要求响应中携带原文，避免二次映射
        parameters.put("top_n", topK);              // 限制返回条数，减少 LLM 上下文 token 消耗
        // instruct 指令引导模型以检索场景理解 query，提升排序精度
        parameters.put("instruct", "Given a web search query, retrieve relevant passages that answer the query.");
        requestBody.put("parameters", parameters);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // ---- 发送 HTTP 请求 ----
        // TODO: 生产环境建议将 RestTemplate 提取为 Spring Bean 单例复用，
        //       并使用连接池（OkHttp / HttpClient）替代 SimpleClientHttpRequestFactory，
        //       同时配合 Sentinel / Resilience4j 添加熔断降级，失败时回退到 RRF 策略
        RestTemplate restTemplate = new RestTemplate();
        // 连接超时 5s，读取超时 10s；Rerank 模型推理耗时通常在 1~3s，留有余量
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5000);
            setReadTimeout(10000);
        }});

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        // ---- 响应校验 ----
        // 校验 HTTP 状态码，非 2xx 时提前抛出，防止后续空指针
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("重排序API调用失败: " + response.getStatusCode() + "，响应: " + response.getBody());
        }
        // 校验响应体结构完整性，output 为 DashScope 接口约定的必要字段
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("output")) {
            throw new RuntimeException("API响应格式异常，缺少output字段: " + responseBody);
        }

        // ---- 解析重排序结果 ----
        Map<String, Object> output = (Map<String, Object>) responseBody.get("output");
        // results 列表已按相关性分数降序排列，直接按顺序消费即可
        List<Map<String, Object>> rerankedResults = (List<Map<String, Object>>) output.get("results");
        if (rerankedResults == null || rerankedResults.isEmpty()) {
            log.warn("重排序模型返回空结果，output内容: {}", output);
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        List<String> rankLogs = new ArrayList<>();

        for (int i = 0; i < rerankedResults.size(); i++) {
            Map<String, Object> item = rerankedResults.get(i);
            // document.text 为 return_documents=true 时接口返回的原始文档内容
            String text = (String) ((Map<String, Object>) item.get("document")).get("text");

            // 兼容 DashScope 不同版本接口的分数字段命名差异
            // gte-rerank-v2 使用 relevance_score，旧版接口可能使用 score
            Double score = null;
            if (item.containsKey("relevance_score")) {
                score = ((Number) item.get("relevance_score")).doubleValue();
            } else if (item.containsKey("score")) {
                score = ((Number) item.get("score")).doubleValue();
            }

            if (text != null) {
                result.add(text);

                // 通过文本内容反查 docId，再映射到 chunkId，用于定位该片段的原始来源
                // 注意：若存在内容完全相同的不同片段，此处会命中第一个匹配项
                String matchedChunkId = "unknown";
                for (Map.Entry<String, String> entry : idToContent.entrySet()) {
                    if (entry.getValue().equals(text)) {
                        matchedChunkId = idToChunkId.getOrDefault(entry.getKey(), "unknown");
                        break;
                    }
                }

                rankLogs.add(String.format("排名 %d: chunkId=%s, 分数=%.4f", i + 1, matchedChunkId, score != null ? score : 0.0));
            }
        }

        // 输出完整排名日志，用于评估重排序效果和线上问题排查
        log.info("gte-rerank-v2 重排序结果: {}", String.join("; ", rankLogs));
        log.info("重排序后返回 {} 条文档，原始合并候选集共 {} 条", result.size(), documents.size());

        return result;
    }
}