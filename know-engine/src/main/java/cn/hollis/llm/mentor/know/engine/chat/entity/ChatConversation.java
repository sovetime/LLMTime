package cn.hollis.llm.mentor.know.engine.chat.entity;

import cn.hollis.llm.mentor.know.engine.chat.constant.ChatConversationStatus;
import cn.hollis.llm.mentor.know.engine.document.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI对话会话表
 */
@Data
@TableName("chat_conversation")
public class ChatConversation extends BaseEntity {

    /**
     * 会话唯一标识
     */
    private String conversationId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 状态
     */
    private ChatConversationStatus status;
}
