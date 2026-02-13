# Repository Guidelines

### 注意事项
- 回答均用中文回答
- 思考时间不要太久，希望给用户更好的体验
- 中文注释不需要使用句号

## 项目结构与模块组织
本仓库是 Maven 多模块项目（根目录 `pom.xml`）。核心模块包括：
- `springai`、`rag`、`function-call`、`langchain4j`
- `agent/general-agent`
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
