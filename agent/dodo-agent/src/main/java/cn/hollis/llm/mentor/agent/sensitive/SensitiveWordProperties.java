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
public class SensitiveWordProperties {

    /**
     * 是否开启敏感词过滤
     */
    private boolean enabled = true;

    /**
     * 敏感词词库文件路径（classpath）
     */
    private String dictionaryPath = "sensitive-words.txt";

    /**
     * 掩码字符（仅用于日志中展示过滤后文本，不送给大模型）
     */
    private String maskChar = "*";

    /**
     * 匹配时忽略的字符（空字符串表示不忽略）
     * 例如 " -._@" 可绕过"赌 博"这类干扰
     */
    private String skipChars = "";
}
