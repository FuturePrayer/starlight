package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 应用配置实体。
 * <p>以键值对形式存储系统级配置项，如注册开关、分享基础 URL、TOTP 开关等。</p>
 */
@Entity
@Table(name = "sl_app_setting")
public class AppSetting {

    /** 配置项键名，作为主键 */
    @Id
    @Column(nullable = false, length = 120)
    private String settingKey;

    /** 配置项值，以文本形式存储 */
    @Column(columnDefinition = "TEXT")
    private String settingValue;

    /** 配置项创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 配置项最后更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 生命周期回调 —— 持久化前设置时间戳 */
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /** JPA 生命周期回调 —— 更新前刷新时间戳 */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

