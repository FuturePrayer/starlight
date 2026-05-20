package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.AssetService;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.SettingsService;
import cn.suhoan.starlight.service.asset.AssetStorageRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AssetStorageRegistry assetStorageRegistry;
    private final AssetService assetService;

    public AdminController(SessionAuthService sessionAuthService,
                           SettingsService settingsService,
                           AssetStorageRegistry assetStorageRegistry,
                           AssetService assetService) {
        this.sessionAuthService = sessionAuthService;
        this.settingsService = settingsService;
        this.assetStorageRegistry = assetStorageRegistry;
        this.assetService = assetService;
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
        putAssetSettings(data);
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
        settingsService.setAssetUploadEnabled(request.assetUploadEnabled());
        String requestedAssetProvider = request.assetStorageProvider() == null ? "local" : request.assetStorageProvider();
        if ("s3".equalsIgnoreCase(requestedAssetProvider) && !assetStorageRegistry.isS3Available()) {
            requestedAssetProvider = "local";
        }
        settingsService.setAssetStorageProvider(requestedAssetProvider);
        settingsService.setAssetUserQuotaBytes(request.assetUserQuotaBytes());
        settingsService.setAssetCleanupGraceHours(request.assetCleanupGraceHours());
        Map<String, Object> data = new HashMap<>();
        data.put("registrationEnabled", settingsService.isRegistrationEnabled());
        data.put("shareBaseUrl", settingsService.getShareBaseUrl());
        data.put("totpEnabled", settingsService.isTotpEnabled());
        data.put("passkeyEnabled", settingsService.isPasskeyEnabled());
        data.put("siteUrlHttps", settingsService.isSiteUrlHttps());
        data.put("mcpEnabled", settingsService.isMcpServerEnabled());
        data.put("gitImportEnabled", settingsService.isGitImportEnabled());
        data.put("gitImportMaxConcurrent", settingsService.getGitImportMaxConcurrent());
        putAssetSettings(data);
        return ApiResponse.ok(data);
    }

    @GetMapping("/assets/usage")
    public ApiResponse<Map<String, Object>> assetUsage(@RequestParam(defaultValue = "self") String scope) {
        UserAccount admin = sessionAuthService.requireAdmin();
        return ApiResponse.ok(assetService.getAdminUsage(admin, scope));
    }

    @PostMapping("/assets/cleanup")
    public ApiResponse<AssetService.CleanupResult> cleanupAssets(@RequestParam(defaultValue = "true") boolean dryRun,
                                                                 @RequestParam(defaultValue = "self") String scope) {
        UserAccount admin = sessionAuthService.requireAdmin();
        return ApiResponse.ok(assetService.cleanupUnreferenced(admin, dryRun, scope));
    }

    private void putAssetSettings(Map<String, Object> data) {
        String configuredProvider = settingsService.getAssetStorageProvider();
        boolean s3Available = assetStorageRegistry.isS3Available();
        data.put("assetUploadEnabled", settingsService.isAssetUploadEnabled());
        data.put("assetStorageProvider", "s3".equals(configuredProvider) && !s3Available ? "local" : configuredProvider);
        data.put("assetConfiguredStorageProvider", configuredProvider);
        data.put("assetS3Available", s3Available);
        data.put("assetUserQuotaBytes", settingsService.getAssetUserQuotaBytes());
        data.put("assetCleanupGraceHours", settingsService.getAssetCleanupGraceHours());
    }

    /** 管理员设置请求体 */
    public record AdminSettingsRequest(boolean registrationEnabled, String shareBaseUrl,
                                       boolean totpEnabled, boolean passkeyEnabled,
                                       boolean mcpEnabled,
                                       boolean gitImportEnabled,
                                       int gitImportMaxConcurrent,
                                       boolean assetUploadEnabled,
                                       String assetStorageProvider,
                                       long assetUserQuotaBytes,
                                       int assetCleanupGraceHours) {}
}
