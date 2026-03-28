package cn.suhoan.starlight.service.search;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索摘要与高亮工具。
 */
final class SearchSnippetUtil {

    private SearchSnippetUtil() {
    }

    /**
     * 从纯文本内容中提取关键词附近的片段，并用 &lt;mark&gt; 标签高亮关键词。
     *
     * @param plainText  纯文本内容
     * @param keyword    搜索关键词
     * @param contextLen 关键词前后各取的上下文字符数
     * @return 带有 &lt;mark&gt; 高亮标签的 HTML 片段
     */
    static String buildHighlightedSnippet(String plainText, String keyword, int contextLen) {
        if (plainText == null || keyword == null || keyword.isBlank()) {
            return "";
        }
        String lowerContent = plainText.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int idx = lowerContent.indexOf(lowerKeyword);

        String raw;
        if (idx < 0) {
            // 关键词不在纯文本中（可能在标题里），取开头作摘要
            raw = plainText.length() > contextLen
                    ? plainText.substring(0, contextLen) + "…"
                    : plainText;
            return escapeHtml(raw);
        }

        int start = Math.max(0, idx - contextLen / 2);
        int end = Math.min(plainText.length(), idx + keyword.length() + contextLen / 2);
        String snippet = plainText.substring(start, end);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < plainText.length() ? "…" : "";

        // 高亮所有匹配位置
        String escaped = escapeHtml(snippet);
        String escapedKeyword = escapeHtml(keyword);
        String highlighted = caseInsensitiveReplace(escaped, escapedKeyword, "<mark>", "</mark>");

        return prefix + highlighted + suffix;
    }

    /**
     * 对标题中的关键词添加 &lt;mark&gt; 高亮。
     */
    static String highlightTitle(String title, String keyword) {
        if (title == null || keyword == null || keyword.isBlank()) {
            return escapeHtml(title);
        }
        String escaped = escapeHtml(title);
        String escapedKeyword = escapeHtml(keyword);
        return caseInsensitiveReplace(escaped, escapedKeyword, "<mark>", "</mark>");
    }

    /**
     * 构建搜索结果 Map。
     */
    static Map<String, Object> toSearchResult(String id, String title, String plainText,
                                               Object updatedAt, String keyword) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("title", highlightTitle(title, keyword));
        map.put("snippet", buildHighlightedSnippet(plainText, keyword, 80));
        map.put("updatedAt", updatedAt);
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

    private static String escapeHtml(String value) {
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
}

