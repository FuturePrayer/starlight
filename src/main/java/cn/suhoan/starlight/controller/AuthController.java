package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.AuthService;
import cn.suhoan.starlight.service.QrCodeService;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.SettingsService;
import cn.suhoan.starlight.service.TotpService;
import cn.suhoan.starlight.service.WebAuthnService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private record PendingTotp(String userId, long expiresAt) {}

    private final ConcurrentHashMap<String, PendingTotp> pendingTotpLogins = new ConcurrentHashMap<>();

    private final AuthService authService;
    private final SessionAuthService sessionAuthService;
    private final SettingsService settingsService;
    private final TotpService totpService;
    private final QrCodeService qrCodeService;
    private final WebAuthnService webAuthnService;

    public AuthController(AuthService authService,
                          SessionAuthService sessionAuthService,
                          SettingsService settingsService,
                          TotpService totpService,
                          QrCodeService qrCodeService,
                          WebAuthnService webAuthnService) {
        this.authService = authService;
        this.sessionAuthService = sessionAuthService;
        this.settingsService = settingsService;
        this.totpService = totpService;
        this.qrCodeService = qrCodeService;
        this.webAuthnService = webAuthnService;
    }

    @GetMapping("/registration-status")
    public ApiResponse<Map<String, Object>> registrationStatus() {
        boolean passkeyLoginAvailable = settingsService.isPasskeyEnabled() && settingsService.isSiteUrlHttps();
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", settingsService.isRegistrationEnabled());
        data.put("passkeyEnabled", passkeyLoginAvailable);
        return ApiResponse.ok(data);
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
        // Check if TOTP is required
        boolean totpGlobalEnabled = settingsService.isTotpEnabled();
        boolean userHasTotp = userAccount.getTotpSecret() != null && !userAccount.getTotpSecret().isBlank();
        if (totpGlobalEnabled && userHasTotp) {
            // Don't create session yet; return a pending token
            String pendingToken = UUID.randomUUID().toString();
            pendingTotpLogins.put(pendingToken, new PendingTotp(userAccount.getId(), System.currentTimeMillis() + 300_000));
            cleanExpiredPending();
            Map<String, Object> data = new HashMap<>();
            data.put("requireTotp", true);
            data.put("pendingToken", pendingToken);
            return ApiResponse.ok(data);
        }
        sessionAuthService.login(userAccount.getId());
        return ApiResponse.ok(authService.toProfile(userAccount));
    }

    @PostMapping("/totp/verify-login")
    public ApiResponse<Map<String, Object>> verifyTotpLogin(@RequestBody TotpLoginRequest request) {
        PendingTotp pending = pendingTotpLogins.remove(request.pendingToken());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("验证已过期，请重新登录");
        }
        UserAccount userAccount = sessionAuthService.findUserById(pending.userId());
        if (!totpService.validate(userAccount.getTotpSecret(), request.code())) {
            // Put it back for retry
            pendingTotpLogins.put(request.pendingToken(), pending);
            throw new IllegalArgumentException("验证码错误");
        }
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

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody UpdateProfileRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        UserAccount updated = authService.updateProfile(userAccount, request.username(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(authService.toProfile(updated));
    }

    // ──── TOTP management (requires login) ────

    @PostMapping("/totp/setup")
    public ApiResponse<Map<String, Object>> totpSetup() {
        UserAccount user = sessionAuthService.requireUser();
        if (!settingsService.isTotpEnabled()) {
            throw new IllegalArgumentException("管理员尚未开启两步验证功能");
        }
        String secret = totpService.generateSecret();
        String otpAuthUri = totpService.generateOtpAuthUri(user.getEmail(), secret);
        String qrDataUrl = qrCodeService.generateDataUrl(otpAuthUri, 256);
        Map<String, Object> data = new HashMap<>();
        data.put("secret", secret);
        data.put("qrDataUrl", qrDataUrl);
        return ApiResponse.ok(data);
    }

    @PostMapping("/totp/confirm")
    public ApiResponse<Void> totpConfirm(@RequestBody TotpConfirmRequest request) {
        UserAccount user = sessionAuthService.requireUser();
        if (!totpService.validate(request.secret(), request.code())) {
            throw new IllegalArgumentException("验证码错误，请重试");
        }
        user.setTotpSecret(request.secret());
        authService.updateTotpSecret(user);
        return ApiResponse.okMessage("两步验证已开启");
    }

    @DeleteMapping("/totp")
    public ApiResponse<Void> totpRevoke() {
        UserAccount user = sessionAuthService.requireUser();
        user.setTotpSecret(null);
        authService.updateTotpSecret(user);
        return ApiResponse.okMessage("两步验证已关闭");
    }

    // ──── Passkey management (requires login) ────

    @GetMapping("/passkey/credentials")
    public ApiResponse<List<Map<String, Object>>> passkeyList() {
        UserAccount user = sessionAuthService.requireUser();
        return ApiResponse.ok(webAuthnService.listCredentials(user.getId()));
    }

    @PostMapping("/passkey/register/start")
    public ApiResponse<Map<String, Object>> passkeyRegisterStart() {
        UserAccount user = sessionAuthService.requireUser();
        if (!settingsService.isPasskeyEnabled()) {
            throw new IllegalArgumentException("管理员尚未开启通行密钥功能");
        }
        return ApiResponse.ok(webAuthnService.startRegistration(user));
    }

    @PostMapping("/passkey/register/finish")
    public ApiResponse<Void> passkeyRegisterFinish(@RequestBody Map<String, Object> body) {
        UserAccount user = sessionAuthService.requireUser();
        String handle = (String) body.get("handle");
        String credentialJson = body.get("credential").toString();
        String nickname = (String) body.get("nickname");
        // If credential is a Map (from JSON parsing), re-serialize it
        if (body.get("credential") instanceof Map) {
            try {
                credentialJson = new ObjectMapper().writeValueAsString(body.get("credential"));
            } catch (Exception e) {
                throw new IllegalArgumentException("请求格式错误");
            }
        }
        webAuthnService.finishRegistration(user, handle, credentialJson, nickname);
        return ApiResponse.okMessage("通行密钥已注册");
    }

    @DeleteMapping("/passkey/credentials/{id}")
    public ApiResponse<Void> passkeyDelete(@PathVariable String id) {
        UserAccount user = sessionAuthService.requireUser();
        webAuthnService.deleteCredential(id, user.getId());
        return ApiResponse.okMessage("通行密钥已删除");
    }

    // ──── Passkey login (no auth needed) ────

    @PostMapping("/passkey/login/start")
    public ApiResponse<Map<String, Object>> passkeyLoginStart() {
        if (!settingsService.isPasskeyEnabled()) {
            throw new IllegalArgumentException("通行密钥登录未启用");
        }
        return ApiResponse.ok(webAuthnService.startAssertion());
    }

    @PostMapping("/passkey/login/finish")
    public ApiResponse<Map<String, Object>> passkeyLoginFinish(@RequestBody Map<String, Object> body) {
        String handle = (String) body.get("handle");
        String credentialJson = body.get("credential").toString();
        if (body.get("credential") instanceof Map) {
            try {
                credentialJson = new ObjectMapper().writeValueAsString(body.get("credential"));
            } catch (Exception e) {
                throw new IllegalArgumentException("请求格式错误");
            }
        }
        UserAccount userAccount = webAuthnService.finishAssertion(handle, credentialJson);
        sessionAuthService.login(userAccount.getId());
        return ApiResponse.ok(authService.toProfile(userAccount));
    }

    private void cleanExpiredPending() {
        long now = System.currentTimeMillis();
        pendingTotpLogins.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    public record RegisterRequest(String email, String password) {}
    public record LoginRequest(String username, String password) {}
    public record UpdateProfileRequest(String username, String currentPassword, String newPassword) {}
    public record TotpLoginRequest(String pendingToken, String code) {}
    public record TotpConfirmRequest(String secret, String code) {}
}
