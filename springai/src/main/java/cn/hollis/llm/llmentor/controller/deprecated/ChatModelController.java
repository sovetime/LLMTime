package cn.hollis.llm.llmentor.controller.deprecated;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 这里没有使用chatclient构建，不看了
 * 聊天模型控制器demo
 * 提供多种方式的模型调用接口，支持简单调用、消息调用、Prompt调用和流式调用
 */
@Deprecated
@RestController
@RequestMapping("/model")
public class ChatModelController {

    @Autowired
    private DashScopeChatModel dashScopeChatModel;

    /**
     * 简单消息调用
     * 直接将用户消息发送给模型，返回模型响应
     * @param message 用户消息
     * @return 模型响应内容
     */
    @RequestMapping("/call/string")
    public String callString(String message) {
        return dashScopeChatModel.call(message);
    }

    /**
     * 消息调用
     * 使用系统消息设定角色，用户消息进行交互
     * @param message 用户消息
     * @return 模型翻译后的响应
     */
    @RequestMapping("/call/messages")
    public String callMessages(String message) {
        SystemMessage systemMessage = new SystemMessage("你是一个翻译工具，请把用户的消息翻译成英文");
        Message userMsg = new UserMessage(message);
        return dashScopeChatModel.call(systemMessage, userMsg);
    }

    /**
     * Prompt调用
     * 使用Prompt构建器，支持自定义聊天选项如模型选择
     * @param message 用户消息
     * @return 模型响应内容
     */
    @RequestMapping("/call/prompt")
    public String callPrompt(String message) {
        SystemMessage systemMessage = new SystemMessage("请如实回答我的问题");
        Message userMsg = new UserMessage(message);

        ChatOptions chatOptions = ChatOptions.builder().model("deepseek-v3").build();
        Prompt prompt=new Prompt.Builder().messages(systemMessage, userMsg).chatOptions(chatOptions).build();
        return dashScopeChatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 流式调用
     * 以流式方式返回模型响应，支持实时输出
     * @param message 用户消息
     * @param response HTTP响应
     * @return 响应流
     */
    @RequestMapping("/stream/string")
    public Flux<String> callStreamString(String message, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return dashScopeChatModel.stream(message);
    }
}
