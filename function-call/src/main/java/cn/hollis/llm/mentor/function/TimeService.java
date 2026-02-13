package cn.hollis.llm.mentor.function;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时区时间服务类
 * 提供基于时区的当前时间获取功能，支持函数调用模式
 * @author AI Assistant
 */
@Service
public class TimeService {
    
    /**
     * 根据请求的时区获取当前时间
     * @param request 包含时区ID的请求对象
     * @return 包含格式化时间的响应对象
     */
    public Response getTimeByZoneId(Request request) {
        System.out.println("getTimeByZoneId，zoneId=" + request.zoneId);
        ZoneId zid = ZoneId.of(request.zoneId);
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zid);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        return new Response(zonedDateTime.format(formatter));
    }

    /**
     * 获取时间的请求对象
     * @param zoneId 时区ID，例如 "Asia/Shanghai"
     */
    public record Request(@JsonProperty(required = true, value = "zoneId")
                          @JsonPropertyDescription("时区，比如 Asia/Shanghai") String zoneId) {}

    /**
     * 获取时间的响应对象
     * @param time 格式化的时间字符串
     */
    public record Response(String time) {
    }
}
