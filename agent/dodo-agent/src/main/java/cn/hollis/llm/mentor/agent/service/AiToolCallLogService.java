package cn.hollis.llm.mentor.agent.service;

import cn.hollis.llm.mentor.agent.entity.AiToolCallLog;
import cn.hollis.llm.mentor.agent.entity.vo.ToolCallStatsVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 工具调用日志服务
 */
public interface AiToolCallLogService extends IService<AiToolCallLog> {

    /**
     * 统计工具调用成功率
     *
     * @param toolName 工具名称，为空时统计全部工具
     * @return 工具调用统计结果
     */
    ToolCallStatsVO getStats(String toolName);
}
