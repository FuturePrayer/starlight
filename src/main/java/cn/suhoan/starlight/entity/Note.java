package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sl_note")
public class Note extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(nullable = false, length = 65535)
    private String markdownContent;

    @Lob
    @Column(nullable = false, length = 65535)
    private String renderedHtml;

    @Lob
    @Column(nullable = false, length = 65535)
    private String outlineJson;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
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

