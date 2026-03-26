package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SessionAuthService sessionAuthService;
    private final SettingsService settingsService;

    public AdminController(SessionAuthService sessionAuthService, SettingsService settingsService) {
        this.sessionAuthService = sessionAuthService;
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> getSettings() {
        sessionAuthService.requireAdmin();
        Map<String, Object> data = new HashMap<>();
        data.put("registrationEnabled", settingsService.isRegistrationEnabled());
        data.put("shareBaseUrl", settingsService.getShareBaseUrl());
        data.put("totpEnabled", settingsService.isTotpEnabled());
        data.put("passkeyEnabled", settingsService.isPasskeyEnabled());
        data.put("siteUrlHttps", settingsService.isSiteUrlHttps());
        return ApiResponse.ok(data);
    }

    @PostMapping("/settings")
    public ApiResponse<Map<String, Object>> saveSettings(@RequestBody AdminSettingsRequest request) {
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
        Map<String, Object> data = new HashMap<>();
        data.put("registrationEnabled", settingsService.isRegistrationEnabled());
        data.put("shareBaseUrl", settingsService.getShareBaseUrl());
        data.put("totpEnabled", settingsService.isTotpEnabled());
        data.put("passkeyEnabled", settingsService.isPasskeyEnabled());
        data.put("siteUrlHttps", settingsService.isSiteUrlHttps());
        return ApiResponse.ok(data);
    }

    public record AdminSettingsRequest(boolean registrationEnabled, String shareBaseUrl,
                                       boolean totpEnabled, boolean passkeyEnabled) {}
}
