package cn.hollis.llm.mentor.know.engine.chat.service;

import cn.hollis.llm.mentor.know.engine.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * AI对话消息表 Service 接口
 */
public interface ChatMessageService extends IService<ChatMessage> {

    /**
     * 根据会话ID获取消息列表
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> getMessagesByConversationId(String conversationId);

    /**
     * 根据消息ID获取消息
     *
     * @param messageId 消息ID
     * @return 消息信息
     */
    ChatMessage getByMessageId(String messageId);

    /**
     * 保存用户消息
     *
     * @param conversationId 会话ID
     * @param content        消息内容
     * @return 消息ID
     */
    String saveUserMessage(String conversationId, String content);

    /**
     * 更新问题改写结果（transformContent）
     *
     * @param messageId        消息ID
     * @param transformContent 改写后的问题
     */
    void updateTransformContent(String messageId, String transformContent);

    /**
     * 对话完成后保存 Assistant 消息（插入新记录）
     *
     * @param conversationId 会话ID
     * @param content        AI回复正文
     * @param modelName      模型名称
     * @param tokenCount     Token数量
     * @param ragReferences  RAG引用内容
     * @return 新消息ID
     */
    String saveAssistantMessage(String conversationId, String content, String modelName,
                                Integer tokenCount, List<ChatMessage.RagReference> ragReferences);

    /**
     * 删除会话的所有消息
     *
     * @param conversationId 会话ID
     * @return 是否成功
     */
    boolean deleteMessagesByConversationId(String conversationId);

    /**
     * 获取会话最近N条消息
     *
     * @param conversationId 会话ID
     * @param limit          消息数量
     * @return 消息列表
     */
    List<ChatMessage> getRecentMessages(String conversationId, int limit);
}
