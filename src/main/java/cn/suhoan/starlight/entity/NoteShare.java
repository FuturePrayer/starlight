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
 * 笔记分享实体。
 * <p>记录笔记的分享信息，包括分享 token、访问方式（公开/密码保护）和过期时间。</p>
 */
@Entity
@Table(name = "sl_note_share")
public class NoteShare extends BaseEntity {

    /** 被分享的笔记，懒加载 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    /** 分享创建者（笔记所有者），懒加载 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    /** 分享唯一标识 token，用于生成分享链接 */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** 访问方式：PUBLIC（公开）或 PASSWORD（密码保护） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareAccessType accessType;

    /** 密码保护的哈希值，仅当 accessType 为 PASSWORD 时有值 */
    @Column(length = 255)
    private String passwordHash;

    /** 过期时间，为 null 表示永不过期 */
    private LocalDateTime expiresAt;

    /**
     * 判断分享链接是否已过期。
     *
     * @return 如果已设置过期时间且当前时间已超过过期时间则返回 true
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public ShareAccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(ShareAccessType accessType) {
        this.accessType = accessType;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}

