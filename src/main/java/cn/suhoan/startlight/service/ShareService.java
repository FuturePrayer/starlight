package cn.suhoan.startlight.service;

import cn.suhoan.startlight.entity.Note;
import cn.suhoan.startlight.entity.NoteShare;
import cn.suhoan.startlight.entity.ShareAccessType;
import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.repository.NoteShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional
public class ShareService {

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

    public Map<String, Object> createShare(UserAccount owner,
                                           Note note,
                                           String accessType,
                                           String password,
                                           LocalDateTime expiresAt,
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
        share.setExpiresAt(expiresAt);
        noteShareRepository.save(share);
        return toShareItem(share, requestBaseUrl);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listShares(String noteId, String ownerId, String requestBaseUrl) {
        return noteShareRepository.findByNoteIdAndOwnerIdOrderByCreatedAtDesc(noteId, ownerId)
                .stream()
                .map(share -> toShareItem(share, requestBaseUrl))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> openShare(String token, String password) {
        NoteShare share = noteShareRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "分享链接不存在"));
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
        shareMap.put("expiresAt", share.getExpiresAt());
        data.put("share", shareMap);
        return data;
    }

    private String generateToken() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<String, Object> toShareItem(NoteShare share, String requestBaseUrl) {
        String baseUrl = settingsService.getShareBaseUrl();
        String prefix = baseUrl.isBlank() ? requestBaseUrl : baseUrl;
        Map<String, Object> map = new HashMap<>();
        map.put("id", share.getId());
        map.put("token", share.getToken());
        map.put("accessType", share.getAccessType().name());
        map.put("expiresAt", share.getExpiresAt());
        map.put("url", prefix + "/s/" + share.getToken());
        map.put("createdAt", share.getCreatedAt());
        return map;
    }
}

