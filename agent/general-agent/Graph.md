## Spring AI Alibaba Graph 流程说明


### 1. 定义State

### 2. 定义Node
每一个Node都是一个单独的函数，在Java中，可以定义为一个单独的类，然后让他实现NodeAction接口，重写apply方法。这个apply方法的实现就就是这个节点需要做的事情

### 1. 定义State

### 1. 定义State



本文档对应 `agent/general-agent` 模块中已实现的研究助手 Graph 流程

## 1. 入口与整体结构

- HTTP 入口
  - `GET /graph/research?question=xxx&conversationId=yyy`
  - `conversationId` 可选，不传时自动生成 `threadId`
- Controller
  - `agent/general-agent/src/main/java/cn/hollis/llm/mentor/agent/controller/GraphResearchController.java`
- Graph 服务
  - `agent/general-agent/src/main/java/cn/hollis/llm/mentor/agent/graph/ResearchAssistantGraphService.java`

## 2. State 定义

在 `ResearchAssistantGraphService#buildGraph` 中通过 `KeyStrategyFactory` 定义状态字段和更新策略

- `question`：用户问题，`ReplaceStrategy`
- `plan`：规划步骤列表，`AppendStrategy`
- `researchNotes`：研究笔记，`ReplaceStrategy`
- `draft`：报告草稿，`ReplaceStrategy`
- `feedback`：审核意见，`ReplaceStrategy`
- `approved`：是否通过审核，`ReplaceStrategy`
- `revisionCount`：修订轮次，`ReplaceStrategy`

## 3. Node 定义

每个节点都实现 `NodeAction`，在 `apply(OverAllState state)` 中读取状态并返回增量更新

- Planner 节点
  - 文件：`agent/general-agent/src/main/java/cn/hollis/llm/mentor/agent/graph/node/PlannerNode.java`
  - 功能：读取 `question`，生成计划步骤，写入 `plan`

- Researcher 节点
  - 文件：`agent/general-agent/src/main/java/cn/hollis/llm/mentor/agent/graph/node/ResearcherNode.java`
  - 功能：读取 `question + plan`，生成研究笔记，写入 `researchNotes`

- Writer 节点
  - 文件：`agent/general-agent/src/main/java/cn/hollis/llm/mentor/agent/graph/node/WriterNode.java`
  - 功能：
    - 无 `feedback` 时生成首稿
    - 有 `feedback` 时基于意见改稿
  - 输出：`draft`

- Reviewer 节点
  - 文件：`agent/general-agent/src/main/java/cn/hollis/llm/mentor/agent/graph/node/ReviewerNode.java`
  - 功能：审核 `draft`，解析 `APPROVED` 与 `FEEDBACK`
  - 输出：`approved`、`feedback`、`revisionCount + 1`

## 4. Edge 与条件路由

在 `ResearchAssistantGraphService#buildGraph` 里定义边

- 固定主链路
  - `START -> planner -> researcher -> writer -> reviewer`

- 条件边
  - 路由实现：`agent/general-agent/src/main/java/cn/hollis/llm/mentor/agent/graph/route/ReviewerRouteAction.java`
  - 规则：
    - `approved == true` 或 `revisionCount >= 3`：返回 `end`
    - 否则返回 `writer` 继续修订

## 5. 编译与执行

- 编译
  - `CompiledGraph compiledGraph = stateGraph.compile()`
- 运行
  - 初始化状态：
    - `question`
    - `revisionCount = 0`
    - `approved = false`
  - 运行调用：
    - `compiledGraph.invoke(initialState, runnableConfig)`
  - `runnableConfig` 使用 `threadId` 支持会话隔离

## 6. 返回结果

Graph 完成后返回以下字段

- `question`
- `plan`
- `researchNotes`
- `draft`
- `feedback`
- `approved`
- `revisionCount`
- `threadId`

## 7. 快速调用示例

```http
GET /graph/research?question=请分析2026年AI智能体的发展趋势
```

或指定会话

```http
GET /graph/research?question=请重写上次报告并补充风险项&conversationId=demo-thread-001
```

