package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.NoteService;
import cn.suhoan.starlight.service.QrCodeService;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 笔记分享控制器。
 * <p>提供笔记分享链接的创建、查询、删除和公开访问接口。</p>
 *
 * @author suhoan
 */
@RestController
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final SessionAuthService sessionAuthService;
    private final NoteService noteService;
    private final ShareService shareService;
    private final QrCodeService qrCodeService;

    public ShareController(SessionAuthService sessionAuthService,
                           NoteService noteService,
                           ShareService shareService,
                           QrCodeService qrCodeService) {
        this.sessionAuthService = sessionAuthService;
        this.noteService = noteService;
        this.shareService = shareService;
        this.qrCodeService = qrCodeService;
    }

    /** 获取指定笔记的所有分享链接 */
    @GetMapping("/api/notes/{id}/shares")
    public ApiResponse<List<Map<String, Object>>> listShares(@PathVariable String id,
                                                             HttpServletRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        noteService.getOwnedNote(userAccount.getId(), id);
        return ApiResponse.ok(shareService.listShares(id, userAccount.getId(), getBaseUrl(request)));
    }

    /** 创建笔记分享链接 */
    @PostMapping("/api/notes/{id}/shares")
    public ApiResponse<Map<String, Object>> createShare(@PathVariable String id,
                                                        @RequestBody ShareRequest shareRequest,
                                                        HttpServletRequest request) {
        log.info("创建分享链接: noteId={}, accessType={}", id, shareRequest.accessType());
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.getOwnedNote(userAccount.getId(), id);
        return ApiResponse.ok(shareService.createShare(
                userAccount,
                note,
                shareRequest.accessType(),
                shareRequest.password(),
                shareRequest.expiresAt(),
                shareRequest.timezoneOffset(),
                getBaseUrl(request)
        ));
    }

    /** 删除分享链接 */
    @DeleteMapping("/api/notes/{id}/shares/{shareId}")
    public ApiResponse<Void> deleteShare(@PathVariable String id,
                                         @PathVariable String shareId) {
        UserAccount userAccount = sessionAuthService.requireUser();
        noteService.getOwnedNote(userAccount.getId(), id);
        shareService.deleteShare(shareId, userAccount.getId());
        return ApiResponse.ok(null);
    }

    /**
     * 通过分享 token 公开访问笔记内容。
     * <p>无需登录即可访问。</p>
     */
    @GetMapping("/api/shares/{token}")
    public ApiResponse<Map<String, Object>> openShare(@PathVariable String token,
                                                      @RequestParam(required = false) String password) {
        return ApiResponse.ok(shareService.openShare(token, password));
    }

    /** 获取分享链接的二维码 */
    @GetMapping("/api/shares/{token}/qrcode")
    public ApiResponse<Map<String, Object>> shareQrCode(@PathVariable String token,
                                                         HttpServletRequest request) {
        String url = shareService.getShareUrl(token, getBaseUrl(request));
        String qrDataUrl = qrCodeService.generateDataUrl(url, 280);
        return ApiResponse.ok(Map.of("qrDataUrl", qrDataUrl, "url", url));
    }

    /** 从 HTTP 请求中提取基础 URL */
    private String getBaseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() +
                ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort()) +
                request.getContextPath();
    }

    /** 创建分享请求体 */
    public record ShareRequest(String accessType, String password, LocalDateTime expiresAt, Integer timezoneOffset) {
    }
}

