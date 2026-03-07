package cn.hollis.llm.mentor.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规划Node，负责产出步骤计划
 */
public class PlannerNode implements NodeAction {

    private final ChatClient chatClient;

    public PlannerNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 从全局状态读取用户问题
        String question = state.value("question", "");
        String prompt = """
                用户问题：%s
                请制定一个清晰的研究计划，输出 3-5 个步骤
                要求
                1. 每个步骤单独一行
                2. 不要解释
                """.formatted(question);

        String result = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 将模型输出按行切分为步骤列表
        List<String> planSteps = Arrays.stream((result == null ? "" : result).split("\\R"))
                .map(String::trim)
                .filter(step -> !step.isEmpty())
                .toList();

        Map<String, Object> map = new HashMap<>();
        map.put("plan", planSteps);
        return map;
    }
}
