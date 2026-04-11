package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 通用 API Key 实体。
 * <p>当前主要用于 MCP Server 鉴权，后续也可扩展到其他集成能力。</p>
 */
@Entity
@Table(name = "sl_api_key")
public class ApiKey extends BaseEntity {

    /** API Key 所属用户。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    /** API Key 名称/备注。 */
    @Column(nullable = false, length = 120)
    private String name;

    /** 用于界面展示的密钥前缀。 */
    @Column(nullable = false, length = 32)
    private String keyPrefix;

    /** API Key 的 SHA-256 哈希值。 */
    @Column(nullable = false, unique = true, length = 64)
    private String secretHash;

    /** 是否启用。 */
    @Column(nullable = false)
    private boolean enabledFlag = true;

    /** 是否只读。 */
    @Column(nullable = false)
    private boolean readOnlyFlag = true;

    /** 是否允许访问全部分类。 */
    @Column(nullable = false)
    private boolean allowAllCategoriesFlag = true;

    /** 最近一次成功使用时间。 */
    private LocalDateTime lastUsedAt;

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public boolean isEnabledFlag() {
        return enabledFlag;
    }

    public void setEnabledFlag(boolean enabledFlag) {
        this.enabledFlag = enabledFlag;
    }

    public boolean isReadOnlyFlag() {
        return readOnlyFlag;
    }

    public void setReadOnlyFlag(boolean readOnlyFlag) {
        this.readOnlyFlag = readOnlyFlag;
    }

    public boolean isAllowAllCategoriesFlag() {
        return allowAllCategoriesFlag;
    }

    public void setAllowAllCategoriesFlag(boolean allowAllCategoriesFlag) {
        this.allowAllCategoriesFlag = allowAllCategoriesFlag;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}

