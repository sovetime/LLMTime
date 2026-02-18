package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.tools.TemperatureTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

/**
 * LangChain4j 底层 API 示例，包含流式输出、记忆管理、结构化输出和工具调用
 * 上层开发都封装好了，不建议直接使用
 */
@RestController
@RequestMapping("/langchain/low")
public class LangChainLowLevelController {

    @Autowired
    OpenAiChatModel chatModel;

    @RequestMapping("/hello")
    public String hello() {
        return chatModel.chat("你好,你是谁？");
    }

    @Autowired
    OpenAiStreamingChatModel streamingChatModel;

    @RequestMapping("/streamTest")
    public void streamTest(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        streamingChatModel.chat("你好,给我推荐一些新疆美食？", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                System.out.println(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                System.out.println("===输出结束===");
            }

            @Override
            public void onError(Throwable error) {
                System.out.println(error);
            }
        });
    }

    // 流式输出
    @RequestMapping("/streamHello")
    public Flux<String> streamHello(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        // 将底层回调式流输出桥接为 Reactor Flux，便于前端消费。
        Flux<String> flux = Flux.create(fluxSink -> {
            streamingChatModel.chat("你好,给我推荐一些新疆美食？", new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fluxSink.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    fluxSink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    fluxSink.error(error);
                }
            });
        });
        return flux;
    }

    //不会使用，可以直接看下面使用chatMemory的
    @Deprecated
    @RequestMapping("/memory")
    public String memory(HttpServletResponse response) {
        // 手动维护消息列表，演示多轮对话上下文拼接。
        List<ChatMessage> messages = new ArrayList<>();

        //第一轮对话
        messages.add(systemMessage("你是一个点餐助手"));
        messages.add(userMessage("给我点一个汉堡，两个鸡腿，一杯可乐"));
        AiMessage answer = chatModel.chat(messages).aiMessage();
        System.out.println(answer);
        System.out.println("======");

        messages.add(answer);

        //第二轮对话
        messages.add(userMessage("刚才菜点多了，去掉一个鸡腿，再加一杯可乐吧?"));
        AiMessage answer1 = chatModel.chat(messages).aiMessage();
        System.out.println(answer1);
        System.out.println("======");

        messages.add(answer1);

        //第三轮对话
        messages.add(userMessage("我现在总共点了哪些东西？"));
        AiMessage answer2 = chatModel.chat(messages).aiMessage();
        System.out.println(answer2);
        System.out.println("======");

        return answer2.text();
    }

    @RequestMapping("/memory1")
    public String memory1(HttpServletResponse response) {
        // 使用 ChatMemory 统一管理会话窗口。
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        //第一轮对话
        chatMemory.add(systemMessage("你是一个点餐助手"));
        chatMemory.add(userMessage("给我点一个汉堡，两个鸡腿，一杯可乐"));
        AiMessage answer = chatModel.chat(chatMemory.messages()).aiMessage();
        System.out.println(answer);
        System.out.println("======");

        chatMemory.add(answer);

        //第二轮对话
        chatMemory.add(userMessage("刚才菜点多了，去掉一个鸡腿，再加一杯可乐吧"));
        AiMessage answer1 = chatModel.chat(chatMemory.messages()).aiMessage();
        System.out.println(answer1);
        System.out.println("======");

        chatMemory.add(answer1);

        //第三轮对话
        chatMemory.add(userMessage("我现在总共点了哪些东西？"));
        AiMessage answer2 = chatModel.chat(chatMemory.messages()).aiMessage();
        System.out.println(answer2);
        System.out.println("======");

        return answer2.text();
    }

    // 结构化输出
    @RequestMapping("/structure")
    public String structure() {
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("Person")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("name")
                                .addIntegerProperty("age")
                                .addNumberProperty("height")
                                .addBooleanProperty("married")
                                .required("name", "age", "height", "married")
                                .build())
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(UserMessage.from("""
                        John is 42 years old and lives an independent life.
                        He stands 1.75 meters tall and carries himself with confidence.
                        Currently unmarried, he enjoys the freedom to focus on his personal goals and interests.
                        """))
                .build();

        return chatModel.chat(chatRequest).aiMessage().text();
    }

    //工具调用
    @RequestMapping("tool")
    public String tool() {
        // 完整工具调用流程：声明工具、执行工具、回填结果、二次推理。
        //定义工具列表
        List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(TemperatureTools.class);

        //构造用户提示词
        UserMessage userMessage = UserMessage.from("2025年11月11日，杭州的气温怎样？");
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(userMessage);

        //创建ChatRequest，并指定工具列表
        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecifications)
                .toolChoice(ToolChoice.AUTO)
                .build();

        //调用模型
        ChatResponse response = chatModel.chat(request);
        AiMessage aiMessage = response.aiMessage();

        //把模型结果添加到 chatMessages中
        chatMessages.add(aiMessage);

        //执行工具
        List<ToolExecutionRequest> toolExecutionRequests = response.aiMessage().toolExecutionRequests();
        toolExecutionRequests.forEach(toolExecutionRequest -> {
            ToolExecutor toolExecutor = new DefaultToolExecutor(new TemperatureTools(), toolExecutionRequest);
            System.out.println("execute tool " + toolExecutionRequest.name());
            String result = toolExecutor.execute(toolExecutionRequest, UUID.randomUUID().toString());
            ToolExecutionResultMessage toolExecutionResultMessages = ToolExecutionResultMessage.from(toolExecutionRequest, result);
            //把工具执行结果添加到 chatMessages中
            chatMessages.add(toolExecutionResultMessages);
        });

        //重新构造ChatRequest，并使用之前的对话chatMessages，以及指定toolSpecifications
        ChatRequest finalRequest = ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(toolSpecifications)
                .build();

        //再次调用模型，返回结果
        ChatResponse finalChatResponse = chatModel.chat(finalRequest);
        return finalChatResponse.aiMessage().text();
    }
}
