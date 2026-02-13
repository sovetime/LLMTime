package cn.hollis.llm.mentor.langchain4j.service;

import cn.hollis.llm.mentor.langchain4j.Book;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

/**
 * 高层 AiService 定义，LangChain4j 会基于接口声明自动生成实现。
 */
@AiService
public interface LangChainAiService {

    /**
     * 单轮问答
     */
    String chat(String userMessage);

    /**
     * 流式问答
     */
    Flux<String> chatStream(String userMessage);

    /**
     * 模板化提示词示例
     */
    @SystemMessage("你是一个毒舌博主，擅长怼人。")
    @UserMessage("针对用户的内容：{{topic}}，先复述一遍他的问题，然后再回答。")
    Flux<String> chatTemplate(String topic);

    /**
     * 结构化输出示例，返回图书推荐对象。
     */
    @SystemMessage("你是一个专业的图书推荐人员。")
    @UserMessage("请帮我推荐3本和 Java 相关的书")
    Book getBooks();
}
