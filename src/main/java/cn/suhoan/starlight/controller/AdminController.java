package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员控制器。
 * <p>提供系统级设置的管理接口，仅管理员可访问。</p>
 *
 * @author suhoan
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SessionAuthService sessionAuthService;
    private final SettingsService settingsService;

    public AdminController(SessionAuthService sessionAuthService, SettingsService settingsService) {
        this.sessionAuthService = sessionAuthService;
        this.settingsService = settingsService;
    }

    /** 获取系统设置（需管理员权限） */
    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> getSettings() {
        sessionAuthService.requireAdmin();
        Map<String, Object> data = new HashMap<>();
        data.put("registrationEnabled", settingsService.isRegistrationEnabled());
        data.put("shareBaseUrl", settingsService.getShareBaseUrl());
        data.put("totpEnabled", settingsService.isTotpEnabled());
        data.put("passkeyEnabled", settingsService.isPasskeyEnabled());
        data.put("siteUrlHttps", settingsService.isSiteUrlHttps());
        data.put("mcpEnabled", settingsService.isMcpServerEnabled());
        data.put("gitImportEnabled", settingsService.isGitImportEnabled());
        data.put("gitImportMaxConcurrent", settingsService.getGitImportMaxConcurrent());
        return ApiResponse.ok(data);
    }

    /**
     * 保存系统设置（需管理员权限）。
     * <p>注意：设置分享 URL 可能会自动禁用通行密钥（非 HTTPS 时）。</p>
     */
    @PostMapping("/settings")
    public ApiResponse<Map<String, Object>> saveSettings(@RequestBody AdminSettingsRequest request) {
        log.info("管理员更新系统设置");
        sessionAuthService.requireAdmin();
        settingsService.setRegistrationEnabled(request.registrationEnabled());
        // Set share base URL first (may auto-disable passkey)
        settingsService.setShareBaseUrl(request.shareBaseUrl());
        settingsService.setTotpEnabled(request.totpEnabled());
        // Passkey can only be enabled if HTTPS
        if (request.passkeyEnabled()) {
            settingsService.setPasskeyEnabled(true);
        } else {
            settingsService.setPasskeyEnabled(false);
        }
        settingsService.setMcpServerEnabled(request.mcpEnabled());
        settingsService.setGitImportEnabled(request.gitImportEnabled());
        settingsService.setGitImportMaxConcurrent(request.gitImportMaxConcurrent());
        Map<String, Object> data = new HashMap<>();
        data.put("registrationEnabled", settingsService.isRegistrationEnabled());
        data.put("shareBaseUrl", settingsService.getShareBaseUrl());
        data.put("totpEnabled", settingsService.isTotpEnabled());
        data.put("passkeyEnabled", settingsService.isPasskeyEnabled());
        data.put("siteUrlHttps", settingsService.isSiteUrlHttps());
        data.put("mcpEnabled", settingsService.isMcpServerEnabled());
        data.put("gitImportEnabled", settingsService.isGitImportEnabled());
        data.put("gitImportMaxConcurrent", settingsService.getGitImportMaxConcurrent());
        return ApiResponse.ok(data);
    }

    /** 管理员设置请求体 */
    public record AdminSettingsRequest(boolean registrationEnabled, String shareBaseUrl,
                                       boolean totpEnabled, boolean passkeyEnabled,
                                       boolean mcpEnabled,
                                       boolean gitImportEnabled,
                                       int gitImportMaxConcurrent) {}
}
