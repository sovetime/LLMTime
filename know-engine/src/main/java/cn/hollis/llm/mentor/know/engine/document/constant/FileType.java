package cn.hollis.llm.mentor.know.engine.document.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件类型
 */
@Getter
@AllArgsConstructor
public enum FileType {

    PDF("pdf"),
    DOC("doc"),
    TXT("txt"),
    HTML("html"),
    MARKDOWN("markdown"),
    CSV("csv"),
    EXCEL("excel");

    private final String type;
}
