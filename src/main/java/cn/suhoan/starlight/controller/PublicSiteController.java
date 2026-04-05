package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.PublicSiteService;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开站点（星迹书阁）控制器。
 * <p>提供分类的公开站点管理接口（需要登录）和公开访问接口（无需登录）。
 * 用户可将分类设为公开站点，生成只读公开页面用于轻量级知识分享。</p>
 *
 * @author suhoan
 */
@RestController
public class PublicSiteController {

    private static final Logger log = LoggerFactory.getLogger(PublicSiteController.class);

    private final SessionAuthService sessionAuthService;
    private final PublicSiteService publicSiteService;
    private final SettingsService settingsService;

    public PublicSiteController(SessionAuthService sessionAuthService,
                                PublicSiteService publicSiteService,
                                SettingsService settingsService) {
        this.sessionAuthService = sessionAuthService;
        this.publicSiteService = publicSiteService;
        this.settingsService = settingsService;
    }

    // ──── 需要登录的管理接口 ────

    /** 获取分类的星迹书阁信息 */
    @GetMapping("/api/categories/{id}/site")
    public ApiResponse<Map<String, Object>> getSiteInfo(@PathVariable String id) {
        UserAccount user = sessionAuthService.requireUser();
        return ApiResponse.ok(publicSiteService.getSiteInfo(user, id));
    }

    /** 开启星迹书阁 */
    @PostMapping("/api/categories/{id}/site")
    public ApiResponse<Map<String, Object>> enableSite(@PathVariable String id,
                                                       @RequestBody(required = false) SiteRequest request) {
        log.info("开启星迹书阁请求: categoryId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount user = sessionAuthService.requireUser();
        String siteTitle = request != null ? request.siteTitle() : null;
        boolean mergeSubSites = request != null && Boolean.TRUE.equals(request.mergeSubSites());
        return ApiResponse.ok(publicSiteService.enableSite(user, id, siteTitle, mergeSubSites));
    }

    /** 关闭星迹书阁 */
    @DeleteMapping("/api/categories/{id}/site")
    public ApiResponse<Void> disableSite(@PathVariable String id) {
        log.info("关闭星迹书阁请求: categoryId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount user = sessionAuthService.requireUser();
        publicSiteService.disableSite(user, id);
        return ApiResponse.okMessage("星迹书阁已关闭");
    }

    /** 更新星迹书阁标题 */
    @PutMapping("/api/categories/{id}/site")
    public ApiResponse<Map<String, Object>> updateSiteTitle(@PathVariable String id,
                                                            @RequestBody SiteRequest request) {
        log.info("更新星迹书阁标题请求: categoryId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount user = sessionAuthService.requireUser();
        return ApiResponse.ok(publicSiteService.updateSiteTitle(user, id, request.siteTitle()));
    }

    // ──── 无需登录的公开访问接口 ────

    /** 公开访问：获取站点首页（文章列表） */
    @GetMapping("/api/site/{token}")
    public ApiResponse<Map<String, Object>> getPublicSiteIndex(@PathVariable String token) {
        return ApiResponse.ok(publicSiteService.getPublicSiteIndex(token));
    }

    /** 公开访问：获取站点中某篇文章详情 */
    @GetMapping("/api/site/{token}/notes/{noteId}")
    public ApiResponse<Map<String, Object>> getPublicSiteNote(@PathVariable String token,
                                                              @PathVariable String noteId) {
        return ApiResponse.ok(publicSiteService.getPublicSiteNote(token, noteId));
    }

    /**
     * 公开访问：获取站点 URL。
     * <p>二维码由前端根据返回的 URL 自行生成，后端不再负责图片渲染。</p>
     */
    @GetMapping("/api/site/{token}/qrcode")
    public ApiResponse<Map<String, Object>> siteQrCode(@PathVariable String token,
                                                       HttpServletRequest request) {
        // 先验证 token 有效性（会在 service 层抛异常）
        publicSiteService.getPublicSiteIndex(token);
        String url = getSiteUrl(token, request);
        log.debug("获取站点URL: token={}", token);
        return ApiResponse.ok(Map.of("url", url));
    }

    /** 构建站点公开访问 URL */
    private String getSiteUrl(String token, HttpServletRequest request) {
        String baseUrl = settingsService.getShareBaseUrl();
        String prefix = baseUrl.isBlank() ? getBaseUrl(request) : baseUrl;
        return prefix + "/site/" + token;
    }

    /** 从 HTTP 请求中提取基础 URL */
    private String getBaseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() +
                ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort()) +
                request.getContextPath();
    }

    /** 星迹书阁请求体 */
    public record SiteRequest(String siteTitle, Boolean mergeSubSites) {
    }
}

