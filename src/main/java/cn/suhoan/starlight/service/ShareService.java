package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.NoteShare;
import cn.suhoan.starlight.entity.ShareAccessType;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.NoteShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 笔记分享服务。
 * <p>处理笔记的分享创建、列表查询、删除和公开访问等业务逻辑。</p>
 *
 * @author suhoan
 */
@Service
@Transactional
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private final NoteShareRepository noteShareRepository;
    private final PasswordService passwordService;
    private final SettingsService settingsService;
    private final ThemeService themeService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShareService(NoteShareRepository noteShareRepository,
                        PasswordService passwordService,
                        SettingsService settingsService,
                        ThemeService themeService) {
        this.noteShareRepository = noteShareRepository;
        this.passwordService = passwordService;
        this.settingsService = settingsService;
        this.themeService = themeService;
    }

    /**
     * 创建笔记分享链接。
     *
     * @param owner          笔记所有者
     * @param note           被分享的笔记
     * @param accessType     访问方式 ("PUBLIC" 或 "PASSWORD")
     * @param password       密码（当 accessType 为 PASSWORD 时必填）
     * @param expiresAt      过期时间（用户浏览器本地时间）
     * @param timezoneOffset 浏览器时区偏移（分钟）
     * @param requestBaseUrl 请求的基础 URL
     * @return 分享信息 Map
     */
    public Map<String, Object> createShare(UserAccount owner,
                                           Note note,
                                           String accessType,
                                           String password,
                                           LocalDateTime expiresAt,
                                           Integer timezoneOffset,
                                           String requestBaseUrl) {
        ShareAccessType type = "PASSWORD".equalsIgnoreCase(accessType) ? ShareAccessType.PASSWORD : ShareAccessType.PUBLIC;
        NoteShare share = new NoteShare();
        share.setOwner(owner);
        share.setNote(note);
        share.setToken(generateToken());
        share.setAccessType(type);
        if (type == ShareAccessType.PASSWORD) {
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("私密分享必须设置密码");
            }
            share.setPasswordHash(passwordService.hash(password));
        }
        share.setExpiresAt(toServerLocalTime(expiresAt, timezoneOffset));
        noteShareRepository.save(share);
        log.info("分享链接已创建: shareId={}, noteId={}, accessType={}, ownerId={}", share.getId(), note.getId(), type, owner.getId());
        return toShareItem(share, requestBaseUrl);
    }

    /**
     * 获取指定笔记的所有分享链接列表。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listShares(String noteId, String ownerId, String requestBaseUrl) {
        return noteShareRepository.findByNoteIdAndOwnerIdOrderByCreatedAtDesc(noteId, ownerId)
                .stream()
                .map(share -> toShareItem(share, requestBaseUrl))
                .toList();
    }

    /**
     * 删除分享链接。
     *
     * @param shareId 分享 ID
     * @param ownerId 当前用户 ID（必须是分享创建者）
     */
    public void deleteShare(String shareId, String ownerId) {
        NoteShare share = noteShareRepository.findById(shareId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "分享链接不存在"));
        if (!share.getOwner().getId().equals(ownerId)) {
            log.warn("无权删除分享链接: shareId={}, userId={}", shareId, ownerId);
            throw new ResponseStatusException(FORBIDDEN, "无权删除该分享链接");
        }
        log.info("分享链接已删除: shareId={}, ownerId={}", shareId, ownerId);
        noteShareRepository.delete(share);
    }

    @Transactional(readOnly = true)
    public String getShareUrl(String token, String requestBaseUrl) {
        noteShareRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "分享链接不存在"));
        String baseUrl = settingsService.getShareBaseUrl();
        String prefix = baseUrl.isBlank() ? requestBaseUrl : baseUrl;
        return prefix + "/s/" + token;
    }

    /**
     * 通过 token 打开分享链接，验证密码和有效期后返回笔记内容。
     *
     * @param token    分享 token
     * @param password 访问密码（公开分享可为空）
     * @return 包含笔记内容、所有者信息和分享信息的 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> openShare(String token, String password) {
        NoteShare share = noteShareRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "分享链接不存在"));
        if (share.getNote().getDeletedAt() != null) {
            log.warn("分享链接访问失败，笔记已进入回收站: shareId={}, noteId={}", share.getId(), share.getNote().getId());
            throw new ResponseStatusException(FORBIDDEN, "该笔记已移入回收站，分享暂不可用");
        }
        if (share.isExpired()) {
            throw new ResponseStatusException(FORBIDDEN, "分享链接已过期");
        }
        if (share.getAccessType() == ShareAccessType.PASSWORD
                && !passwordService.matches(password == null ? "" : password, share.getPasswordHash())) {
            throw new ResponseStatusException(FORBIDDEN, "分享密码错误");
        }
        Map<String, Object> theme = themeService.resolveTheme(share.getOwner().getThemeId());
        Map<String, Object> data = new HashMap<>();
        data.put("note", Map.of(
                "id", share.getNote().getId(),
                "title", share.getNote().getTitle(),
                "markdownContent", share.getNote().getMarkdownContent(),
                "renderedHtml", share.getNote().getRenderedHtml(),
                "outlineJson", share.getNote().getOutlineJson(),
                "updatedAt", share.getNote().getUpdatedAt()
        ));
        data.put("owner", Map.of(
                "username", share.getOwner().getUsername(),
                "theme", theme
        ));
        Map<String, Object> shareMap = new HashMap<>();
        shareMap.put("token", share.getToken());
        shareMap.put("accessType", share.getAccessType().name());
        shareMap.put("expiresAt", formatExpiresAt(share.getExpiresAt()));
        data.put("share", shareMap);
        return data;
    }

    /** 生成安全的随机分享 token（16字节 Base64Url 编码） */
    private String generateToken() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Convert user's local datetime to server's local datetime using the browser timezoneOffset.
     * JS getTimezoneOffset() returns minutes: positive for west of UTC, negative for east.
     * e.g. UTC+8 → -480
     */
    private LocalDateTime toServerLocalTime(LocalDateTime userLocal, Integer timezoneOffset) {
        if (userLocal == null) return null;
        if (timezoneOffset == null) return userLocal;
        ZoneOffset userOffset = ZoneOffset.ofTotalSeconds(-timezoneOffset * 60);
        return userLocal.atOffset(userOffset)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Format server-local expiresAt with the server's timezone offset so the frontend
     * can correctly convert to the user's local time via new Date("...+08:00").
     */
    private String formatExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) return null;
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(expiresAt);
        return expiresAt.atOffset(offset).toString();
    }

    /** 将分享实体转换为前端展示用的 Map */
    private Map<String, Object> toShareItem(NoteShare share, String requestBaseUrl) {
        String baseUrl = settingsService.getShareBaseUrl();
        String prefix = baseUrl.isBlank() ? requestBaseUrl : baseUrl;
        Map<String, Object> map = new HashMap<>();
        map.put("id", share.getId());
        map.put("token", share.getToken());
        map.put("accessType", share.getAccessType().name());
        map.put("expiresAt", formatExpiresAt(share.getExpiresAt()));
        map.put("url", prefix + "/s/" + share.getToken());
        map.put("createdAt", share.getCreatedAt());
        return map;
    }
}

