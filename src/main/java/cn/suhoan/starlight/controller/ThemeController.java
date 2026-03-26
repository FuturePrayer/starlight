package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.ThemeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    private final ThemeService themeService;
    private final SessionAuthService sessionAuthService;

    public ThemeController(ThemeService themeService, SessionAuthService sessionAuthService) {
        this.themeService = themeService;
        this.sessionAuthService = sessionAuthService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listThemes() {
        return ApiResponse.ok(themeService.listThemes());
    }

    @PostMapping("/select")
    public ApiResponse<Map<String, Object>> selectTheme(@RequestBody ThemeRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(themeService.saveUserTheme(userAccount, request.themeId()));
    }

    public record ThemeRequest(String themeId) {
    }
}

