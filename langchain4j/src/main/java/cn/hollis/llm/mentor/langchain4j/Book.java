package cn.hollis.llm.mentor.langchain4j;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;

/**
 * 图书结构化输出对象，用于约束模型返回字段。
 */
public record Book(@JsonPropertyDescription("书籍名称") String title,
                   @JsonPropertyDescription("作者") String author,
                   @JsonPropertyDescription("书籍介绍") String description,
                   @JsonPropertyDescription("价格") BigDecimal price) {
}
