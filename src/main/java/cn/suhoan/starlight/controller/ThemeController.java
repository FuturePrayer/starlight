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

/**
 * 主题管理控制器。
 * <p>提供主题列表查询和用户主题切换功能。</p>
 *
 * @author suhoan
 */
@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    private final ThemeService themeService;
    private final SessionAuthService sessionAuthService;

    public ThemeController(ThemeService themeService, SessionAuthService sessionAuthService) {
        this.themeService = themeService;
        this.sessionAuthService = sessionAuthService;
    }

    /** 获取所有可用主题列表 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listThemes() {
        return ApiResponse.ok(themeService.listThemes());
    }

    /** 切换当前用户的主题 */
    @PostMapping("/select")
    public ApiResponse<Map<String, Object>> selectTheme(@RequestBody ThemeRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(themeService.saveUserTheme(userAccount, request.themeId()));
    }

    /** 主题选择请求体 */
    public record ThemeRequest(String themeId) {
    }
}

