package cn.hollis.llm.mentor.know.engine.rag.modules.splitter;

import cn.hollis.llm.mentor.know.engine.document.constant.FileType;
import cn.hollis.llm.mentor.know.engine.document.constant.SplitType;
import cn.hollis.llm.mentor.know.engine.document.entity.DocumentSplitParam;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;

/**
 * 文档分割器工厂类
 * 根据分割类型返回对应的文档分割器实例
 */
public class DocumentSplitterFactory {
    /**
     * 获取文档分割器实例
     *
     * @param documentSplitParam 文档分割参数，包含分割类型、块大小、重叠度等配置
     * @return 对应的文档分割器实例，如果分割类型不支持则返回 null
     */
    public static DocumentSplitter getInstance(DocumentSplitParam documentSplitParam) {
        // 按标题分割：使用 Markdown 标题层级进行分割，支持父子关系
        if (SplitType.TITLE.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按长度分割：按单词数量分割文档
        if (SplitType.LENGTH.name().equals(documentSplitParam.splitType())) {
            return new DocumentByWordSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按分隔符分割：使用自定义分隔符进行分割
        if (SplitType.SEPARATOR.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.separator(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按正则表达式分割：使用自定义正则表达式进行分割
        if (SplitType.REGEX.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.regex(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 智能分割：自动选择最佳分割策略，使用 Markdown 标题分割并自动计算 10% 的重叠度
        if (SplitType.SMART.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.chunkSize(), (int) (documentSplitParam.chunkSize() * 0.1));
        }

        return null;
    }
}
