package cn.hollis.llm.mentor.agent.entity.vo;

import lombok.Data;

/**
 * 工具调用统计结果
 */
@Data
public class ToolCallStatsVO {

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 调用总数
     */
    private Long totalCount;

    /**
     * 成功次数
     */
    private Long successCount;

    /**
     * 失败次数
     */
    private Long failedCount;

    /**
     * 成功率百分比
     */
    private Double successRate;
}
