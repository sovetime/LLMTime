package cn.hollis.llm.mentor.agent.service.impl;

import cn.hollis.llm.mentor.agent.entity.AiToolCallLog;
import cn.hollis.llm.mentor.agent.entity.vo.ToolCallStatsVO;
import cn.hollis.llm.mentor.agent.mapper.AiToolCallLogMapper;
import cn.hollis.llm.mentor.agent.service.AiToolCallLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 工具调用日志服务实现
 */
@Service
public class AiToolCallLogServiceImpl extends ServiceImpl<AiToolCallLogMapper, AiToolCallLog>
        implements AiToolCallLogService {

    @Override
    public ToolCallStatsVO getStats(String toolName) {
        LambdaQueryWrapper<AiToolCallLog> totalQuery = buildToolNameQuery(toolName);
        long totalCount = this.count(totalQuery);

        LambdaQueryWrapper<AiToolCallLog> successQuery = buildToolNameQuery(toolName)
                .eq(AiToolCallLog::getSuccess, true);
        long successCount = this.count(successQuery);

        long failedCount = totalCount - successCount;
        double successRate = totalCount == 0 ? 0 : successCount * 100.0 / totalCount;

        ToolCallStatsVO stats = new ToolCallStatsVO();
        stats.setToolName(StringUtils.isBlank(toolName) ? "ALL" : toolName);
        stats.setTotalCount(totalCount);
        stats.setSuccessCount(successCount);
        stats.setFailedCount(failedCount);
        stats.setSuccessRate(successRate);
        return stats;
    }

    private LambdaQueryWrapper<AiToolCallLog> buildToolNameQuery(String toolName) {
        LambdaQueryWrapper<AiToolCallLog> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(toolName)) {
            queryWrapper.eq(AiToolCallLog::getToolName, toolName);
        }
        return queryWrapper;
    }
}
