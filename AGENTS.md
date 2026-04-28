# Repository Guidelines

### 注意事项
- 回答均用中文回答
- 思考时间不要太久,希望给用户更好的体验
- 用户明确要求修改代码时（如生成下注释、生成对应代码等等）必须直接修改仓库文件,不要只给示例代码
- 代码注释统一使用中文,中文注释不需要使用句号
- 所有源码和配置文件统一使用 UTF-8 无 BOM 编码,避免出现 `java: 非法字符: '\ufeff'`
- 回退代码时禁止整文件覆盖用户未提交改动,优先使用最小范围回退并先确认影响

## 项目结构与模块组织
本仓库是 Maven 多模块项目（根目录 `pom.xml`）。核心模块包括：
- `springai`、`rag`、`function-call`、`langchain4j`
- `know-engine`（知识引擎，包含 RAG 向量存储等功能）
- `agent/general-agent`、`agent/dodo-agent`
- `mcp/*`（`mcp-client`、`mcp-server-sse`、`mcp-server-stdio`、`mcp-server-streamable`、`mcp-server-sse-https`）

各模块统一采用标准目录：
- 源码：`module/src/main/java`
- 测试：`module/src/test/java`
- 资源：`module/src/main/resources`

## 构建、测试与开发命令
以下命令默认在仓库根目录执行。
- `mvn clean compile`：编译全部模块。
- `mvn clean install`：完整构建并执行测试。
- `mvn clean install -DskipTests`：跳过测试，快速构建。
- `mvn test`：运行全部测试。
- `mvn test -Dtest=ClassName`：运行单个测试类。
- `mvn spring-boot:run -pl springai`：本地启动 `springai` 模块。
- `mvn clean package -pl rag`：仅打包 `rag` 模块。

## 代码风格与命名规范
- 技术栈：Java 21、Spring Boot 3.5.x、Spring AI 1.1.x。
- 使用 4 空格缩进，方法保持清晰且尽量简短。
- 命名规则：
  - 类名：`PascalCase`（如 `SimpleReactAgent`）
  - 方法/变量：`camelCase`
  - 常量：`UPPER_SNAKE_CASE`
  - 包名：全小写（如 `cn.hollis.llm.mentor.rag`）
- 优先使用构造器注入，组件使用 `@Service` / `@Component` / `@RestController`。

## 测试指南
- 测试框架：JUnit 5（位于 `src/test/java`）。
- 测试方法名应体现行为，例如 `shouldReturnOrder_whenOrderExists`。
- 推荐采用 Given-When-Then 结构。
- 可用如下命令运行定向测试：
  - `mvn test -Dtest=cn.hollis.llm.mentor.rag.splitter.WordHeaderTextSplitterTest`

## 提交与合并请求规范
- 当前历史提交较简短（如 `代码初始化`、`SpringAI demo`），建议保持“短句+明确目的”。
- 推荐格式：`<module>: <change summary>`，例如 `rag: add markdown splitter fallback`。
- PR 需包含：
  - 变更目的与范围
  - 影响模块
  - 测试证据（命令与结果）
  - 配置/API Key 影响与脱敏确认

## 安全与配置提示
- 不要提交真实密钥，生产环境请使用环境变量或密钥管理服务。
- 仓库启用了资源过滤，发布前请检查配置占位符是否正确替换。

## graphify


这个项目在 `graphify-out/` 目录下维护了一份 graphify 知识图谱

规则：
- 仅在处理 `agent/dodo-agent` 项目相关问题时读取和使用 graphify，其他项目默认不读取，因为尚未建立对应图谱
- 在回答架构或代码库相关问题之前，先阅读 `graphify-out/GRAPH_REPORT.md`，了解关键枢纽节点和社区结构
- 如果存在 `graphify-out/wiki/index.md`，优先通过它进行导航，而不是直接读取原始文件
- 对于跨模块的“X 和 Y 是什么关系”这类问题，优先使用 `graphify query "<问题>"`、`graphify path "<A>" "<B>"` 或 `graphify explain "<概念>"`，不要优先用 `grep`，因为这些命令会基于图谱中的 EXTRACTED 和 INFERRED 边进行遍历，而不是简单扫描文本
- 在本次会话中修改代码文件后，执行 `graphify update .` 来更新图谱（仅 AST 分析，不会产生 API 成本）
