package cn.hollis.llm.llmentor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;

//record JDK14引入，提供getter/setter方法
//@JsonPropertyDescription Jackson 注解，用于生成 JSON Schema 时的字段描述。
public record Book(@JsonPropertyDescription("书名，以中文展示") String name,
                   @JsonPropertyDescription("作者") String author,
                   @JsonPropertyDescription("简介") String desc,
                   @JsonPropertyDescription("价格，人民币，以分为单位") BigDecimal price,
                   @JsonPropertyDescription("出版社") String publisher) {

}
