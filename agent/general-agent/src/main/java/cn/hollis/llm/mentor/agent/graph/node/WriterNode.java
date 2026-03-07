package cn.hollis.llm.mentor.agent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 写作节点支持首稿与基于反馈的改稿
 */
public class WriterNode implements NodeAction {

    private final ChatClient chatClient;

    public WriterNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String question = state.value("question", "");
        String researchNotes = state.value("researchNotes", "");
        String feedback = state.value("feedback", "");
        String currentDraft = state.value("draft", "");

        String prompt;
        if (feedback == null || feedback.isBlank()) {
            // 首次写作
            prompt = """
                    你是报告撰写人
                    用户问题：%s
                    研究笔记：
                    %s

                    请写一份完整报告草稿
                    要求
                    1. 包含标题、结论、正文分节
                    2. 语言清晰，避免口语化
                    3. 只输出报告正文
                    """.formatted(question, researchNotes);
        } else {
            // 审核不通过时按反馈改稿
            prompt = """
                    你是报告撰写人
                    用户问题：%s
                    当前草稿：
                    %s
                    审核意见：
                    %s
                    研究笔记：
                    %s

                    请根据审核意见改写草稿
                    要求
                    1. 必须逐条落实审核意见
                    2. 保持结构完整
                    3. 只输出修订后的完整报告
                    """.formatted(question, currentDraft, feedback, researchNotes);
        }

        String draft = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        Map<String, Object> map = new HashMap<>();
        map.put("draft", draft == null ? "" : draft);
        return map;
    }
}
