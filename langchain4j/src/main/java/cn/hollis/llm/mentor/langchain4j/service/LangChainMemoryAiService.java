package cn.hollis.llm.mentor.langchain4j.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * 带会话记忆能力的 AiService
 */
@AiService
public interface LangChainMemoryAiService {

    /**
     * 基于 memoryId 进行多轮对话隔离
     */
    String chatMemory(@MemoryId String memoryId, @UserMessage String userMessage);
}
