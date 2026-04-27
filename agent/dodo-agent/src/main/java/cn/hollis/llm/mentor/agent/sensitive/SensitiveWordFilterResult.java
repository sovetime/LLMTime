package cn.hollis.llm.mentor.agent.sensitive;

import java.util.List;

/**
 * 敏感词过滤结果
 *
 * @param originalText 原始文本
 * @param filteredText 过滤后文本
 * @param hit 是否命中敏感词
 * @param hitWords 命中的敏感词
 */
public record SensitiveWordFilterResult(
        String originalText,
        String filteredText,
        boolean hit,
        List<String> hitWords
) {
}
