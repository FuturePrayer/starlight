package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 用户账户实体。
 * <p>存储用户的基本信息，包括登录凭证、权限标识和个人偏好设置。</p>
 */
@Entity
@Table(name = "sl_user")
public class UserAccount extends BaseEntity {

    /** 用户名，全局唯一，用于登录和展示 */
    @Column(nullable = false, unique = true, length = 120)
    private String username;

    /** 邮箱地址，全局唯一，用于注册和通信 */
    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /** 密码哈希值（SHA-256 + 盐），不存储明文密码 */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    /** 是否为管理员标识 */
    @Column(nullable = false)
    private boolean adminFlag;

    /** 当前选中的主题 ID，默认为 win11-light */
    @Column(nullable = false, length = 120)
    private String themeId = "win11-light";

    /** TOTP 两步验证密钥（Base32 编码），为 null 表示未绑定 */
    @Column(length = 255)
    private String totpSecret;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isAdminFlag() {
        return adminFlag;
    }

    public void setAdminFlag(boolean adminFlag) {
        this.adminFlag = adminFlag;
    }

    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }
}

