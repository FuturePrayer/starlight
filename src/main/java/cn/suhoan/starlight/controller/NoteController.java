package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.CategoryAccessService;
import cn.suhoan.starlight.service.NoteService;
import cn.suhoan.starlight.service.NoteTransferService;
import cn.suhoan.starlight.service.SessionAuthService;
import cn.suhoan.starlight.service.search.NoteSearchService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 笔记管理控制器。
 * <p>提供笔记和分类的 CRUD 接口、笔记树形结构查询和全文搜索功能。</p>
 *
 * @author suhoan
 */
@RestController
@RequestMapping("/api")
public class NoteController {

    private static final Logger log = LoggerFactory.getLogger(NoteController.class);

    private final SessionAuthService sessionAuthService;
    private final NoteService noteService;
    private final NoteTransferService noteTransferService;
    private final NoteSearchService noteSearchService;
    private final CategoryAccessService categoryAccessService;

    public NoteController(SessionAuthService sessionAuthService,
                          NoteService noteService,
                          NoteTransferService noteTransferService,
                          NoteSearchService noteSearchService,
                          CategoryAccessService categoryAccessService) {
        this.sessionAuthService = sessionAuthService;
        this.noteService = noteService;
        this.noteTransferService = noteTransferService;
        this.noteSearchService = noteSearchService;
        this.categoryAccessService = categoryAccessService;
    }

    /** 获取笔记树形结构（分类 + 笔记） */
    @GetMapping("/tree")
    public ApiResponse<Map<String, Object>> tree() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.buildTree(userAccount.getId()));
    }

    /** 获取当前用户的所有笔记摘要列表 */
    @GetMapping("/notes")
    public ApiResponse<Object> notes() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.listUserNotes(userAccount.getId()));
    }

    /** 获取回收站笔记列表 */
    @GetMapping("/trash")
    public ApiResponse<List<Map<String, Object>>> trashNotes() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.listTrashNotes(userAccount.getId()));
    }

    /** 获取回收站树形结构（分类 + 笔记）。 */
    @GetMapping("/trash/tree")
    public ApiResponse<Map<String, Object>> trashTree() {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.buildTrashTree(userAccount.getId()));
    }

    /** 创建分类 */
    @PostMapping("/categories")
    public ApiResponse<Map<String, Object>> createCategory(@RequestBody CategoryRequest request) {
        UserAccount userAccount = sessionAuthService.requireUser();
        Category category = noteService.createCategory(userAccount, request.name(), request.parentId());
        Map<String, Object> result = new HashMap<>();
        result.put("id", category.getId());
        result.put("name", category.getName());
        result.put("parentId", category.getParent() == null ? null : category.getParent().getId());
        return ApiResponse.ok(result);
    }

    /** 将分类及其子树移入回收站。 */
    @DeleteMapping("/categories/{id}")
    public ApiResponse<Map<String, Object>> deleteCategory(@PathVariable String id) {
        log.info("删除分类请求: categoryId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        NoteService.CategoryTrashOperationResult result = noteService.deleteCategory(userAccount.getId(), id);
        Map<String, Object> data = new HashMap<>();
        data.put("categoryId", result.categoryId());
        data.put("categoryCount", result.categoryCount());
        data.put("noteCount", result.noteCount());
        return ApiResponse.ok(data);
    }

    /** 创建新笔记 */
    @PostMapping("/notes")
    public ApiResponse<Map<String, Object>> createNote(@RequestBody NoteRequest request) {
        log.info("创建笔记请求: userId={}", sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.createNote(userAccount, request.title(), request.markdownContent(), request.categoryId());
        return ApiResponse.ok(noteService.toDetail(note));
    }

    /** 获取笔记详情 */
    @GetMapping("/notes/{id}")
    public ApiResponse<Map<String, Object>> getNote(@PathVariable String id) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.getNoteDetail(userAccount.getId(), id));
    }

    /** 获取回收站笔记详情 */
    @GetMapping("/trash/{id}")
    public ApiResponse<Map<String, Object>> getTrashNote(@PathVariable String id) {
        UserAccount userAccount = sessionAuthService.requireUser();
        return ApiResponse.ok(noteService.getTrashNoteDetail(userAccount.getId(), id));
    }

    /** 更新笔记内容 */
    @PutMapping("/notes/{id}")
    public ApiResponse<Map<String, Object>> updateNote(@PathVariable String id,
                                                       @RequestBody NoteRequest request) {
        log.info("更新笔记请求: noteId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.updateNote(userAccount, id, request.title(), request.markdownContent(), request.categoryId());
        return ApiResponse.ok(noteService.toDetail(note));
    }

    /** 删除笔记 */
    @DeleteMapping("/notes/{id}")
    public ApiResponse<Void> deleteNote(@PathVariable String id) {
        log.info("删除笔记请求: noteId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        noteService.deleteNote(userAccount.getId(), id);
        return ApiResponse.okMessage("已移入回收站");
    }

    /** 恢复回收站中的笔记 */
    @PostMapping("/trash/{id}/restore")
    public ApiResponse<Map<String, Object>> restoreNote(@PathVariable String id) {
        log.info("恢复回收站笔记请求: noteId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.restoreNote(userAccount.getId(), id);
        return ApiResponse.ok(noteService.toDetail(note));
    }

    /** 恢复回收站中的分类子树。 */
    @PostMapping("/trash/categories/{id}/restore")
    public ApiResponse<Map<String, Object>> restoreTrashCategory(@PathVariable String id) {
        log.info("恢复回收站分类请求: categoryId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        NoteService.CategoryTrashOperationResult result = noteService.restoreTrashCategory(userAccount.getId(), id);
        Map<String, Object> data = new HashMap<>();
        data.put("categoryId", result.categoryId());
        data.put("categoryCount", result.categoryCount());
        data.put("noteCount", result.noteCount());
        return ApiResponse.ok(data);
    }

    /** 彻底删除回收站中的笔记 */
    @DeleteMapping("/trash/{id}")
    public ApiResponse<Void> purgeNote(@PathVariable String id) {
        log.info("彻底删除回收站笔记请求: noteId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        noteService.purgeNote(userAccount.getId(), id);
        return ApiResponse.okMessage("已彻底删除");
    }

    /** 彻底删除回收站中的分类子树。 */
    @DeleteMapping("/trash/categories/{id}")
    public ApiResponse<Map<String, Object>> purgeTrashCategory(@PathVariable String id) {
        log.info("彻底删除回收站分类请求: categoryId={}, userId={}", id, sessionAuthService.getCurrentUserId());
        UserAccount userAccount = sessionAuthService.requireUser();
        NoteService.CategoryTrashOperationResult result = noteService.purgeTrashCategory(userAccount.getId(), id);
        Map<String, Object> data = new HashMap<>();
        data.put("categoryId", result.categoryId());
        data.put("categoryCount", result.categoryCount());
        data.put("noteCount", result.noteCount());
        return ApiResponse.ok(data);
    }


    /** 更新笔记置顶状态 */
    @PutMapping("/notes/{id}/pinned")
    public ApiResponse<Map<String, Object>> updatePinned(@PathVariable String id,
                                                         @RequestBody FlagRequest request) {
        log.info("更新笔记置顶状态请求: noteId={}, userId={}, pinned={}", id, sessionAuthService.getCurrentUserId(), request.value());
        UserAccount userAccount = sessionAuthService.requireUser();
        Note note = noteService.setPinned(userAccount.getId(), id, request.value());
        return ApiResponse.ok(noteService.toDetail(note));
    }

    /**
     * 导出当前用户的全部笔记。
     * <p>导出结果为 ZIP 文件，内部结构为「分类目录 + Markdown 文件」。</p>
     */
    @GetMapping("/notes/export")
    public ResponseEntity<byte[]> exportNotes() {
        UserAccount userAccount = sessionAuthService.requireUser();
        log.info("导出笔记请求: userId={}", userAccount.getId());
        NoteTransferService.ArchivePayload payload = noteTransferService.exportArchive(userAccount.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(payload.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(payload.content().length)
                .body(payload.content());
    }

    /**
     * 导入 ZIP 形式的 Markdown 笔记包。
     * <p>会根据 ZIP 内部目录结构创建分类，并将 Markdown 文件导入为笔记。</p>
     */
    @PostMapping(value = "/notes/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importNotes(@RequestParam("file") MultipartFile file) {
        UserAccount userAccount = sessionAuthService.requireUser();
        log.info("导入笔记请求: userId={}, fileName={}, size={}",
                userAccount.getId(), file == null ? null : file.getOriginalFilename(), file == null ? 0 : file.getSize());
        return ApiResponse.ok(noteTransferService.importArchive(userAccount, file));
    }

    /**
     * 全文搜索笔记。
     * <p>支持多关键词（空格分隔）搜索与评分排序。
     * 支持搜索范围：all（所有笔记）、categories（指定分类）、current（当前笔记）。</p>
     *
     * @param q           搜索关键词（支持空格分隔多个关键词）
     * @param offset      偏移量
     * @param limit       每页数量（最大50）
     * @param scope       搜索范围：all / categories / current
     * @param categoryIds 指定分类 ID（scope=categories 时必填，逗号分隔，后端会展开子分类）
     * @param noteId      笔记 ID（scope=current 时必填）
     */
    @GetMapping("/notes/search")
    public ApiResponse<Map<String, Object>> searchNotes(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(required = false) String noteId) {
        if (q == null || q.isBlank()) {
            return ApiResponse.ok(Map.of("items", List.of(), "hasMore", false));
        }
        int safeLimit = Math.clamp(limit, 1, 50);
        int safeOffset = Math.max(offset, 0);
        UserAccount userAccount = sessionAuthService.requireUser();
        String trimmedQuery = q.trim();

        List<Map<String, Object>> results;

        switch (scope) {
            case "current" -> {
                // 搜索当前笔记
                if (noteId == null || noteId.isBlank()) {
                    return ApiResponse.ok(Map.of("items", List.of(), "hasMore", false));
                }
                results = noteSearchService.searchInNote(userAccount.getId(), noteId.trim(), trimmedQuery);
                return ApiResponse.ok(Map.of("items", results, "hasMore", false));
            }
            case "categories" -> {
                // 搜索指定分类（后端展开子分类）
                if (categoryIds == null || categoryIds.isBlank()) {
                    return ApiResponse.ok(Map.of("items", List.of(), "hasMore", false));
                }
                List<String> rootIds = Arrays.stream(categoryIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (rootIds.isEmpty()) {
                    return ApiResponse.ok(Map.of("items", List.of(), "hasMore", false));
                }
                // 展开父分类为包含所有后代的完整集合
                Set<String> expandedIds = categoryAccessService.expandAuthorizedCategoryIds(
                        userAccount.getId(), rootIds);
                results = noteSearchService.search(
                        userAccount.getId(), trimmedQuery, safeOffset, safeLimit + 1, expandedIds);
            }
            default -> {
                // 搜索所有笔记
                results = noteSearchService.search(
                        userAccount.getId(), trimmedQuery, safeOffset, safeLimit + 1);
            }
        }

        boolean hasMore = results.size() > safeLimit;
        List<Map<String, Object>> page = hasMore ? results.subList(0, safeLimit) : results;
        return ApiResponse.ok(Map.of("items", page, "hasMore", hasMore));
    }

    /** 创建分类请求体 */
    public record CategoryRequest(String name, String parentId) {
    }

    /** 创建/更新笔记请求体 */
    public record NoteRequest(String title, String markdownContent, String categoryId) {
    }

    /** 布尔状态更新请求体 */
    public record FlagRequest(boolean value) {
    }
}

