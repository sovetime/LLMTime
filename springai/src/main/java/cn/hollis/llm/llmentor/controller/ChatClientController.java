package cn.hollis.llm.llmentor.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * ChatClient 控制器
 * Spring AI ChatClient demo
 */
@RestController
@RequestMapping("/client")
public class ChatClientController implements InitializingBean {

    @Autowired
    private ChatModel dashScopeChatModel;

    private ChatClient chatClient;

    /**
     * 简单调用
     */
    @GetMapping("/simpleCall")
    public String simpleCall(String message) {
        return chatClient.prompt(message)
                .call().content();
    }

    /**
     * 覆盖系统提示词
     */
    @GetMapping("/callOverwrite")
    public String callOverwrite(String message) {
        return chatClient.prompt(message)
                .system("无论问啥都回复不知道")
                .call().content();
    }

    /**
     * 单独设置用户消息
     */
    @GetMapping("/callUser")
    public String callUser(String message) {
        return chatClient.prompt()
                .user(message)
                .call().content();
    }

    /**
     * 同时设置系统消息和用户消息
     */
    @GetMapping("/call")
    public String call(String message) {
        return chatClient.prompt(
                new Prompt(new SystemMessage("无论问啥都回复不知道"), new UserMessage(message)))
                .call().content();
    }

    /**
     * 流式调用
     */
    @GetMapping("/stream")
    public Flux<String> stream(String message) {
        return chatClient.prompt(message).stream().content();
    }

    /**
     * 初始化 ChatClient
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        chatClient = ChatClient.builder(dashScopeChatModel)
                // 打印请求和响应日志 advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 默认系统提示词
                .defaultSystem("请用中文回答问题")
                //额外参数，模型名称、温度、最大生成token、top-k等等
                .defaultOptions(
                    DashScopeChatOptions.builder()
                        .temperature(0.7)// 温度参数
                        .build()
                )
                .build();
    }
}
