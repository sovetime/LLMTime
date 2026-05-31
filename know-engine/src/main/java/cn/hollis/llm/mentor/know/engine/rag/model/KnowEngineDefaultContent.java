package cn.hollis.llm.mentor.know.engine.rag.model;

import cn.hollis.llm.mentor.know.engine.rag.constant.MetadataKeyConstant;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.DefaultContent;

import java.util.Map;
import java.util.Objects;

public class KnowEngineDefaultContent extends DefaultContent {
    public KnowEngineDefaultContent(TextSegment textSegment, Map<ContentMetadata, Object> metadata) {
        super(textSegment, metadata);
    }

    public KnowEngineDefaultContent(DefaultContent defaultContent) {
        super(defaultContent.textSegment(), defaultContent.metadata());
    }

    public KnowEngineDefaultContent(String text) {
        super(text);
    }

    public KnowEngineDefaultContent(TextSegment textSegment) {
        super(textSegment);
    }

    @Override
    public int hashCode() {
        return Objects.requireNonNull(this.textSegment().metadata().getString(MetadataKeyConstant.CHUNK_ID)).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return Objects.equals(this.textSegment().metadata().getString(MetadataKeyConstant.CHUNK_ID), ((KnowEngineDefaultContent) o).textSegment().metadata().getString(MetadataKeyConstant.CHUNK_ID));
    }

}
