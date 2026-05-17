package cn.hollis.llm.mentor.agent.mapper;

import cn.hollis.llm.mentor.agent.entity.AiToolCallLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用日志 Mapper
 */
@Mapper
public interface AiToolCallLogMapper extends BaseMapper<AiToolCallLog> {
}
