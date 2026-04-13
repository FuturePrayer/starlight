package cn.suhoan.starlight.service.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索摘要与高亮工具。
 * <p>支持单关键词和多关键词的高亮与摘要提取。</p>
 *
 * @author suhoan
 */
final class SearchSnippetUtil {

    private SearchSnippetUtil() {
    }

    /**
     * 从纯文本内容中提取关键词附近的片段，并用 &lt;mark&gt; 标签高亮关键词。
     * 兼容单关键词（旧接口）。
     */
    static String buildHighlightedSnippet(String plainText, String keyword, int contextLen) {
        return buildHighlightedSnippet(plainText, List.of(keyword), contextLen);
    }

    /**
     * 从纯文本内容中提取多个关键词附近的片段，并用 &lt;mark&gt; 标签高亮。
     *
     * @param plainText  纯文本内容
     * @param keywords   搜索关键词列表
     * @param contextLen 关键词前后各取的上下文字符数
     * @return 带有 &lt;mark&gt; 高亮标签的 HTML 片段
     */
    static String buildHighlightedSnippet(String plainText, List<String> keywords, int contextLen) {
        if (plainText == null || keywords == null || keywords.isEmpty()) {
            return "";
        }
        // 过滤空白关键词
        List<String> validKeywords = keywords.stream().filter(k -> k != null && !k.isBlank()).toList();
        if (validKeywords.isEmpty()) {
            return "";
        }

        String lowerContent = plainText.toLowerCase();

        // 找到第一个命中的关键词位置
        int bestIdx = -1;
        for (String kw : validKeywords) {
            int idx = lowerContent.indexOf(kw.toLowerCase());
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
            }
        }

        String raw;
        if (bestIdx < 0) {
            // 关键词不在纯文本中（可能在标题里），取开头作摘要
            raw = plainText.length() > contextLen
                    ? plainText.substring(0, contextLen) + "…"
                    : plainText;
            return escapeHtml(raw);
        }

        int start = Math.max(0, bestIdx - contextLen / 2);
        int end = Math.min(plainText.length(), bestIdx + contextLen);
        String snippet = plainText.substring(start, end);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < plainText.length() ? "…" : "";

        // 高亮所有匹配的关键词
        String escaped = escapeHtml(snippet);
        for (String kw : validKeywords) {
            String escapedKeyword = escapeHtml(kw);
            escaped = caseInsensitiveReplace(escaped, escapedKeyword, "<mark>", "</mark>");
        }

        return prefix + escaped + suffix;
    }

    /**
     * 对标题中的关键词添加 &lt;mark&gt; 高亮。兼容单关键词（旧接口）。
     */
    static String highlightTitle(String title, String keyword) {
        return highlightTitle(title, List.of(keyword));
    }

    /**
     * 对标题中的多个关键词添加 &lt;mark&gt; 高亮。
     */
    static String highlightTitle(String title, List<String> keywords) {
        if (title == null || keywords == null || keywords.isEmpty()) {
            return escapeHtml(title);
        }
        String escaped = escapeHtml(title);
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            String escapedKeyword = escapeHtml(kw);
            escaped = caseInsensitiveReplace(escaped, escapedKeyword, "<mark>", "</mark>");
        }
        return escaped;
    }

    /**
     * 构建搜索结果 Map（单关键词兼容接口）。
     */
    static Map<String, Object> toSearchResult(String id, String title, String plainText,
                                               Object updatedAt, String keyword) {
        return toSearchResult(id, title, plainText, updatedAt, List.of(keyword));
    }

    /**
     * 构建搜索结果 Map（多关键词版本）。
     */
    static Map<String, Object> toSearchResult(String id, String title, String plainText,
                                               Object updatedAt, List<String> keywords) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("title", highlightTitle(title, keywords));
        map.put("snippet", buildHighlightedSnippet(plainText, keywords, 80));
        map.put("updatedAt", updatedAt);
        return map;
    }

    /**
     * 构建带评分的搜索结果 Map（多关键词版本）。
     */
    static Map<String, Object> toScoredSearchResult(String id, String title, String plainText,
                                                     Object updatedAt, List<String> keywords, int score) {
        Map<String, Object> map = toSearchResult(id, title, plainText, updatedAt, keywords);
        map.put("score", score);
        return map;
    }

    /**
     * 大小写不敏感地将文本中所有匹配的关键词用前后标签包裹。
     */
    private static String caseInsensitiveReplace(String text, String keyword, String tagOpen, String tagClose) {
        if (keyword.isEmpty()) return text;
        Pattern pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, tagOpen + Matcher.quoteReplacement(matcher.group()) + tagClose);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 转义 SQL LIKE 通配符。
     */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 将搜索字符串按空格拆分为多个关键词，去除空白项并转为小写。
     *
     * @param query 原始搜索字符串
     * @return 关键词列表（小写）
     */
    static List<String> splitKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : query.trim().split("\\s+")) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
