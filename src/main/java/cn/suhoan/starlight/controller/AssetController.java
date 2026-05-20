package cn.suhoan.starlight.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.Asset;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.AssetService;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.asset.AssetStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final SessionAuthService sessionAuthService;
    private final AssetService assetService;

    public AssetController(SessionAuthService sessionAuthService, AssetService assetService) {
        this.sessionAuthService = sessionAuthService;
        this.assetService = assetService;
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> settings() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(assetService.getSettings(userAccount));
    }

    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(assetService.getUsage(userAccount));
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "noteId", required = false) String noteId) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(assetService.uploadImage(userAccount, file, noteId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> meta(@PathVariable String id) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(assetService.getAssetMeta(userAccount, id));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable String id,
                                                       @RequestParam(value = "token", required = false) String token,
                                                       @RequestHeader(value = "Referer", required = false) String referer) {
        UserAccount currentUser = null;
        try {
            if (StpUtil.isLogin()) {
                currentUser = sessionAuthService.findUserById(StpUtil.getLoginIdAsString());
            }
        } catch (Exception ignored) {
            currentUser = null;
        }
        Asset asset = assetService.getReadableAsset(id, token, currentUser, referer);
        AssetStream stream = assetService.openContent(asset);
        MediaType mediaType = MediaType.parseMediaType(asset.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(asset.getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header(HttpHeaders.ETAG, "\"" + asset.getSha256() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .body(new InputStreamResource(stream.inputStream()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        UserAccount userAccount = sessionAuthService.requireUser();
        assetService.deleteAsset(userAccount, id);
        return ApiResponse.okMessage("图片已删除");
    }

    @PostMapping("/cleanup")
    public ApiResponse<AssetService.CleanupResult> cleanup(@RequestParam(defaultValue = "true") boolean dryRun,
                                                           @RequestParam(defaultValue = "self") String scope) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(assetService.cleanupUnreferenced(userAccount, dryRun, scope));
    }
}
