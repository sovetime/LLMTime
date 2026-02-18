package cn.hollis.llm.mentor.function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * 将 Service服务的方法封装提供给AI模型调用
 */
@Configuration
public class FunctionCallConfiguration {
    
    /**
     * 配置时区时间获取函数，作为AI工具注册到模型中
     * @param timeService 时间服务实例
     * @return 函数接口，供AI模型调用
     */
    @Bean
    @Description("根据用户输入的时区获取该时区的当前时间")
    public Function<TimeService.Request, TimeService.Response> getTimeFunction(TimeService timeService) {
        return timeService::getTimeByZoneId;
    }
}
