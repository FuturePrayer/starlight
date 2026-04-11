package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.service.search.NoteSearchService;
import cn.suhoan.starlight.support.McpApiKeyPrincipal;
import io.modelcontextprotocol.common.McpTransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starlight MCP 笔记工具集。
 * <p>所有工具都会基于 API Key 的分类范围与只读标记进行二次校验。</p>
 */
@Service
public class McpNoteToolService {

    private static final Logger log = LoggerFactory.getLogger(McpNoteToolService.class);

    private final McpAuthService mcpAuthService;
    private final SessionAuthService sessionAuthService;
    private final NoteService noteService;
    private final NoteSearchService noteSearchService;

    public McpNoteToolService(McpAuthService mcpAuthService,
                              SessionAuthService sessionAuthService,
                              NoteService noteService,
                              NoteSearchService noteSearchService) {
        this.mcpAuthService = mcpAuthService;
        this.sessionAuthService = sessionAuthService;
        this.noteService = noteService;
        this.noteSearchService = noteSearchService;
    }

    @McpTool(name = "starlight_list_tree", description = "查询当前 API Key 权限范围内的分类目录和笔记树结构",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public Map<String, Object> listTree(@McpArg(name = "categoryId", description = "可选。指定要查询的起始分类 ID；省略、null 或字符串 \"null\" 表示根目录") String categoryId,
                                        @McpArg(name = "depth", description = "可选。查询深度，默认 2，最小为 0") Integer depth,
                                        McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requirePrincipal(transportContext);
        String normalizedCategoryId = NoteService.normalizeNullableCategoryId(categoryId);
        if (normalizedCategoryId != null) {
            noteService.getOwnedCategory(principal.ownerId(), normalizedCategoryId);
            mcpAuthService.assertCategoryAccessible(principal, normalizedCategoryId);
        }
        int safeDepth = depth == null ? 2 : Math.max(depth, 0);
        log.debug("MCP 查询目录树: apiKeyId={}, ownerId={}", principal.apiKeyId(), principal.ownerId());
        Map<String, Object> result = new java.util.LinkedHashMap<>(noteService.buildTree(
                principal.ownerId(),
                principal.allowAllCategories() ? null : principal.accessibleCategoryIds(),
                normalizedCategoryId,
                safeDepth));
        result.put("categoryId", normalizedCategoryId);
        result.put("depth", safeDepth);
        return result;
    }

    @McpTool(name = "starlight_create_category", description = "创建分类目录",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public Map<String, Object> createCategory(@McpArg(name = "name", description = "分类名称", required = true) String name,
                                              @McpArg(name = "parentId", description = "父分类 ID，可为空") String parentId,
                                              McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requireWritable(transportContext);
        mcpAuthService.assertCategoryAccessible(principal, parentId);
        UserAccount owner = sessionAuthService.findUserById(principal.ownerId());
        var category = noteService.createCategory(owner, name, parentId);
        return categoryResult(category);
    }

    @McpTool(name = "starlight_update_category", description = "修改分类名称或移动分类位置",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public Map<String, Object> updateCategory(@McpArg(name = "categoryId", description = "分类 ID", required = true) String categoryId,
                                              @McpArg(name = "name", description = "新的分类名称", required = true) String name,
                                              @McpArg(name = "parentId", description = "新的父分类 ID，可为空") String parentId,
                                              McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requireWritable(transportContext);
        mcpAuthService.assertCategoryAccessible(principal, categoryId);
        mcpAuthService.assertCategoryAccessible(principal, parentId);
        UserAccount owner = sessionAuthService.findUserById(principal.ownerId());
        var category = noteService.updateCategory(owner, categoryId, name, parentId);
        return categoryResult(category);
    }

    @McpTool(name = "starlight_delete_category", description = "删除一个空分类目录",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public Map<String, Object> deleteCategory(@McpArg(name = "categoryId", description = "分类 ID", required = true) String categoryId,
                                              McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requireWritable(transportContext);
        mcpAuthService.assertCategoryAccessible(principal, categoryId);
        noteService.deleteCategory(principal.ownerId(), categoryId);
        return Map.of("success", true, "categoryId", categoryId);
    }

    @McpTool(name = "starlight_create_note", description = "创建笔记",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public Map<String, Object> createNote(@McpArg(name = "title", description = "笔记标题，可为空") String title,
                                          @McpArg(name = "markdownContent", description = "Markdown 内容", required = true) String markdownContent,
                                          @McpArg(name = "categoryId", description = "分类 ID，可为空") String categoryId,
                                          McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requireWritable(transportContext);
        mcpAuthService.assertCategoryAccessible(principal, categoryId);
        UserAccount owner = sessionAuthService.findUserById(principal.ownerId());
        Note note = noteService.createNote(owner, title, markdownContent, categoryId);
        return noteService.toDetail(note);
    }

    @McpTool(name = "starlight_update_note", description = "更新笔记标题、内容或所属分类",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public Map<String, Object> updateNote(@McpArg(name = "noteId", description = "笔记 ID", required = true) String noteId,
                                          @McpArg(name = "title", description = "新的标题，可为空") String title,
                                          @McpArg(name = "markdownContent", description = "新的 Markdown 内容", required = true) String markdownContent,
                                          @McpArg(name = "categoryId", description = "新的分类 ID，可为空") String categoryId,
                                          McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requireWritable(transportContext);
        Note existing = noteService.getOwnedNote(principal.ownerId(), noteId);
        mcpAuthService.assertNoteAccessible(principal, existing.getCategory() == null ? null : existing.getCategory().getId());
        mcpAuthService.assertCategoryAccessible(principal, categoryId);
        UserAccount owner = sessionAuthService.findUserById(principal.ownerId());
        Note note = noteService.updateNote(owner, noteId, title, markdownContent, categoryId);
        return noteService.toDetail(note);
    }

    @McpTool(name = "starlight_delete_note", description = "将笔记移入回收站",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public Map<String, Object> deleteNote(@McpArg(name = "noteId", description = "笔记 ID", required = true) String noteId,
                                          McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requireWritable(transportContext);
        Note existing = noteService.getOwnedNote(principal.ownerId(), noteId);
        mcpAuthService.assertNoteAccessible(principal, existing.getCategory() == null ? null : existing.getCategory().getId());
        noteService.deleteNote(principal.ownerId(), noteId);
        return Map.of("success", true, "noteId", noteId);
    }

    @McpTool(name = "starlight_get_note_content", description = "根据笔记 ID 查询完整笔记内容",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public Map<String, Object> getNoteContent(@McpArg(name = "noteId", description = "笔记 ID", required = true) String noteId,
                                              McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requirePrincipal(transportContext);
        Note existing = noteService.getOwnedNote(principal.ownerId(), noteId);
        mcpAuthService.assertNoteAccessible(principal, existing.getCategory() == null ? null : existing.getCategory().getId());
        return noteContentResult(existing);
    }

    @McpTool(name = "starlight_search_note_content", description = "在权限范围内全文搜索笔记内容，返回笔记 ID、标题和命中摘要",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public Map<String, Object> searchNoteContent(@McpArg(name = "keyword", description = "搜索关键词", required = true) String keyword,
                                                 @McpArg(name = "offset", description = "分页偏移量，可选") Integer offset,
                                                 @McpArg(name = "limit", description = "分页大小，可选，最大 50") Integer limit,
                                                 McpTransportContext transportContext) {
        McpApiKeyPrincipal principal = mcpAuthService.requirePrincipal(transportContext);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return Map.of("items", List.of(), "hasMore", false);
        }
        int safeOffset = Math.max(offset == null ? 0 : offset, 0);
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 50));
        Set<String> allowedCategoryIds = principal.allowAllCategories() ? null : principal.accessibleCategoryIds();
        List<Map<String, Object>> results = noteSearchService.search(
                principal.ownerId(), normalizedKeyword, safeOffset, safeLimit + 1, allowedCategoryIds);
        boolean hasMore = results.size() > safeLimit;
        List<Map<String, Object>> page = hasMore ? results.subList(0, safeLimit) : results;
        return Map.of("items", page, "hasMore", hasMore);
    }

    private Map<String, Object> categoryResult(cn.suhoan.starlight.entity.Category category) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        map.put("parentId", category.getParent() == null ? null : category.getParent().getId());
        return map;
    }

    private Map<String, Object> noteContentResult(Note note) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", note.getId());
        map.put("title", note.getTitle());
        map.put("markdownContent", note.getMarkdownContent());
        map.put("outlineJson", note.getOutlineJson());
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("createdAt", note.getCreatedAt());
        map.put("deletedAt", note.getDeletedAt());
        map.put("pinnedFlag", note.isPinnedFlag());
        return map;
    }
}

