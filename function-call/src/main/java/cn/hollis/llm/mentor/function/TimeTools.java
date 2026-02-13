package cn.hollis.llm.mentor.function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时区时间获取工具类
 * 提供基于时区获取当前时间的功能，作为AI模型的工具调用
 * @author AI Assistant
 */
public class TimeTools {
    
    /**
     * 根据时区ID获取指定时区的当前时间
     * @param zoneId 时区ID，例如 "Asia/Shanghai"、"America/New_York"
     * @return 格式化的时间字符串，格式为 "yyyy-MM-dd HH:mm:ss z"
     */
    @Tool(name = "getTimeByZoneId", description = "Get time by zone id")
    public String getTimeByZoneId(@ToolParam(description = "Time zone id, such as Asia/Shanghai") String zoneId) {
        System.out.println("getTimeByZoneId，zoneId=" + zoneId);
        ZoneId zid = ZoneId.of(zoneId);
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zid);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        return zonedDateTime.format(formatter);
    }
}
