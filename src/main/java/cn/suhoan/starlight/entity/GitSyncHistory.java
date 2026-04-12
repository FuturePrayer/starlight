package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Git 同步历史记录。
 * <p>只保留最新 5 条，用于向用户反馈最近同步是否成功以及失败原因。</p>
 */
@Entity
@Table(name = "sl_git_sync_history")
public class GitSyncHistory extends BaseEntity {

    /** 关联的导入源。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private GitNoteSource source;

    /** 触发方式：MANUAL / AUTO / INITIAL_IMPORT。 */
    @Column(nullable = false, length = 20)
    private String triggerType;

    /** 是否成功。 */
    @Column(nullable = false)
    private boolean successFlag;

    /** 本次同步对应的提交 ID。 */
    @Column(length = 80)
    private String commitId;

    /** 同步结果摘要。 */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** 开始时间。 */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** 结束时间。 */
    private LocalDateTime finishedAt;

    public GitNoteSource getSource() {
        return source;
    }

    public void setSource(GitNoteSource source) {
        this.source = source;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public boolean isSuccessFlag() {
        return successFlag;
    }

    public void setSuccessFlag(boolean successFlag) {
        this.successFlag = successFlag;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}

