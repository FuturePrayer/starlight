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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证控制器。
 * <p>处理用户注册、登录、登出、个人资料管理、TOTP 两步验证和通行密钥（WebAuthn）相关接口。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** 等待 TOTP 验证的登录请求，key 为 pendingToken */
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

    /** 获取注册状态信息（是否允许注册、是否需要引导管理员等） */
    @GetMapping("/registration-status")
    public ApiResponse<Map<String, Object>> registrationStatus() {
        boolean passkeyLoginAvailable = settingsService.isPasskeyEnabled() && settingsService.isSiteUrlHttps();
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", settingsService.isRegistrationEnabled());
        data.put("available", settingsService.isRegistrationAvailable());
        data.put("bootstrapAdminRequired", settingsService.isBootstrapAdminRegistrationRequired());
        data.put("passkeyEnabled", passkeyLoginAvailable);
        return ApiResponse.ok(data);
    }

    /** 注册新用户并自动登录 */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        log.info("用户注册请求: email={}", request.email());
        UserAccount userAccount = authService.register(request.email(), request.password());
        sessionAuthService.login(userAccount.getId());
        return ApiResponse.ok(authService.toProfile(userAccount));
    }

    /**
     * 用户登录。
     * <p>如果用户启用了 TOTP 两步验证，不会立即创建会话，而是返回一个 pendingToken 要求二次验证。</p>
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        log.info("用户登录请求: username={}", request.username());
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

    /** 验证 TOTP 两步验证码，验证通过后完成登录 */
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

    /** 退出登录 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        sessionAuthService.logout();
        return ApiResponse.okMessage("已退出登录");
    }

    /** 获取当前登录用户的个人资料 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(authService.toProfile(userAccount));
    }

    /** 更新用户个人资料（用户名和/或密码） */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody UpdateProfileRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        UserAccount updated = authService.updateProfile(userAccount, request.username(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(authService.toProfile(updated));
    }

    // ──── TOTP 两步验证管理（需要登录） ────

    /** 发起 TOTP 设置，生成密钥和二维码 */
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

    /** 确认绑定 TOTP，验证码校验通过后保存密钥 */
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

    /** 解除 TOTP 两步验证 */
    @DeleteMapping("/totp")
    public ApiResponse<Void> totpRevoke() {
        UserAccount user = sessionAuthService.requireUser();
        user.setTotpSecret(null);
        authService.updateTotpSecret(user);
        return ApiResponse.okMessage("两步验证已关闭");
    }

    // ──── 通行密钥管理（需要登录） ────

    /** 获取当前用户的通行密钥列表 */
    @GetMapping("/passkey/credentials")
    public ApiResponse<List<Map<String, Object>>> passkeyList() {
        UserAccount user = sessionAuthService.requireUser();
        return ApiResponse.ok(webAuthnService.listCredentials(user.getId()));
    }

    /** 发起通行密钥注册流程 */
    @PostMapping("/passkey/register/start")
    public ApiResponse<Map<String, Object>> passkeyRegisterStart() {
        UserAccount user = sessionAuthService.requireUser();
        if (!settingsService.isPasskeyEnabled()) {
            throw new IllegalArgumentException("管理员尚未开启通行密钥功能");
        }
        return ApiResponse.ok(webAuthnService.startRegistration(user));
    }

    /** 完成通行密钥注册 */
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

    /** 删除指定通行密钥 */
    @DeleteMapping("/passkey/credentials/{id}")
    public ApiResponse<Void> passkeyDelete(@PathVariable String id) {
        UserAccount user = sessionAuthService.requireUser();
        webAuthnService.deleteCredential(id, user.getId());
        return ApiResponse.okMessage("通行密钥已删除");
    }

    // ──── 通行密钥登录（无需登录） ────

    /** 发起通行密钥登录认证 */
    @PostMapping("/passkey/login/start")
    public ApiResponse<Map<String, Object>> passkeyLoginStart() {
        if (!settingsService.isPasskeyEnabled()) {
            throw new IllegalArgumentException("通行密钥登录未启用");
        }
        return ApiResponse.ok(webAuthnService.startAssertion());
    }

    /** 完成通行密钥登录认证 */
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

    /** 清理已过期的 TOTP 待验证记录 */
    private void cleanExpiredPending() {
        long now = System.currentTimeMillis();
        pendingTotpLogins.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    /** 注册请求体 */
    public record RegisterRequest(String email, String password) {}
    /** 登录请求体 */
    public record LoginRequest(String username, String password) {}
    /** 更新个人资料请求体 */
    public record UpdateProfileRequest(String username, String currentPassword, String newPassword) {}
    /** TOTP 登录验证请求体 */
    public record TotpLoginRequest(String pendingToken, String code) {}
    /** TOTP 绑定确认请求体 */
    public record TotpConfirmRequest(String secret, String code) {}
}
