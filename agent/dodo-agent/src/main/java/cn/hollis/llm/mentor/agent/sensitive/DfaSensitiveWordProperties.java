package cn.hollis.llm.mentor.agent.sensitive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 敏感词过滤配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "sensitive.word")
public class DfaSensitiveWordProperties {

    /**
     * 是否开启敏感词过滤
     */
    private boolean enabled = true;

    /**
     * 敏感词词库文件路径
     */
    private String dictionaryPath = "sensitive-words.txt";

    /**
     * 替换字符
     */
    private String maskChar = "*";
}
