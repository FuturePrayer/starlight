package cn.suhoan.starlight.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 渲染服务。
 * <p>提供 Markdown 转 HTML、大纲提取和纯文本提取功能。
 * 使用自实现的轻量级 Markdown 解析器，支持标题、列表、代码块和行内格式。</p>
 *
 * @author suhoan
 */
@Service
public class MarkdownService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarkdownService.class);

    private final ObjectMapper objectMapper;

    public MarkdownService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Markdown 文本渲染为 HTML。
     * <p>支持的功能包括：标题（h1-h6）、无序列表、代码块、行内代码、粗体、斜体和链接。
     * 所有内容在渲染前会进行 HTML 转义以防止 XSS 攻击。</p>
     *
     * @param markdown Markdown 原始文本
     * @return 渲染后的 HTML 字符串
     */
    public String renderToHtml(String markdown) {
        String safe = escape(markdown == null ? "" : markdown);
        String[] lines = safe.split("\\R", -1);
        StringBuilder html = new StringBuilder();
        boolean inList = false;
        boolean inCode = false;
        for (String rawLine : lines) {
            String line = rawLine;
            if (line.startsWith("```")) {
                if (inCode) {
                    html.append("</code></pre>");
                } else {
                    if (inList) {
                        html.append("</ul>");
                        inList = false;
                    }
                    html.append("<pre><code>");
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                html.append(line).append("\n");
                continue;
            }
            if (line.matches("#{1,6}\\s+.*")) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                String title = line.substring(level).trim();
                String slug = slugify(title);
                html.append("<h").append(level).append(" id=\"").append(slug).append("\">")
                        .append(applyInline(title))
                        .append("</h").append(level).append(">");
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(applyInline(line.substring(2).trim())).append("</li>");
                continue;
            }
            if (inList) {
                html.append("</ul>");
                inList = false;
            }
            if (line.isBlank()) {
                html.append("<div class=\"md-space\"></div>");
            } else {
                html.append("<p>").append(applyInline(line)).append("</p>");
            }
        }
        if (inList) {
            html.append("</ul>");
        }
        if (inCode) {
            html.append("</code></pre>");
        }
        return html.toString();
    }

    /**
     * 从 Markdown 内容中提取标题大纲结构，返回 JSON 格式。
     * <p>仅提取 h2-h6 级别的标题，用于前端目录导航。</p>
     *
     * @param markdown Markdown 原始文本
     * @return 大纲 JSON 字符串
     */
    public String buildOutlineJson(String markdown) {
        List<OutlineItem> items = new ArrayList<>();
        String content = markdown == null ? "" : markdown;
        for (String line : content.split("\\R")) {
            if (line.matches("#{2,6}\\s+.*")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                String title = line.substring(level).trim();
                items.add(new OutlineItem(level, title, slugify(title)));
            }
        }
        return objectMapper.writeValueAsString(items);
    }

    /**
     * 将 Markdown 内容转为纯文本（去除语法符号），用于全文搜索索引。
     */
    public String stripToPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        StringBuilder plain = new StringBuilder();
        boolean inCode = false;
        for (String line : markdown.split("\\R", -1)) {
            if (line.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                plain.append(line).append(' ');
                continue;
            }
            // Strip heading markers
            String stripped = line.replaceAll("^#{1,6}\\s+", "");
            // Strip bold/italic markers
            stripped = stripped.replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1");
            // Strip inline code backticks
            stripped = stripped.replaceAll("`([^`]+)`", "$1");
            // Strip link syntax [text](url) → text
            stripped = stripped.replaceAll("\\[([^\\]]+)]\\([^)]+\\)", "$1");
            // Strip image syntax ![alt](url) → alt
            stripped = stripped.replaceAll("!\\[([^\\]]*)]\\([^)]+\\)", "$1");
            // Strip list markers
            stripped = stripped.replaceAll("^\\s*[-*+]\\s+", "");
            stripped = stripped.replaceAll("^\\s*\\d+\\.\\s+", "");
            // Strip blockquote markers
            stripped = stripped.replaceAll("^>+\\s*", "");
            // Strip horizontal rules
            if (stripped.matches("^[-*_]{3,}\\s*$")) {
                continue;
            }
            if (!stripped.isBlank()) {
                plain.append(stripped.strip()).append(' ');
            }
        }
        return plain.toString().strip();
    }

    /** 处理行内 Markdown 语法：行内代码、粗体、斜体和链接 */
    private String applyInline(String value) {
        return value
                .replaceAll("`([^`]+)`", "<code>$1</code>")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>")
                .replaceAll("\\*([^*]+)\\*", "<em>$1</em>")
                .replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "<a href=\"$2\" target=\"_blank\" rel=\"noreferrer\">$1</a>");
    }

    /** 将标题转换为 URL 锚点 ID（slug 格式） */
    private String slugify(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "section" : slug;
    }

    /** HTML 转义，防止 XSS */
    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** 大纲条目内部记录 */
    private record OutlineItem(int level, String title, String anchor) {
    }
}

