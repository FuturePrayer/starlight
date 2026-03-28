package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 所有实体的公共基类。
 * <p>提供 UUID 主键、创建时间和更新时间的自动填充功能。
 * 子类只需关注自身的业务字段即可。</p>
 */
@MappedSuperclass
public abstract class BaseEntity {

    /** 主键，使用 UUID 自动生成 */
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    /** 记录创建时间，仅在对首次持久化时设置 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 记录最后更新时间，每次持久化时自动刷新 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA 生命周期回调 —— 持久化前自动填充 id、createdAt、updatedAt。
     */
    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /**
     * JPA 生命周期回调 —— 更新前自动刷新 updatedAt。
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

