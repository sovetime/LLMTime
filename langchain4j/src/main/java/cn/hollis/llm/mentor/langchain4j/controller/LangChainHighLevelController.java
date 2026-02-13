package cn.hollis.llm.mentor.langchain4j.controller;

import cn.hollis.llm.mentor.langchain4j.Book;
import cn.hollis.llm.mentor.langchain4j.chatmemory.RedisChatMemoryStore;
import cn.hollis.llm.mentor.langchain4j.service.LangChainAiService;
import cn.hollis.llm.mentor.langchain4j.service.LangChainMemoryAiService;
import cn.hollis.llm.mentor.langchain4j.tools.TemperatureTools;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * LangChain4j 高层 API 示例，演示 AiService 的常见能力封装。
 */
@RequestMapping("/langchain/high")
@RestController
public class LangChainHighLevelController implements InitializingBean {

    @Autowired
    private LangChainAiService aiService;

    // 单轮问答。
    @RequestMapping("/chat")
    public String chat(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return aiService.chat("日本都有哪些美食？");
    }

    //流式问答
    @RequestMapping("/streamChat")
    public Flux<String> streamChat(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return aiService.chatStream("日本都有哪些美食？");
    }

    //模板化提示词示例
    @RequestMapping("/chatTemplate")
    public Flux<String> chatTemplate(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return aiService.chatTemplate("我饿了？");
    }

    //结构化输出示例
    @RequestMapping("/structure")
    public String structure(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        Book books = aiService.getBooks();
        return JSON.toJSONString(books);
    }

    @Autowired
    private OpenAiChatModel chatModel;

    private LangChainMemoryAiService langChainMemoryAiService;

    //基于 memoryId 进行多轮对话隔离
    @RequestMapping("/memoryChat")
    public String memoryChat(HttpServletResponse response, String msg, String memoryId) {
        response.setCharacterEncoding("UTF-8");
        // memoryId 用于隔离不同会话上下文
        return langChainMemoryAiService.chatMemory(memoryId, msg);
    }

    //工具调用示例
    @RequestMapping("/toolCalling")
    public String toolCalling(HttpServletResponse response, String msg) {
        response.setCharacterEncoding("UTF-8");

        // 动态创建带工具能力的 AiService
        LangChainAiService langChainAiService = AiServices.builder(LangChainAiService.class)
                .tools(new TemperatureTools())
                .chatModel(chatModel)
                .build();

        return langChainAiService.chat(msg);
    }

    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public void afterPropertiesSet() throws Exception {
        // 启动时初始化带记忆能力的服务实例。
        langChainMemoryAiService = AiServices.builder(LangChainMemoryAiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
//                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder().id(memoryId).maxMessages(10).chatMemoryStore(redisChatMemoryStore).build())
                .build();
    }
}
