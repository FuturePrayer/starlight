package cn.suhoan.startlight.controller;

import cn.suhoan.startlight.dto.ApiResponse;
import cn.suhoan.startlight.service.SessionAuthService;
import cn.suhoan.startlight.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return ApiResponse.ok(Map.of(
                "registrationEnabled", settingsService.isRegistrationEnabled(),
                "shareBaseUrl", settingsService.getShareBaseUrl()
        ));
    }

    @PostMapping("/settings")
    public ApiResponse<Map<String, Object>> saveSettings(@RequestBody AdminSettingsRequest request) {
        sessionAuthService.requireAdmin();
        settingsService.setRegistrationEnabled(request.registrationEnabled());
        settingsService.setShareBaseUrl(request.shareBaseUrl());
        return ApiResponse.ok(Map.of(
                "registrationEnabled", settingsService.isRegistrationEnabled(),
                "shareBaseUrl", settingsService.getShareBaseUrl()
        ));
    }

    public record AdminSettingsRequest(boolean registrationEnabled, String shareBaseUrl) {
    }
}
