package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.GitImportService;
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
 * Git 仓库导入控制器。
 * <p>负责前端 Git 导入向导、导入源列表、手动重导入与自动同步设置。</p>
 */
@RestController
@RequestMapping("/api/git")
public class GitImportController {

    private static final Logger log = LoggerFactory.getLogger(GitImportController.class);

    private final SessionAuthService sessionAuthService;
    private final GitImportService gitImportService;

    public GitImportController(SessionAuthService sessionAuthService, GitImportService gitImportService) {
        this.sessionAuthService = sessionAuthService;
        this.gitImportService = gitImportService;
    }

    /** 查询 Git 导入功能状态。 */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        sessionAuthService.requireUser();
        return ApiResponse.ok(gitImportService.getFeatureStatus());
    }

    /** 根据仓库地址解析分支。 */
    @PostMapping("/branches")
    public ApiResponse<Map<String, Object>> resolveBranches(@RequestBody RepositoryRequest request) {
        sessionAuthService.requireUser();
        return ApiResponse.ok(gitImportService.resolveBranches(request.repositoryUrl()));
    }

    /** 创建 Git 导入预览。 */
    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> createPreview(@RequestBody PreviewRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        log.info("创建 Git 导入预览: userId={}, branch={}", userAccount.getId(), request.branchName());
        return ApiResponse.ok(gitImportService.createPreview(userAccount, request.repositoryUrl(), request.branchName()));
    }

    /** 关闭预览并删除临时目录。 */
    @DeleteMapping("/preview/{token}")
    public ApiResponse<Void> discardPreview(@PathVariable String token) {
        UserAccount userAccount = sessionAuthService.requireUser();
        gitImportService.discardPreview(userAccount, token);
        return ApiResponse.okMessage("预览已关闭");
    }

    /** 执行首次导入。 */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importFromPreview(@RequestBody ImportRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        log.info("执行 Git 首次导入: userId={}, previewToken={}", userAccount.getId(), request.previewToken());
        return ApiResponse.ok(gitImportService.importFromPreview(userAccount,
                new GitImportService.GitImportRequest(
                        request.previewToken(),
                        request.sourcePath(),
                        request.existingTargetCategoryId(),
                        request.targetCategoryName(),
                        request.autoSyncEnabled(),
                        request.scheduleType(),
                        request.scheduleTimezone(),
                        request.scheduleHour(),
                        request.scheduleMinute(),
                        request.scheduleDayOfWeek()
                )));
    }

    /** 查询当前用户已保存的导入源。 */
    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> listSources() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(gitImportService.listSources(userAccount.getId()));
    }

    /** 手动重导入指定导入源。 */
    @PostMapping("/sources/{sourceId}/sync")
    public ApiResponse<Map<String, Object>> syncNow(@PathVariable String sourceId) {
        UserAccount userAccount = sessionAuthService.requireUser();
        log.info("手动触发 Git 重导入: userId={}, sourceId={}", userAccount.getId(), sourceId);
        return ApiResponse.ok(gitImportService.syncSourceNow(userAccount.getId(), sourceId));
    }

    /** 删除已保存的 Git 导入源，但不删除已导入的笔记数据。 */
    @DeleteMapping("/sources/{sourceId}")
    public ApiResponse<Void> deleteSource(@PathVariable String sourceId) {
        UserAccount userAccount = sessionAuthService.requireUser();
        log.info("删除 Git 导入源: userId={}, sourceId={}", userAccount.getId(), sourceId);
        gitImportService.deleteSource(userAccount.getId(), sourceId);
        return ApiResponse.okMessage("已删除保存的 Git 导入源");
    }

    /** 更新自动同步设置。 */
    @PutMapping("/sources/{sourceId}/auto-sync")
    public ApiResponse<Map<String, Object>> updateAutoSync(@PathVariable String sourceId,
                                                           @RequestBody AutoSyncRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(gitImportService.updateAutoSync(userAccount, sourceId,
                new GitImportService.GitAutoSyncRequest(
                        request.autoSyncEnabled(),
                        request.scheduleType(),
                        request.scheduleTimezone(),
                        request.scheduleHour(),
                        request.scheduleMinute(),
                        request.scheduleDayOfWeek()
                )));
    }

    /** 仅包含仓库地址的请求。 */
    public record RepositoryRequest(String repositoryUrl) {
    }

    /** 预览请求。 */
    public record PreviewRequest(String repositoryUrl, String branchName) {
    }

    /** 首次导入请求。 */
    public record ImportRequest(String previewToken,
                                String sourcePath,
                                String existingTargetCategoryId,
                                String targetCategoryName,
                                boolean autoSyncEnabled,
                                String scheduleType,
                                String scheduleTimezone,
                                Integer scheduleHour,
                                Integer scheduleMinute,
                                Integer scheduleDayOfWeek) {
    }

    /** 自动同步设置请求。 */
    public record AutoSyncRequest(boolean autoSyncEnabled,
                                  String scheduleType,
                                  String scheduleTimezone,
                                  Integer scheduleHour,
                                  Integer scheduleMinute,
                                  Integer scheduleDayOfWeek) {
    }
}

