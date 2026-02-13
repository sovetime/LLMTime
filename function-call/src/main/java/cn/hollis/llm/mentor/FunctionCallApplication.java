package cn.hollis.llm.mentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 函数调用应用入口类
 * 提供Spring Boot应用启动支持，集成AI函数调用功能
 * @author AI Assistant
 */
@SpringBootApplication
@EnableScheduling
public class FunctionCallApplication {

    public static void main(String[] args) {
        SpringApplication.run(FunctionCallApplication.class, args);
    }

}
