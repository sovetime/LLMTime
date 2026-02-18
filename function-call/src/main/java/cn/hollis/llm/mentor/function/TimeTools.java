package cn.hollis.llm.mentor.function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 之前没有的新工具，可以直接通过@Tool注册为AI模型的工具
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
