package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Git 导入映射记录。
 * <p>用于追踪某个 Git 导入源创建出的笔记/分类，方便后续重导入时做硬删除覆盖。</p>
 */
@Entity
@Table(name = "sl_git_import_binding")
public class GitImportBinding extends BaseEntity {

    /** 关联的导入源。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private GitNoteSource source;

    /** 绑定类型：NOTE / CATEGORY。 */
    @Column(nullable = false, length = 20)
    private String bindingType;

    /** 被绑定的实体 ID。 */
    @Column(nullable = false, length = 36)
    private String entityId;

    /** 相对导入根目录的路径。 */
    @Column(nullable = false, length = 500)
    private String relativePath;

    /** 文件内容哈希，用于后续扩展跳过完全相同文件。 */
    @Column(length = 80)
    private String contentHash;

    public GitNoteSource getSource() {
        return source;
    }

    public void setSource(GitNoteSource source) {
        this.source = source;
    }

    public String getBindingType() {
        return bindingType;
    }

    public void setBindingType(String bindingType) {
        this.bindingType = bindingType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}

