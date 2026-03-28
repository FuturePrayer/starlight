package cn.suhoan.starlight.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarkdownService {

    private final ObjectMapper objectMapper;

    public MarkdownService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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

    private String applyInline(String value) {
        return value
                .replaceAll("`([^`]+)`", "<code>$1</code>")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>")
                .replaceAll("\\*([^*]+)\\*", "<em>$1</em>")
                .replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "<a href=\"$2\" target=\"_blank\" rel=\"noreferrer\">$1</a>");
    }

    private String slugify(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "section" : slug;
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record OutlineItem(int level, String title, String anchor) {
    }
}

