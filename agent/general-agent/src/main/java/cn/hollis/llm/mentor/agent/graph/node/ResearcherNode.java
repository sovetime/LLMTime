package cn.hollis.llm.mentor.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 研究节点Node,根据planner产出研究笔记
 */
public class ResearcherNode implements NodeAction {

    private final ChatClient chatClient;

    public ResearcherNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String question = state.value("question", "");
        // 计划来自 planner 节点的状态更新
        String planText = buildPlanText(state.value("plan").orElse(List.of()));

        String prompt = """
                你是研究员
                用户问题：%s
                研究计划：
                %s

                请基于计划输出研究笔记
                要求
                1. 每一步至少给出 2 条有效信息
                2. 输出结构化小标题
                3. 仅输出研究笔记正文
                """.formatted(question, planText);

        String notes = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        Map<String, Object> map = new HashMap<>();
        map.put("researchNotes", notes == null ? "" : notes);
        return map;
    }

    private String buildPlanText(Object rawPlan) {
        if (!(rawPlan instanceof List<?> planList) || planList.isEmpty()) {
            return "暂无计划";
        }

        // 转换为带序号文本便于模型理解
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < planList.size(); i++) {
            builder.append(i + 1).append(". ").append(planList.get(i)).append('\n');
        }
        return builder.toString();
    }
}
