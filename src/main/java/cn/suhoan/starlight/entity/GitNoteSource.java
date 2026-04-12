package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Git 笔记导入源。
 * <p>保存仓库地址、分支、导入目录、目标分类与自动同步配置，
 * 供用户后续手动重导入或定时自动同步使用。</p>
 */
@Entity
@Table(name = "sl_git_note_source")
public class GitNoteSource extends BaseEntity {

    /** 导入源所属用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    /** 仓库地址，可包含认证信息。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String repositoryUrl;

    /** 仓库名快照，便于前端展示。 */
    @Column(nullable = false, length = 200)
    private String repositoryName;

    /** 分支名。 */
    @Column(nullable = false, length = 200)
    private String branchName;

    /** 仓库内导入起始目录，相对仓库根目录；空字符串表示根目录。 */
    @Column(nullable = false, length = 500)
    private String sourcePath = "";

    /** 目标分类 ID。 */
    @Column(length = 36)
    private String targetCategoryId;

    /** 目标分类名称快照，便于目标分类被删除后仍能提示用户。 */
    @Column(nullable = false, length = 200)
    private String targetCategoryName;

    /** 目标分类是否由该导入源自动创建。 */
    @Column(nullable = false)
    private boolean targetCategoryCreatedBySource;

    /** 是否开启自动同步。 */
    @Column(nullable = false)
    private boolean autoSyncEnabled;

    /** 自动同步调度类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GitScheduleType scheduleType = GitScheduleType.MANUAL_ONLY;

    /** 用户选择的时区 ID。 */
    @Column(length = 80)
    private String scheduleTimezone;

    /** 每日/每周/每小时同步时使用的小时。 */
    private Integer scheduleHour;

    /** 每日/每周/每小时同步时使用的分钟。 */
    private Integer scheduleMinute;

    /** 每周同步时使用的星期（1-7，对应周一到周日）。 */
    private Integer scheduleDayOfWeek;

    /** 最近一次成功同步的提交 ID。 */
    @Column(length = 80)
    private String lastSyncedCommitId;

    /** 最近一次同步完成时间。 */
    private LocalDateTime lastSyncAt;

    /** 最近一次同步是否成功。 */
    private Boolean lastSyncSuccess;

    /** 最近一次同步结果摘要。 */
    @Column(columnDefinition = "TEXT")
    private String lastSyncMessage;

    /** 最近一次按调度执行的时间，用于避免重复触发。 */
    private LocalDateTime lastScheduledRunAt;

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetCategoryId() {
        return targetCategoryId;
    }

    public void setTargetCategoryId(String targetCategoryId) {
        this.targetCategoryId = targetCategoryId;
    }

    public String getTargetCategoryName() {
        return targetCategoryName;
    }

    public void setTargetCategoryName(String targetCategoryName) {
        this.targetCategoryName = targetCategoryName;
    }

    public boolean isTargetCategoryCreatedBySource() {
        return targetCategoryCreatedBySource;
    }

    public void setTargetCategoryCreatedBySource(boolean targetCategoryCreatedBySource) {
        this.targetCategoryCreatedBySource = targetCategoryCreatedBySource;
    }

    public boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    public void setAutoSyncEnabled(boolean autoSyncEnabled) {
        this.autoSyncEnabled = autoSyncEnabled;
    }

    public GitScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(GitScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getScheduleTimezone() {
        return scheduleTimezone;
    }

    public void setScheduleTimezone(String scheduleTimezone) {
        this.scheduleTimezone = scheduleTimezone;
    }

    public Integer getScheduleHour() {
        return scheduleHour;
    }

    public void setScheduleHour(Integer scheduleHour) {
        this.scheduleHour = scheduleHour;
    }

    public Integer getScheduleMinute() {
        return scheduleMinute;
    }

    public void setScheduleMinute(Integer scheduleMinute) {
        this.scheduleMinute = scheduleMinute;
    }

    public Integer getScheduleDayOfWeek() {
        return scheduleDayOfWeek;
    }

    public void setScheduleDayOfWeek(Integer scheduleDayOfWeek) {
        this.scheduleDayOfWeek = scheduleDayOfWeek;
    }

    public String getLastSyncedCommitId() {
        return lastSyncedCommitId;
    }

    public void setLastSyncedCommitId(String lastSyncedCommitId) {
        this.lastSyncedCommitId = lastSyncedCommitId;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public Boolean getLastSyncSuccess() {
        return lastSyncSuccess;
    }

    public void setLastSyncSuccess(Boolean lastSyncSuccess) {
        this.lastSyncSuccess = lastSyncSuccess;
    }

    public String getLastSyncMessage() {
        return lastSyncMessage;
    }

    public void setLastSyncMessage(String lastSyncMessage) {
        this.lastSyncMessage = lastSyncMessage;
    }

    public LocalDateTime getLastScheduledRunAt() {
        return lastScheduledRunAt;
    }

    public void setLastScheduledRunAt(LocalDateTime lastScheduledRunAt) {
        this.lastScheduledRunAt = lastScheduledRunAt;
    }
}

