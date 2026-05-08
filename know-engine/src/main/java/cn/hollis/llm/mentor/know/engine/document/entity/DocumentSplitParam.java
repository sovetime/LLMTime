package cn.hollis.llm.mentor.know.engine.document.entity;


/**
 * 文档分段参数
 *
 * @param splitType  分段器类型，如 WORD、MARKDOWN、RECURSIVE、EXCEL
 * @param chunkSize  每段最大字符数
 * @param overlap    相邻分段重叠字符数
 * @param titleLevel 按标题切分时的标题层级
 * @param separator  递归分段的自定义分隔符
 * @param regex      正则分段的正则表达式
 */
public record DocumentSplitParam(String splitType,
                                 Integer chunkSize,
                                 Integer overlap,
                                 Integer titleLevel,
                                 String separator,
                                 String regex) {
}
