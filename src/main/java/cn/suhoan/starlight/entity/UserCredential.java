package cn.suhoan.starlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * WebAuthn 通行密钥凭证实体。
 * <p>存储用户注册的通行密钥（Passkey）信息，用于无密码登录认证。</p>
 *
 * @author suhoan
 */
@Entity
@Table(name = "sl_user_credential")
public class UserCredential extends BaseEntity {

    /** 关联的用户 ID */
    @Column(nullable = false, length = 36)
    private String userId;

    /** WebAuthn 凭证 ID（Base64Url 编码） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String credentialId;

    /** COSE 格式的公钥（Base64Url 编码） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyCose;

    /** 签名计数器，用于检测克隆攻击 */
    @Column(nullable = false)
    private long signatureCount;

    /** 通行密钥的别名，如 "iPhone 15"、"YubiKey" 等 */
    @Column(length = 100)
    private String nickname;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getPublicKeyCose() {
        return publicKeyCose;
    }

    public void setPublicKeyCose(String publicKeyCose) {
        this.publicKeyCose = publicKeyCose;
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public void setSignatureCount(long signatureCount) {
        this.signatureCount = signatureCount;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}

