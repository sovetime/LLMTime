package cn.hollis.llm.mentor.know.engine.rag.modules.splitter;

import cn.hollis.llm.mentor.know.engine.document.constant.FileType;
import cn.hollis.llm.mentor.know.engine.document.constant.SplitType;
import cn.hollis.llm.mentor.know.engine.document.entity.DocumentSplitParam;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;


/**
 * 文档分段器工厂，根据分段参数创建对应的分段器实例
 */
public class DocumentSplitterFactory {
    public static DocumentSplitter getInstance(DocumentSplitParam documentSplitParam) {
        // 按标题层级分段
        if (SplitType.TITLE.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.titleLevel(), false, false, documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按固定长度分段
        if (SplitType.LENGTH.name().equals(documentSplitParam.splitType())) {
            return new DocumentByWordSplitter(documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按自定义分隔符分段
        if (SplitType.SEPARATOR.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.separator(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 按正则表达式分段
        if (SplitType.REGEX.name().equals(documentSplitParam.splitType())) {
            return new DocumentByRegexSplitter(documentSplitParam.regex(), "\\n\\n", documentSplitParam.chunkSize(), documentSplitParam.overlap());
        }

        // 智能分段，使用标题分段且 overlap 为 chunkSize 的 10%
        if (SplitType.SMART.name().equals(documentSplitParam.splitType())) {
            return new MarkdownHeaderParentTextSplitter(documentSplitParam.chunkSize(), (int) (documentSplitParam.chunkSize() * 0.1));
        }

        return null;
    }
}
