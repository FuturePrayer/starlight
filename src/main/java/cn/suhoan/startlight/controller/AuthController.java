package cn.suhoan.startlight.controller;

import cn.suhoan.startlight.dto.ApiResponse;
import cn.suhoan.startlight.entity.UserAccount;
import cn.suhoan.startlight.service.AuthService;
import cn.suhoan.startlight.service.SessionAuthService;
import cn.suhoan.startlight.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionAuthService sessionAuthService;
    private final SettingsService settingsService;

    public AuthController(AuthService authService,
                          SessionAuthService sessionAuthService,
                          SettingsService settingsService) {
        this.authService = authService;
        this.sessionAuthService = sessionAuthService;
        this.settingsService = settingsService;
    }

    @GetMapping("/registration-status")
    public ApiResponse<Map<String, Object>> registrationStatus() {
        return ApiResponse.ok(Map.of("enabled", settingsService.isRegistrationEnabled()));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        UserAccount userAccount = authService.register(request.email(), request.password());
        sessionAuthService.login(userAccount.getId());
        return ApiResponse.ok(authService.toProfile(userAccount));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        UserAccount userAccount = authService.login(request.username(), request.password());
        sessionAuthService.login(userAccount.getId());
        return ApiResponse.ok(authService.toProfile(userAccount));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        sessionAuthService.logout();
        return ApiResponse.okMessage("已退出登录");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(authService.toProfile(userAccount));
    }

    public record RegisterRequest(String email, String password) {
    }

    public record LoginRequest(String username, String password) {
    }
}

