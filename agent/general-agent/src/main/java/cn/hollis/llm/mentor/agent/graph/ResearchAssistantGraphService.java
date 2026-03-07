package cn.hollis.llm.mentor.agent.graph;

import cn.hollis.llm.mentor.agent.graph.node.PlannerNode;
import cn.hollis.llm.mentor.agent.graph.node.ResearcherNode;
import cn.hollis.llm.mentor.agent.graph.node.ReviewerNode;
import cn.hollis.llm.mentor.agent.graph.node.WriterNode;
import cn.hollis.llm.mentor.agent.graph.route.ReviewerRouteAction;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;


/**
 * 研究助手图服务
 *
 * START → Planner（制定计划）
 *          → Researcher（收集资料）
 *          → Writer（撰写草稿）
 *          → Reviewer（评审）
 *              ├── 不通过 → Writer（修订，形成反思循环）
 *              └── 通过   → END
 */
@Service
public class ResearchAssistantGraphService {

    /**
     * 编译后的图实例。
     * Graph 的拓扑结构在启动时确定，运行时状态通过 threadId 隔离，
     * 因此可作为单例安全复用，避免每次请求重复构建的开销。
     */
    private final CompiledGraph compiledGraph;

    public ResearchAssistantGraphService(ChatClient.Builder chatClientBuilder) throws GraphStateException {
        ChatClient chatClient = chatClientBuilder.build();
        this.compiledGraph = buildGraph(chatClient);
    }

    // 构建Graph
    private CompiledGraph buildGraph(ChatClient chatClient) throws GraphStateException {
        // 定义全局状态字段及其更新策略
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            // 用户原始问题
            keyStrategyMap.put("question", new ReplaceStrategy());
            // planner 指定的计划（步骤列表）
            keyStrategyMap.put("plan", new AppendStrategy());
            // researcher 收集到的研究内容
            keyStrategyMap.put("researchNotes", new ReplaceStrategy());
            // Writer 生成的报告草稿
            keyStrategyMap.put("draft", new ReplaceStrategy());
            // Reviewer 的反馈
            keyStrategyMap.put("feedback", new ReplaceStrategy());
            // 是否通过审核
            keyStrategyMap.put("approved", new ReplaceStrategy());
            // 当前轮次（防止无线循环）
            keyStrategyMap.put("revisionCount", new ReplaceStrategy());
            return keyStrategyMap;
        };

        // 组装图结构并定义审核后的条件跳转
        StateGraph stateGraph = new StateGraph("research-assistant", keyStrategyFactory)
                // 注册四个功能节点，node_async 将同步 NodeAction 包装为异步执行
                .addNode("planner", node_async(new PlannerNode(chatClient)))
                .addNode("researcher", node_async(new ResearcherNode(chatClient)))
                .addNode("writer", node_async(new WriterNode(chatClient)))
                .addNode("reviewer", node_async(new ReviewerNode(chatClient)))

                //设置线性流程：START -> planner -> researcher -> writer -> reviewer
                .addEdge(StateGraph.START, "planner")
                .addEdge("planner", "researcher")
                .addEdge("researcher", "writer")
                .addEdge("writer", "reviewer")

                // 条件边：reviewer 审核后由 ReviewerRouteAction 决定下一跳
                //   - "writer" → 进入修订循环，Writer 基于 feedback 重新撰写
                //   - "end"    → 审核通过，流程终止
                .addConditionalEdges("reviewer", edge_async(new ReviewerRouteAction()), Map.of(
                        "writer", "writer",
                        "end", StateGraph.END
                ));

        // compile() 做静态校验（环检测、不可达节点检查等）并生成可执行对象
        return stateGraph.compile();
    }

    // 初始化图运行时状态
    public Map<String, Object> run(String question, String threadId) throws Exception {
        // 构造初始状态，仅设置图执行所需的最小必要字段
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("question", question);
        // 初始修订次数为 0
        initialState.put("revisionCount", 0);
        // 初始修订次数为 0
        initialState.put("approved", false);

        // threadId 注入 RunnableConfig，框架通过它区分不同会话的状态上下文
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // 同步阻塞直至图执行完毕，返回终态快照
        Optional<OverAllState> resultState = compiledGraph.invoke(initialState, runnableConfig);
        if (resultState.isEmpty()) {
            throw new IllegalStateException("Graph 执行失败，未返回状态");
        }

        // 提取核心字段，构造对外暴露的结果 DTO（使用 LinkedHashMap 保证字段顺序）
        OverAllState finalState = resultState.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", finalState.value("question", ""));
        result.put("plan", toStringList(finalState.value("plan").orElse(List.of())));
        result.put("researchNotes", finalState.value("researchNotes", ""));
        result.put("draft", finalState.value("draft", ""));
        result.put("feedback", finalState.value("feedback", ""));
        result.put("approved", finalState.value("approved", false));
        result.put("revisionCount", finalState.value("revisionCount", 0));
        return result;
    }

    /**
     * 提取核心字段，构造对外暴露的结果 DTO（使用 LinkedHashMap 保证字段顺序）
     */
    private List<String> toStringList(Object rawList) {
        if (!(rawList instanceof List<?> values)) {
            return List.of();
        }
        // 将状态中的混合对象统一转为字符串列表
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }
}
