package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 笔记实体。
 * <p>存储用户笔记的核心数据，包括 Markdown 原文、渲染后的 HTML、大纲 JSON 和纯文本索引。</p>
 *
 * @author suhoan
 */
@Entity
@Table(name = "sl_note")
public class Note extends BaseEntity {

    /** 笔记所属用户，懒加载 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    /** 笔记所属分类，可为 null 表示未分类，懒加载 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** 笔记标题 */
    @Column(nullable = false, length = 255)
    private String title;

    /** Markdown 原始内容 */
    @Lob
    @Column(nullable = false, length = 65535)
    private String markdownContent;

    /** 由 Markdown 渲染后的 HTML 内容 */
    @Lob
    @Column(nullable = false, length = 65535)
    private String renderedHtml;

    /** 笔记大纲结构（JSON 格式），用于目录导航 */
    @Lob
    @Column(nullable = false, length = 65535)
    private String outlineJson;

    /** 纯文本内容，去除 Markdown 语法，用于全文搜索索引 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String plainText = "";

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public void setMarkdownContent(String markdownContent) {
        this.markdownContent = markdownContent;
    }

    public String getRenderedHtml() {
        return renderedHtml;
    }

    public void setRenderedHtml(String renderedHtml) {
        this.renderedHtml = renderedHtml;
    }

    public String getOutlineJson() {
        return outlineJson;
    }

    public void setOutlineJson(String outlineJson) {
        this.outlineJson = outlineJson;
    }

    public String getPlainText() {
        return plainText;
    }

    public void setPlainText(String plainText) {
        this.plainText = plainText;
    }
}

