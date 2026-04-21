package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.ApiKeyService;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.TotpService;
import cn.suhoan.starlight.service.WebAuthnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * API Key 管理控制器。
 * <p>当前用户可在此创建、查看、更新和删除自己的 API Key。</p>
 */
@RestController
@RequestMapping("/api/auth/api-keys")
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final SessionAuthService sessionAuthService;
    private final ApiKeyService apiKeyService;
    private final TotpService totpService;
    private final WebAuthnService webAuthnService;
    private final ObjectMapper objectMapper;

    public ApiKeyController(SessionAuthService sessionAuthService,
                            ApiKeyService apiKeyService,
                            TotpService totpService,
                            WebAuthnService webAuthnService,
                            ObjectMapper objectMapper) {
        this.sessionAuthService = sessionAuthService;
        this.apiKeyService = apiKeyService;
        this.totpService = totpService;
        this.webAuthnService = webAuthnService;
        this.objectMapper = objectMapper;
    }

    /** 查询当前用户的 API Key 列表。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listKeys() {
        UserAccount user = sessionAuthService.requireUser();
        return ApiResponse.ok(apiKeyService.listUserKeys(user.getId()));
    }

    /** 创建 API Key。 */
    @PostMapping
    public ApiResponse<Map<String, Object>> createKey(@RequestBody ApiKeyRequest request) {
        UserAccount user = sessionAuthService.requireUser();
        log.info("创建 API Key 请求: ownerId={}, name={}", user.getId(), request.name());
        return ApiResponse.ok(apiKeyService.createKey(
                user,
                request.name(),
                request.readOnlyFlag(),
                request.allowAllCategoriesFlag(),
                request.scopeCategoryIds()
        ));
    }

    /** 更新 API Key。 */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateKey(@PathVariable String id,
                                                      @RequestBody ApiKeyUpdateRequest request) {
        UserAccount user = sessionAuthService.requireUser();
        log.info("更新 API Key 请求: ownerId={}, apiKeyId={}", user.getId(), id);
        return ApiResponse.ok(apiKeyService.updateKey(
                user,
                id,
                request.name(),
                request.enabledFlag(),
                request.readOnlyFlag(),
                request.allowAllCategoriesFlag(),
                request.scopeCategoryIds()
        ));
    }

    /** 删除 API Key。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteKey(@PathVariable String id) {
        UserAccount user = sessionAuthService.requireUser();
        log.info("删除 API Key 请求: ownerId={}, apiKeyId={}", user.getId(), id);
        apiKeyService.deleteKey(user.getId(), id);
        return ApiResponse.okMessage("API Key 已删除");
    }

    /** 使用 TOTP 验证后复制 API Key 明文。 */
    @PostMapping("/{id}/copy/totp")
    public ApiResponse<Map<String, Object>> copyKeyWithTotp(@PathVariable String id,
                                                            @RequestBody ApiKeyCopyTotpRequest request) {
        UserAccount user = sessionAuthService.requireUser();
        apiKeyService.assertCopyVerificationAvailable(user);
        if (user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            throw new IllegalArgumentException("当前账户尚未开启两步验证");
        }
        if (!totpService.validate(user.getTotpSecret(), request.code())) {
            throw new IllegalArgumentException("两步验证码错误");
        }
        return ApiResponse.ok(Map.of("apiKey", apiKeyService.copyKey(user, id)));
    }

    /** 发起通行密钥验证，用于复制 API Key 明文。 */
    @PostMapping("/{id}/copy/passkey/start")
    public ApiResponse<Map<String, Object>> startCopyKeyWithPasskey(@PathVariable String id) {
        UserAccount user = sessionAuthService.requireUser();
        apiKeyService.assertCopyVerificationAvailable(user);
        apiKeyService.getOwnedKey(user.getId(), id);
        return ApiResponse.ok(webAuthnService.startAssertion());
    }

    /** 完成通行密钥验证，并返回 API Key 明文。 */
    @PostMapping("/{id}/copy/passkey/finish")
    public ApiResponse<Map<String, Object>> finishCopyKeyWithPasskey(@PathVariable String id,
                                                                     @RequestBody ApiKeyCopyPasskeyRequest request) {
        UserAccount user = sessionAuthService.requireUser();
        apiKeyService.assertCopyVerificationAvailable(user);
        UserAccount verifiedUser = webAuthnService.finishAssertion(request.handle(), toCredentialJson(request.credential()));
        if (!user.getId().equals(verifiedUser.getId())) {
            throw new IllegalArgumentException("当前通行密钥不属于当前登录用户");
        }
        return ApiResponse.ok(Map.of("apiKey", apiKeyService.copyKey(user, id)));
    }

    private String toCredentialJson(Object credential) {
        if (credential == null) {
            throw new IllegalArgumentException("请求格式错误");
        }
        if (credential instanceof String credentialJson) {
            return credentialJson;
        }
        try {
            return objectMapper.writeValueAsString(credential);
        } catch (Exception exception) {
            throw new IllegalArgumentException("请求格式错误", exception);
        }
    }

    /** 创建 API Key 请求体。 */
    public record ApiKeyRequest(String name,
                                boolean readOnlyFlag,
                                boolean allowAllCategoriesFlag,
                                List<String> scopeCategoryIds) {
    }

    /** 更新 API Key 请求体。 */
    public record ApiKeyUpdateRequest(String name,
                                      boolean enabledFlag,
                                      boolean readOnlyFlag,
                                      boolean allowAllCategoriesFlag,
                                      List<String> scopeCategoryIds) {
    }

    /** TOTP 复制验证请求体。 */
    public record ApiKeyCopyTotpRequest(String code) {
    }

    /** 通行密钥复制验证请求体。 */
    public record ApiKeyCopyPasskeyRequest(String handle, Object credential) {
    }
}

