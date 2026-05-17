# dodo-agent 敏感词过滤机制评估

## 结论

当前机制适合作为面试项目里的第一道输入安全防线：实现简单、性能可控、接入点明确，能在用户问题进入大模型前拦截词库中的显式敏感词。

但如果按生产级内容安全标准衡量，当前机制还不够。它更像是“基于本地词库的前置硬拦截”，可以挡住明确命中的敏感词，无法覆盖变体绕过、语义风险、输出风险、工具调用风险和动态治理能力。

面试时可以这样回答：

> 我们在用户输入进入 Agent 之前做了一层基于 AC 自动机的敏感词检测，命中后直接拒绝请求，不把风险内容传给大模型。这个方案性能好、实现轻量，适合做第一层规则拦截。但它不是完整的内容安全系统，生产上还需要补充文本归一化、动态词库、上下文语义审核、模型输出审核、审计告警和测试覆盖。

## 当前主要流程

### 1. 配置加载

配置类是 `cn.hollis.llm.mentor.agent.sensitive.SensitiveWordProperties`，通过 `@ConfigurationProperties(prefix = "sensitive.word")` 绑定配置。

当前 `application.yml` 中配置如下：

```yaml
sensitive:
  word:
    enabled: true
    dictionary-path: sensitive-words.txt
    mask-char: "*"
```

关键配置：

- `enabled`：是否开启敏感词过滤
- `dictionaryPath`：classpath 下的词库路径，默认 `sensitive-words.txt`
- `maskChar`：日志中展示命中文本时使用的掩码字符
- `skipChars`：匹配时允许忽略的干扰字符，目前配置文件中没有显式配置

### 2. 词库加载

服务类是 `SensitiveWordFilterService`。

应用启动后执行 `@PostConstruct init()`：

1. 如果 `sensitive.word.enabled=false`，直接跳过
2. 如果开启，则调用 `reload()`
3. `reload()` 从 classpath 读取 `sensitive-words.txt`
4. 逐行读取词库，跳过空行和 `#` 开头的注释行
5. 使用读取到的词构建 AC 自动机
6. 通过 `volatile AcAutomaton automaton` 一次性替换当前生效的自动机

这里的设计优点是：词库重载时先构建新自动机，构建完成后再替换引用，查询线程拿到的是完整快照，不会读到半初始化状态。

### 3. AC 自动机匹配

`SensitiveWordFilterService.AcAutomaton` 内部维护 trie 节点和 fail 指针。

构建过程：

1. 把每个敏感词插入 trie
2. 通过 BFS 构建 fail 指针
3. 把 fail 节点上的输出词合并到当前节点

匹配过程：

1. 从根节点开始扫描文本
2. 当前字符没有子节点时沿 fail 指针回退
3. 命中输出词时记录命中区间和敏感词
4. 最终返回所有命中结果

这个算法的复杂度接近 `O(文本长度 + 命中数量)`，比对每个敏感词逐个 `contains` 更适合词库较多的场景。

### 4. 跳字符匹配

如果配置了 `skipChars`，会走 `matchWithSkipChars()`。

它会先生成一份去掉干扰字符的 cleanText，同时保留 cleanText 下标到原始文本下标的映射。命中后再把 cleanText 中的命中区间映射回原文区间。

例如词库中有“赌博”，用户输入“赌 博”，如果空格被配置为跳过字符，就可以命中。

当前 `application.yml` 没有配置 `skipChars`，所以默认不会启用这类绕过处理。

### 5. 过滤结果生成

`filter(String text)` 返回 `SensitiveWordFilterResult`，字段包括：

- `originalText`：原始输入
- `filteredText`：命中区间被掩码替换后的文本
- `hit`：是否命中
- `hitWords`：命中的敏感词列表

注意：当前系统没有把 `filteredText` 继续传给大模型，而是命中后直接拒绝请求。`filteredText` 主要用于日志排查。

### 6. Controller 接入

当前接入点在 `AgentController` 的 `filterQuery()` 方法。

已覆盖的接口：

- `/agent/chat/stream`：网页搜索 Agent
- `/agent/file/stream`：文件问答 Agent
- `/agent/pptx/stream`：PPT 生成 Agent
- `/agent/deep/stream`：深度研究 Agent

处理逻辑：

1. Controller 先校验 query 是否为空
2. 初始化对应 Agent 和会话记忆
3. 调用 `filterQuery(query, conversationId, scene)`
4. `filterQuery()` 调用 `sensitiveWordService.filter(query)`
5. 如果命中，记录 warn 日志，并抛出 `IllegalArgumentException("输入包含敏感词，请重新提问")`
6. 如果未命中，原样返回 query，继续进入 Agent

也就是说，这套机制是在用户输入进入大模型之前做硬拒绝，而不是替换后继续执行。

## 当前机制的优点

- 性能较好：AC 自动机适合多关键词匹配，扫描一次文本即可发现多个敏感词
- 接入位置合理：拦在大模型调用前，避免敏感输入进入 Agent 推理链路
- 线程安全思路正确：自动机通过 `volatile` 快照发布，重载时不会影响正在执行的匹配
- 结果结构清晰：原文、掩码文本、命中状态、命中词分开表达
- 支持基础绕过处理：代码支持 `skipChars`，具备处理空格、符号插入绕过的扩展点
- 配置简单：可通过配置开关控制是否启用，通过 classpath 词库维护规则

## 当前不足

### 1. 只覆盖部分入口

当前只在 `AgentController` 的四个流式接口中调用 `filterQuery()`。如果项目里还有其他 Controller、工具接口、异步任务入口、MCP 调用入口或内部服务直接调用大模型，就可能绕过这套过滤。

更稳的做法是把过滤下沉到统一的请求拦截层、Advisor、ChatClient 包装层，或 Agent 执行前置钩子里。

### 2. 只过滤用户输入，不过滤模型输出

当前机制只处理 query，不处理大模型输出内容。

风险场景：

- 模型生成了不合规内容
- 搜索结果或文件内容中包含敏感内容，被模型总结输出
- 工具调用返回了敏感文本

生产上通常需要输入审核和输出审核两层。

### 3. 对变体绕过支持有限

目前默认没有配置 `skipChars`，也没有看到统一的文本归一化逻辑。

可能绕过的形式包括：

- 繁简体转换
- 全角半角混写
- 大小写变体
- 同音字、形近字
- emoji 或零宽字符插入
- 拼音、谐音、拆字表达
- 多语言混写

本地词库规则适合命中显式词，不适合判断语义变体。

### 4. 词库是静态 classpath 文件

词库位于 `sensitive-words.txt`，应用启动或手动 `reload()` 时加载。

不足：

- 没有后台管理能力
- 没有版本号和变更审计
- 没有灰度发布
- 没有多租户、多场景差异化规则
- 词库变更通常需要重新打包或至少替换资源后触发重载

生产上更常见的是数据库、配置中心或对象存储托管词库，并记录版本。

### 5. 日志中记录了命中词

当前命中后日志会打印 `hitWords` 和 `filteredText`。

`filteredText` 已做掩码，相对安全；但 `hitWords` 是原始敏感词，严格场景下可能造成敏感信息进入日志系统。

建议面试时主动说明：演示项目为了排查方便记录命中词，生产环境可以只记录规则 ID、词库版本、风险等级和脱敏后的文本。

### 6. 缺少测试覆盖

当前没有看到 `agent/dodo-agent/src/test` 目录，也没有敏感词过滤相关单测。

建议至少补充：

- 普通命中
- 多词命中
- 重叠词命中
- 不命中
- 空文本
- 注释行和空行词库加载
- skipChars 绕过
- 掩码区间正确性
- 词库为空时行为

### 7. 缺少风险分级

当前命中任意敏感词后都是同一个行为：拒绝请求。

实际业务中可以按风险分级处理：

- 高风险：直接拒绝
- 中风险：提示用户改写
- 低风险：脱敏后继续
- 疑似风险：进入模型审核或人工审核

## 这套机制“够不够”

如果目标是课程项目、面试展示、Demo 或内部低风险系统：基本够用。它能说明你考虑了大模型输入安全，并且没有采用低效的逐词扫描，而是使用了 AC 自动机。

如果目标是生产级大模型应用：不够。它只能作为第一层规则过滤，需要和更多安全能力组合使用。

推荐面试表述：

> 当前方案够做第一层防线，但不能单独作为完整内容安全方案。它的定位是快速、确定性地拦截词库中明确存在的风险词。生产环境我会继续补充六类能力：文本归一化、动态词库、场景化规则、输入输出双向审核、语义审核模型、审计告警与灰度发布。

## 可优化方向

### 短期优化

- 在 `application.yml` 中配置常见 `skipChars`，例如空格、横线、下划线、点号等
- 增加文本归一化：trim、大小写统一、全角半角转换、繁简转换、零宽字符清理
- 补充 `SensitiveWordFilterService` 单元测试
- 日志中避免直接打印原始 `hitWords`，改为打印命中数量、规则 ID 或脱敏词
- 增加词库加载失败告警，避免静默降级为无过滤

### 中期优化

- 把词库迁移到数据库或配置中心
- 给词库增加版本、分类、风险等级、启停状态
- 支持按场景配置不同规则，例如 webSearch、file、pptx、deep
- 提供管理接口或后台页面，支持热更新
- 把过滤从 Controller 私有方法抽到统一组件，避免新接口遗漏

### 长期优化

- 增加模型输出审核
- 增加文件内容、搜索结果、工具返回内容审核
- 引入语义审核模型处理隐晦表达和上下文风险
- 建立审计报表，包括命中趋势、场景分布、规则效果
- 支持人审闭环，把误杀和漏杀反馈回词库或审核模型

## 面试回答模板

可以按下面结构回答：

1. 先讲定位：这是大模型调用前的输入安全前置拦截
2. 再讲实现：本地词库加载后构建 AC 自动机，匹配复杂度接近线性
3. 再讲流程：Controller 收到 query 后先调用过滤服务，命中则拒绝，不进入 Agent
4. 再讲优点：性能好、线程安全、接入简单、结果可观测
5. 最后主动讲不足：只做输入规则过滤，不覆盖输出、语义变体、动态词库和审计闭环

示例回答：

> 我们现在的敏感词过滤是在用户请求进入 Agent 前做的。应用启动时从 classpath 加载 `sensitive-words.txt`，构建 AC 自动机。用户调用网页搜索、文件问答、PPT 生成或深度研究接口时，Controller 会先调用 `SensitiveWordFilterService.filter()`，如果命中敏感词，就记录脱敏日志并返回“输入包含敏感词，请重新提问”，不会把这段输入交给大模型。AC 自动机的好处是可以一次扫描完成多词匹配，性能比逐个关键词 contains 更好。这个方案适合作为第一层规则防线，但生产上还需要继续补充文本归一化、动态词库、输出审核、工具结果审核和语义审核模型。

## 相关代码位置

- `agent/dodo-agent/src/main/java/cn/hollis/llm/mentor/agent/sensitive/SensitiveWordProperties.java`
- `agent/dodo-agent/src/main/java/cn/hollis/llm/mentor/agent/sensitive/SensitiveWordFilterService.java`
- `agent/dodo-agent/src/main/java/cn/hollis/llm/mentor/agent/sensitive/SensitiveWordFilterResult.java`
- `agent/dodo-agent/src/main/java/cn/hollis/llm/mentor/agent/controller/AgentController.java`
- `agent/dodo-agent/src/main/resources/application.yml`
- `agent/dodo-agent/src/main/resources/sensitive-words.txt`
