package cn.hollis.llm.mentor.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具调用日志
 */
@Data
@TableName("ai_tool_call_log")
public class AiToolCallLog {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 会话记录ID
     */
    @TableField("session_record_id")
    private Long sessionRecordId;

    /**
     * 智能体类型
     */
    @TableField("agent_type")
    private String agentType;

    /**
     * 工具调用ID
     */
    @TableField("tool_call_id")
    private String toolCallId;

    /**
     * 工具名称
     */
    @TableField("tool_name")
    private String toolName;

    /**
     * 调用参数JSON
     */
    @TableField("arguments")
    private String arguments;

    /**
     * 是否调用成功
     */
    @TableField("success")
    private Boolean success;

    /**
     * 失败原因
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 工具结果长度
     */
    @TableField("result_size")
    private Integer resultSize;

    /**
     * 调用耗时
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
