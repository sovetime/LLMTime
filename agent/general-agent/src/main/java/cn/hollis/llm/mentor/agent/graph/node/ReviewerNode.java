package cn.hollis.llm.mentor.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 审核节点负责给出是否通过和修改建议
 */
public class ReviewerNode implements NodeAction {

    private final ChatClient chatClient;

    public ReviewerNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String draft = state.value("draft", "");
        int revisionCount = state.value("revisionCount", 0);

        String prompt = """
                你是审核员，请审核以下报告草稿
                %s

                请按以下格式输出
                APPROVED: true 或 false
                FEEDBACK: 你的审核意见
                """.formatted(draft);

        String reviewResult = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        String safeReviewResult = reviewResult == null ? "" : reviewResult;
        // 从结构化文本中解析审核结果
        boolean approved = parseApproved(safeReviewResult);
        String feedback = parseFeedback(safeReviewResult);

        Map<String, Object> map = new HashMap<>();
        map.put("approved", approved);
        map.put("feedback", feedback);
        map.put("revisionCount", revisionCount + 1);
        return map;
    }

    private boolean parseApproved(String reviewResult) {
        String lowerText = reviewResult.toLowerCase(Locale.ROOT);
        if (lowerText.contains("approved: true")) {
            return true;
        }
        if (lowerText.contains("approved: false")) {
            return false;
        }
        return lowerText.contains("通过");
    }

    private String parseFeedback(String reviewResult) {
        // 优先解析 FEEDBACK 行
        String[] lines = reviewResult.split("\\R");
        for (String line : lines) {
            String trimLine = line.trim();
            if (trimLine.toLowerCase(Locale.ROOT).startsWith("feedback:")) {
                return trimLine.substring("feedback:".length()).trim();
            }
        }
        return reviewResult;
    }
}
