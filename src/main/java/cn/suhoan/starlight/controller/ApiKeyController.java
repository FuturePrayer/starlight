package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.ApiKeyService;
import cn.suhoan.starlight.service.SessionAuthService;
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

    public ApiKeyController(SessionAuthService sessionAuthService, ApiKeyService apiKeyService) {
        this.sessionAuthService = sessionAuthService;
        this.apiKeyService = apiKeyService;
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
}

