package cn.suhoan.startlight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "sl_user_credential")
public class UserCredential extends BaseEntity {

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String credentialId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKeyCose;

    @Column(nullable = false)
    private long signatureCount;

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

