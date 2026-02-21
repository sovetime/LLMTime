# rag 模块 Controller 说明

本文档汇总 `rag` 模块中各个 Controller 的职责、核心接口，以及源码中已有注释信息

## 1. GraphRagController（Neo4j 图数据库）

- 路由前缀：`/rag/graph`
- 作用：基于 Neo4j 做图谱式 RAG 查询，示例领域是导演和电影关系
- 主要接口：
  - `GET /ask`：根据 `movieName` 检索图谱上下文并让大模型回答
  - `GET /init`：初始化导演、电影节点以及 `DIRECTED` 关系
- 已有注释：
  - `//rag图数据库`
  - `// 保存节点`

## 2. ModularRagController（模块化 RAG）

- 路由前缀：`/rag/modular`
- 作用：演示 Spring AI 模块化 RAG 流程，包含检索器、查询改写、查询扩展、查询增强
- 主要接口：
  - `GET /retriever`：执行模块化 RAG 调用并返回回答
- 已有注释：
  - `//SpringAI模块化Rag功能`
  - `// 必需：绑定向量存储`
  - `// 返回最相似的 5 个文档`
  - `// 相似度低于 0.6 的过滤掉`

## 3. RagEmbeddingController（向量化）

- 路由前缀：`/rag/embedding`
- 作用：文档读取、清洗、分块后执行向量化并入库
- 主要接口：
  - `GET/POST /test`：测试 Embedding 模型输出
  - `GET/POST /embed`：文件转 Document，清洗分段后向量化存储
- 已有注释：
  - `//rag向量转换`
  - `//文档向量化村粗`
  - `//文档转换成Document`
  - `//清洗并分段`
  - `//向量化并存储`

## 4. RagEsController（ES 关键词检索）

- 路由前缀：`/rag/es`
- 作用：文档写入 Elasticsearch，并提供关键词检索
- 主要接口：
  - `GET/POST /write`：读取文件、清洗、分片并批量写入 ES
  - `GET/POST /search`：按关键词检索 ES 文档块
- 已有注释：
  - `// 写入ES`
  - `// 加载文档`
  - `// 文本清洗`
  - `// 文档分片`
  - `// 每块最大字符数`
  - `// 块之间重叠 100 字符`
  - `// 存储到ES`
  - `//关键词检索`

## 5. RagHybridController（混合检索）

- 路由前缀：`/rag/hybrid`
- 作用：融合 ES 关键词检索和向量检索，并通过重排提升结果质量
- 主要接口：
  - `GET/POST /write`：文档分片后同时写 ES 和向量库
  - `GET/POST /searchFromEs`：关键词检索
  - `GET/POST /searchFromVector`：向量检索
  - `GET/POST /searchFromHybrid`：混合召回 + 重排序
  - `GET/POST /chatToHybrid`：先改写问题，再混合检索并让模型回答
- 已有注释：
  - `//rag 混合检索`
  - `// 加载文档`
  - `// 文档分片`
  - `// 每块最大字符数`
  - `// 块之间重叠 100 字符`
  - `//存储到 ES`
  - `//批量写入文档`
  - `//向量化并存储`
  - `//关键词检索`
  - `//向量检索`
  - `//混合检索`
  - `// 重排序`

## 6. RagRetrieverController（向量检索问答）

- 路由前缀：`/rag/retriever`
- 作用：提供纯检索、手工拼 Prompt 问答、Advisor 问答三种方式
- 主要接口：
  - `GET /query`：按阈值做相似度检索并返回文档内容
  - `GET /retrieve`：检索后拼装 Prompt，直接调用模型回答
  - `GET /retrieveAdvisor`：通过 `QuestionAnswerAdvisor` 自动检索增强
- 已有注释：
  - `// 2. 构建提示词模板`
  - `// 自定义Prompt模板`
  - `// 实现 Logger 的 Advisor`
  - `// 设置 ChatClient 中 ChatModel 的 Options 参数`

## 7. RagMetadataController（元数据过滤检索）

- 路由前缀：`/rag/metadata`
- 作用：给文档打元数据标签并按标签过滤检索，支持 Advisor 方式问答
- 主要接口：
  - `GET /embedding`：写入带 `fileName` 元数据的向量文档
  - `GET /retrieveMetadata`：按 `fileName` 过滤表达式检索
  - `GET /retrieveAdvisorWithMetadata`：动态注入过滤表达式后问答
- 已有注释：
  - `//rag元数据查询`
  - `// 为文档添加元数据并写入向量库`
  - `// 为了演示方便，就直接把指定的文件名传进去了，实际环境中，这个文件名的提取工作也是需要大模型来进行参数抽取的`
  - `// 将文件解析为 Document 列表`
  - `// 为每个文档打上 fileName 元数据标签`
  - `// 执行向量化并写入向量库`
  - `// 调试工具，确认向量库有没有存进内容`
  - `// 按元数据过滤执行相似度检索`
  - `// 构造带元数据过滤条件的检索请求，只检索 fileName 匹配的向量分片`
  - `// 通过 Advisor 注入元数据过滤条件后执行问答`
  - `// 动态传入元数据过滤表达式，Advisor 会在检索时自动应用`
  - `// 自定义问答 Prompt 模板`
  - `// 构建 QuestionAnswerAdvisor：`
  - `// - similarityThreshold(0.5)：过滤相似度低于 0.5 的噪声文档`
  - `// - topK(5)：每次检索返回最相关的 5 个文档分片`
  - `// 注册问答检索增强 Advisor`
  - `// 设置默认模型参数`

## 8. RagRewriteController（查询改写）

- 路由前缀：`/rag/rewrite`
- 作用：先做查询改写，再多路检索并聚合文档后回答
- 主要接口：
  - `GET /chatWithQueryRewrite`：查询改写 + 相似度检索 + 生成回答
- 已有注释：
  - `//rag查询重写`
  - `//查询重写`
  - `// 组合方法`
  - `// 相似度检索，set去重`
  - `// 构建提示词模板`
  - `// 处理检索到的文档内容`
  - `// 填充模板参数`
  - `// 调用大模型生成回答`

## 9. RagReaderController（文档读取与分块）

- 路由前缀：`/rag`
- 作用：读取并清洗文档，或按重叠策略切分文档
- 主要接口：
  - `GET/POST /read`：读取清洗后拼接文本返回
  - `GET/POST /chunker`：按固定大小 + 重叠分块
- 已有注释：
  - `// 读取并清洗文档 返回拼接后的文本内容`
  - `//文档清洗，转换成Document`
  - `// 拼接文本内容并输出元数据`
  - `// 读取并清洗文档后，按段落重叠策略切分（固定大小分块）`
  - `// 每段最大 100 字符 段间重叠 5 字符`
  - `// 执行切分并逐段打印结果`

## 10. RagSplitterController（分割器实验）

- 路由前缀：`/rag`
- 作用：集中演示多种分割器和多模态 PDF 分块
- 主要接口：
  - `GET/POST /split`：`OverlapParagraphTextSplitter` 分块
  - `GET/POST /splitRecursive`：递归字符分割
  - `GET/POST /splitSentence`：按句分割
  - `GET/POST /splitParent`：Markdown 标题分割
  - `GET/POST /mulitModal`：多模态 PDF 解析后分块
- 已有注释：
  - `// 《斗破苍穹》是中国网络作家天蚕土豆创作的玄幻小说，2009年4月14日起在起点中文网连载，2011年7月20日完结，首版由湖北少年儿童出版社出版。2010年7月，该作品部分章节被编为《废材当自强》由湖北少年儿童出版社出版 [22]。`
  - `// 小说以斗气大陆为背景，讲述天才少年萧炎从斗气尽失逐步成长为斗帝的历程，期间通过收集异火、修炼丹药突破困境，最终解开斗帝失踪之谜并前往大千世界 [23]。作品构建了炼药师体系、异火榜及天鼎榜等设定，其中炼药师需具备火木双属性斗气与灵魂感知力 [6]。`
  - `// 该小说全网点击量近100亿次，实体书累计销量超300万册，2017年7月荣登“2017猫片胡润原创文学IP价值榜”榜首 [13-14]。2020年8月被国家图书馆永久典藏并位列中国文化产业IP价值综合榜TOP50前五 [6]，其改编动画在腾讯视频创下2.6万热度值纪录，并推出盲盒、游戏等衍生品 [25]。幻维数码制作的动画年番《斗破苍穹》重现佛怒火莲等经典场景，多次入围华语剧集口碑榜前十 [24]。2025年1月入选“2024网络文学神作榜”，同年2月28日荣获2024阅文IP盛典20大荣耀IP [15-16]。2025年11月，上海金山区人民法院宣判国内首例AI著作权侵权案，用户擅自使用《斗破苍穹》角色“美杜莎”形象训练AI模型被判赔偿5万元 [26-29]。`

## 11. RagImageController（多模态图文 RAG）

- 路由前缀：`/rag/image`
- 作用：演示图像理解、多模态 PDF 处理、向量化入库与图文问答
- 主要接口：
  - `GET/POST /callWithSpringAiAlibaba`：使用 Spring AI + 阿里模型做看图问答
  - `GET/POST /callWithOpenAI`：以 OpenAI 兼容方式调用多模态模型
  - `GET/POST /callWithChatClient`：基于 ChatClient 的多模态调用
  - `GET/POST /process`：处理 PDF 并返回抽取文本
  - `GET/POST /processFile`：多模态处理 + 分块 + 向量化
  - `GET/POST /chat`：通过检索增强完成图文问答
- 已有注释：
  - `//多模态的文件的处理（包括了文件中的图片转文字）`
  - `//文档内容清晰，移除一些不必要的符号`
  - `//定义多模态分块器，对文档做分块`
  - `//把分块后的结果做向量化并保存到向量数据库`
  - `//构建查询增强器`
  - `//基于向量相似度进检索文档`
  - `//绑定向量存储`
  - `//返回相似度最高的5个文档`
  - `//相似度低于0.3的过滤掉`
  - `//SpringAI 自带的模块化Rag支持`
  - `//检索阶段，从向量库检索文档(必须）`
  - `//生成阶段，构建增强提示词`

## 12. RagFileController（文件上传下载）

- 路由前缀：`/rag/files`
- 作用：文件中转到 MinIO，以及生成下载链接
- 主要接口：
  - `GET /upload`：根据 `fileUrl` 下载并上传到 MinIO
  - `GET /download-url/{objectName}`：获取预签名下载链接
- 已有注释：
  - `// 从URL下载文件并转换为MutipartFile`
  - `// 从URL中提取文件名`
  - `// 获取文件内容类型`
  - `// 将输入流转换为字节数组`

## 13. RagRouterController（查询路由）

- 路由前缀：`/rag/router`
- 作用：把用户问题路由到不同处理链路
- 主要接口：
  - `GET /route`：返回路由结果

## 14. RagGenerateController（生成能力）

- 路由前缀：`/rag/generate`
- 作用：提供 `text2sql` 能力
- 主要接口：
  - `GET/POST /sql`：自然语言转 SQL
- 已有注释：
  - `//`（源码中该类顶部存在一个空注释）
