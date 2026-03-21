package cn.suhoan.startlight.controller;

import cn.suhoan.startlight.dto.ApiResponse;
import cn.suhoan.startlight.entity.Note;
import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.service.NoteService;
import cn.suhoan.startlight.service.SessionAuthService;
import cn.suhoan.startlight.service.ShareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class ShareController {

    private final SessionAuthService sessionAuthService;
    private final NoteService noteService;
    private final ShareService shareService;

    public ShareController(SessionAuthService sessionAuthService,
                           NoteService noteService,
                           ShareService shareService) {
        this.sessionAuthService = sessionAuthService;
        this.noteService = noteService;
        this.shareService = shareService;
    }

    @GetMapping("/api/notes/{id}/shares")
    public ApiResponse<List<Map<String, Object>>> listShares(@PathVariable String id,
                                                             jakarta.servlet.http.HttpServletRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        noteService.getOwnedNote(userAccount.getId(), id);
        return ApiResponse.ok(shareService.listShares(id, userAccount.getId(), getBaseUrl(request)));
    }

    @PostMapping("/api/notes/{id}/shares")
    public ApiResponse<Map<String, Object>> createShare(@PathVariable String id,
                                                        @RequestBody ShareRequest shareRequest,
                                                        jakarta.servlet.http.HttpServletRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.getOwnedNote(userAccount.getId(), id);
        return ApiResponse.ok(shareService.createShare(
                userAccount,
                note,
                shareRequest.accessType(),
                shareRequest.password(),
                shareRequest.expiresAt(),
                getBaseUrl(request)
        ));
    }

    @GetMapping("/api/shares/{token}")
    public ApiResponse<Map<String, Object>> openShare(@PathVariable String token,
                                                      @RequestParam(required = false) String password) {
        return ApiResponse.ok(shareService.openShare(token, password));
    }

    private String getBaseUrl(jakarta.servlet.http.HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() +
                ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort()) +
                request.getContextPath();
    }

    public record ShareRequest(String accessType, String password, LocalDateTime expiresAt) {
    }
}

