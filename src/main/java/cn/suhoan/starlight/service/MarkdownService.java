package cn.suhoan.starlight.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 内容分析服务。
 * <p>提供大纲提取和纯文本提取功能，前端负责 Markdown 到 HTML 的渲染。</p>
 *
 * @author suhoan
 */
@Service
public class MarkdownService {

    private final ObjectMapper objectMapper;

    public MarkdownService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    /** 将标题转换为 URL 锚点 ID（slug 格式） */
    private String slugify(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "section" : slug;
    }

    /** 大纲条目内部记录 */
    private record OutlineItem(int level, String title, String anchor) {
    }
}

