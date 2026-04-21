package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.repository.NoteRepository;
import cn.suhoan.starlight.support.McpApiKeyPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * MCP 目录范围服务。
 * <p>负责把 API Key 的授权目录转换为 MCP 可见的“权限根目录”，
 * 并构建适用于 MCP 的树形结果，避免把真实根目录直接暴露给受限 API Key。</p>
 */
@Service
@Transactional(readOnly = true)
public class McpScopeService {

    /** 多授权根场景下使用的虚拟根目录 ID。 */
    public static final String VIRTUAL_ROOT_CATEGORY_ID = "__mcp_virtual_root__";
    private static final String VIRTUAL_ROOT_NAME = "已授权目录";
    private static final Comparator<Note> PINNED_NOTE_COMPARATOR = Comparator
            .comparing(Note::getPinnedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Note::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER);

    private final CategoryAccessService categoryAccessService;
    private final NoteRepository noteRepository;

    public McpScopeService(CategoryAccessService categoryAccessService, NoteRepository noteRepository) {
        this.categoryAccessService = categoryAccessService;
        this.noteRepository = noteRepository;
    }

    /**
     * 构建 MCP 专用目录树。
     * <p>与 Web 端树不同，这里会把受限 API Key 的根目录映射到其权限根，
     * 并在深度不足时保留下一层子目录的基础元信息，提示 AI 继续下钻。</p>
     */
    public Map<String, Object> buildScopedTree(McpApiKeyPrincipal principal, String categoryId, Integer depth) {
        List<Category> allCategories = categoryAccessService.listUserCategories(principal.ownerId());
        Map<String, Category> categoryById = new LinkedHashMap<>();
        for (Category category : allCategories) {
            categoryById.put(category.getId(), category);
        }

        Map<String, List<String>> allChildrenMap = buildChildrenMap(allCategories);
        Set<String> accessibleCategoryIds = principal.allowAllCategories()
                ? new LinkedHashSet<>(categoryById.keySet())
                : new LinkedHashSet<>(principal.accessibleCategoryIds());
        Map<String, List<String>> accessibleChildrenMap = buildAccessibleChildrenMap(accessibleCategoryIds, allChildrenMap);
        List<Note> accessibleNotes = filterAccessibleNotes(principal, noteRepository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(principal.ownerId()));
        Map<String, List<Note>> accessibleNotesByCategory = groupNotesByCategory(accessibleNotes);

        ScopeRootDescriptor rootDescriptor = resolveScopeRoot(principal, categoryById);
        QueryTarget queryTarget = resolveQueryTarget(principal, rootDescriptor, categoryId, categoryById);
        int safeDepth = depth == null ? 2 : Math.max(depth, 0);

        TreeBuildContext context = new TreeBuildContext(
                categoryById,
                accessibleChildrenMap,
                accessibleNotesByCategory,
                queryTarget.overrideChildrenMap(),
                queryTarget.suppressNoteCategoryIds()
        );

        List<Map<String, Object>> items = switch (queryTarget.kind()) {
            case ROOT_VIEW -> buildRootViewItems(rootDescriptor, safeDepth, context, categoryById, principal.allowAllCategories(), accessibleNotesByCategory);
            case CATEGORY_VIEW -> List.of(buildCategoryNode(queryTarget.category().getId(), safeDepth, context));
        };

        List<Map<String, Object>> pinnedItems = buildPinnedItems(queryTarget, rootDescriptor, accessibleChildrenMap, accessibleNotes, principal.allowAllCategories());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryId", queryTarget.currentCategoryId());
        result.put("rootCategoryId", rootDescriptor.rootCategoryId());
        result.put("rootCategoryName", rootDescriptor.rootCategoryName());
        result.put("virtualRoot", rootDescriptor.virtualRoot());
        result.put("scopeHints", buildScopeHints(principal, queryTarget, rootDescriptor, context));
        result.put("depth", safeDepth);
        result.put("items", items);
        result.put("pinnedItems", pinnedItems);
        result.put("trashCount", principal.allowAllCategories()
                ? noteRepository.countByOwnerIdAndDeletedAtIsNotNull(principal.ownerId())
                : 0);
        return result;
    }

    /**
     * 断言分类可作为 MCP 写操作目标。
     * <p>与普通浏���不同，受限 API Key 不允许把目标分类写到真实根目录或权限容器目录。</p>
     */
    public void assertWritableCategoryAccessible(McpApiKeyPrincipal principal, String categoryId) {
        String normalizedCategoryId = NoteService.normalizeNullableCategoryId(categoryId);
        if (principal.allowAllCategories()) {
            return;
        }
        if (normalizedCategoryId == null || !principal.accessibleCategoryIds().contains(normalizedCategoryId)) {
            throw new ResponseStatusException(FORBIDDEN, "当前 API Key 无权在该位置执行写操作");
        }
    }

    private QueryTarget resolveQueryTarget(McpApiKeyPrincipal principal,
                                           ScopeRootDescriptor rootDescriptor,
                                           String requestedCategoryId,
                                           Map<String, Category> categoryById) {
        String normalizedCategoryId = NoteService.normalizeNullableCategoryId(requestedCategoryId);
        if (principal.allowAllCategories()) {
            if (normalizedCategoryId == null) {
                return QueryTarget.rootView(null, Map.of(), Set.of());
            }
            Category category = categoryById.get(normalizedCategoryId);
            if (category == null) {
                throw new ResponseStatusException(NOT_FOUND, "分类不存在");
            }
            return QueryTarget.categoryView(category, normalizedCategoryId, Map.of(), Set.of());
        }

        if (normalizedCategoryId == null || VIRTUAL_ROOT_CATEGORY_ID.equals(normalizedCategoryId)) {
            if (rootDescriptor.singleScopeRoot()) {
                return QueryTarget.rootView(rootDescriptor.rootCategoryId(), Map.of(), Set.of());
            }
            return QueryTarget.rootView(rootDescriptor.rootCategoryId(), Map.of(), Set.of());
        }

        if (rootDescriptor.singleScopeRoot() && normalizedCategoryId.equals(rootDescriptor.rootCategoryId())) {
            return QueryTarget.rootView(rootDescriptor.rootCategoryId(), Map.of(), Set.of());
        }

        if (rootDescriptor.virtualRoot()) {
            if (principal.accessibleCategoryIds().contains(normalizedCategoryId)) {
                Category category = categoryById.get(normalizedCategoryId);
                if (category == null) {
                    throw new ResponseStatusException(NOT_FOUND, "分类不存在");
                }
                return QueryTarget.categoryView(category, normalizedCategoryId, Map.of(), Set.of());
            }
            throw new ResponseStatusException(FORBIDDEN, "当前 API Key 无权访问该分类");
        }

        if (normalizedCategoryId.equals(rootDescriptor.rootCategoryId()) && !principal.accessibleCategoryIds().contains(normalizedCategoryId)) {
            Category category = categoryById.get(normalizedCategoryId);
            if (category == null) {
                throw new ResponseStatusException(NOT_FOUND, "分类不存在");
            }
            Map<String, List<String>> overrideChildrenMap = Map.of(category.getId(), rootDescriptor.rootEntryCategoryIds());
            Set<String> suppressNoteCategoryIds = Set.of(category.getId());
            return QueryTarget.categoryView(category, normalizedCategoryId, overrideChildrenMap, suppressNoteCategoryIds);
        }

        if (!principal.accessibleCategoryIds().contains(normalizedCategoryId)) {
            throw new ResponseStatusException(FORBIDDEN, "当前 API Key 无权访问该分类");
        }
        Category category = categoryById.get(normalizedCategoryId);
        if (category == null) {
            throw new ResponseStatusException(NOT_FOUND, "分类不存在");
        }
        return QueryTarget.categoryView(category, normalizedCategoryId, Map.of(), Set.of());
    }

    private ScopeRootDescriptor resolveScopeRoot(McpApiKeyPrincipal principal, Map<String, Category> categoryById) {
        if (principal.allowAllCategories()) {
            return new ScopeRootDescriptor(null, null, false, "real_root", "全部笔记", List.of());
        }
        List<Category> scopeRoots = principal.scopeRootCategoryIds().stream()
                .map(categoryById::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Category::getId))
                .toList();
        if (scopeRoots.isEmpty()) {
            return new ScopeRootDescriptor(VIRTUAL_ROOT_CATEGORY_ID, null, true, "virtual_root", VIRTUAL_ROOT_NAME, List.of());
        }
        if (scopeRoots.size() == 1) {
            Category root = scopeRoots.getFirst();
            return new ScopeRootDescriptor(root.getId(), root.getId(), false, "single_scope_root", root.getName(), List.of(root.getId()));
        }
        Set<String> parentIds = new LinkedHashSet<>();
        for (Category category : scopeRoots) {
            parentIds.add(category.getParent() == null ? null : category.getParent().getId());
        }
        if (parentIds.size() == 1) {
            String parentId = parentIds.iterator().next();
            if (parentId != null) {
                Category parent = categoryById.get(parentId);
                if (parent != null) {
                    return new ScopeRootDescriptor(parent.getId(), parent.getId(), false, "shared_parent", parent.getName(),
                            scopeRoots.stream().map(Category::getId).toList());
                }
            }
        }
        return new ScopeRootDescriptor(VIRTUAL_ROOT_CATEGORY_ID, null, true, "virtual_root", VIRTUAL_ROOT_NAME,
                scopeRoots.stream().map(Category::getId).toList());
    }

    private Map<String, Object> buildScopeHints(McpApiKeyPrincipal principal,
                                                QueryTarget queryTarget,
                                                ScopeRootDescriptor rootDescriptor,
                                                TreeBuildContext context) {
        boolean currentNodeVirtualRoot = queryTarget.kind() == QueryKind.ROOT_VIEW && rootDescriptor.virtualRoot();
        boolean scopeBoundaryContainer = isScopeBoundaryContainer(principal, queryTarget, rootDescriptor);
        List<String> nextQueryCategoryIds = listNextQueryCategoryIds(queryTarget, rootDescriptor, context, principal.allowAllCategories());

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("rootMode", resolveRootMode(principal, rootDescriptor));
        hints.put("currentView", queryTarget.kind() == QueryKind.ROOT_VIEW ? "root" : "category");
        hints.put("currentNodeVirtualRoot", currentNodeVirtualRoot);
        hints.put("scopeBoundaryContainer", scopeBoundaryContainer);
        hints.put("scopeBoundaryType", scopeBoundaryContainer
                ? (currentNodeVirtualRoot ? "virtual_root" : "shared_parent")
                : "none");
        hints.put("recommendedNextAction", nextQueryCategoryIds.isEmpty() ? "read_note_or_search" : "query_child_category");
        hints.put("nextQueryCategoryIds", nextQueryCategoryIds);
        return hints;
    }

    private List<Map<String, Object>> buildRootViewItems(ScopeRootDescriptor rootDescriptor,
                                                         int safeDepth,
                                                         TreeBuildContext context,
                                                         Map<String, Category> categoryById,
                                                         boolean allowAllCategories,
                                                         Map<String, List<Note>> accessibleNotesByCategory) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (allowAllCategories) {
            List<String> topLevelCategoryIds = new ArrayList<>();
            for (Category category : categoryById.values()) {
                if (category.getParent() == null) {
                    topLevelCategoryIds.add(category.getId());
                }
            }
            topLevelCategoryIds.sort(Comparator.comparing(id -> categoryById.get(id).getName(), String.CASE_INSENSITIVE_ORDER));
            for (String categoryId : topLevelCategoryIds) {
                items.add(buildCategoryNode(categoryId, safeDepth - 1, context));
            }
            for (Note note : sortNotes(accessibleNotesByCategory.getOrDefault(null, List.of()))) {
                if (!note.isPinnedFlag()) {
                    items.add(toTreeNote(note));
                }
            }
            items.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
            return items;
        }

        if (rootDescriptor.singleScopeRoot()) {
            return buildCategoryContentItems(rootDescriptor.rootCategoryId(), safeDepth, context);
        }

        List<String> rootEntryCategoryIds = new ArrayList<>(rootDescriptor.rootEntryCategoryIds());
        rootEntryCategoryIds.sort(Comparator.comparing(id -> categoryById.get(id).getName(), String.CASE_INSENSITIVE_ORDER));
        for (String categoryId : rootEntryCategoryIds) {
            items.add(buildCategoryNode(categoryId, safeDepth - 1, context));
        }
        return items;
    }

    private boolean isScopeBoundaryContainer(McpApiKeyPrincipal principal,
                                             QueryTarget queryTarget,
                                             ScopeRootDescriptor rootDescriptor) {
        if (principal.allowAllCategories()) {
            return false;
        }
        if (queryTarget.kind() == QueryKind.ROOT_VIEW) {
            return !rootDescriptor.singleScopeRoot();
        }
        return queryTarget.category() != null && !principal.accessibleCategoryIds().contains(queryTarget.category().getId())
                && rootDescriptor.rootCategoryId() != null
                && rootDescriptor.rootCategoryId().equals(queryTarget.category().getId());
    }

    private String resolveRootMode(McpApiKeyPrincipal principal, ScopeRootDescriptor rootDescriptor) {
        if (principal.allowAllCategories()) {
            return "real_root";
        }
        return rootDescriptor.rootMode();
    }

    private List<String> listNextQueryCategoryIds(QueryTarget queryTarget,
                                                  ScopeRootDescriptor rootDescriptor,
                                                  TreeBuildContext context,
                                                  boolean allowAllCategories) {
        if (queryTarget.kind() == QueryKind.ROOT_VIEW) {
            if (allowAllCategories) {
                List<String> topLevelCategoryIds = context.categoryById().values().stream()
                        .filter(category -> category.getParent() == null)
                        .map(Category::getId)
                        .toList();
                return sortCategoryIds(topLevelCategoryIds, context.categoryById());
            }
            if (rootDescriptor.singleScopeRoot()) {
                return sortCategoryIds(
                        context.accessibleChildrenMap().getOrDefault(rootDescriptor.rootCategoryId(), List.of()),
                        context.categoryById()
                );
            }
            return sortCategoryIds(rootDescriptor.rootEntryCategoryIds(), context.categoryById());
        }
        List<String> directChildIds = queryTarget.overrideChildrenMap().getOrDefault(
                queryTarget.category().getId(),
                context.accessibleChildrenMap().getOrDefault(queryTarget.category().getId(), List.of())
        );
        return sortCategoryIds(directChildIds, context.categoryById());
    }

    private List<String> sortCategoryIds(Collection<String> categoryIds, Map<String, Category> categoryById) {
        return categoryIds.stream()
                .filter(categoryById::containsKey)
                .sorted(Comparator.comparing(id -> categoryById.get(id).getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(String::valueOf))
                .toList();
    }

    /**
     * 将“单授权根分类”作为 MCP 逻辑根目录时，直接返回该分类下的子分类与笔记，
     * 避免外层再包一层同名分类节点，减少客户端处理复杂度。
     */
    private List<Map<String, Object>> buildCategoryContentItems(String categoryId,
                                                                int safeDepth,
                                                                TreeBuildContext context) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> directChildIds = new ArrayList<>(context.accessibleChildrenMap().getOrDefault(categoryId, List.of()));
        directChildIds.sort(Comparator.comparing(id -> context.categoryById().get(id).getName(), String.CASE_INSENSITIVE_ORDER));
        for (String childId : directChildIds) {
            items.add(buildCategoryNode(childId, safeDepth - 1, context));
        }
        for (Note note : sortNotes(context.accessibleNotesByCategory().getOrDefault(categoryId, List.of()))) {
            if (!note.isPinnedFlag()) {
                items.add(toTreeNote(note));
            }
        }
        return items;
    }

    private Map<String, Object> buildCategoryNode(String categoryId, int remainingDepth, TreeBuildContext context) {
        Category category = context.categoryById().get(categoryId);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", category.getId());
        node.put("name", category.getName());
        node.put("type", "category");
        node.put("siteToken", category.getSiteToken());
        node.put("parentId", category.getParent() == null ? null : category.getParent().getId());
        node.put("children", new ArrayList<Map<String, Object>>());

        List<String> directChildIds = new ArrayList<>(context.overrideChildrenMap().getOrDefault(
                categoryId,
                context.accessibleChildrenMap().getOrDefault(categoryId, List.of())
        ));
        directChildIds.sort(Comparator.comparing(id -> context.categoryById().get(id).getName(), String.CASE_INSENSITIVE_ORDER));
        List<Note> directNotes = context.suppressNoteCategoryIds().contains(categoryId)
                ? List.of()
                : sortNotes(context.accessibleNotesByCategory().getOrDefault(categoryId, List.of()));

        node.put("childCategoryCount", directChildIds.size());
        node.put("childNoteCount", directNotes.size());
        node.put("hasChildren", !directChildIds.isEmpty() || !directNotes.isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (remainingDepth >= 0) {
            for (String childId : directChildIds) {
                children.add(buildCategoryNode(childId, remainingDepth - 1, context));
            }
            for (Note note : directNotes) {
                if (!note.isPinnedFlag()) {
                    children.add(toTreeNote(note));
                }
            }
        } else if (!directChildIds.isEmpty() || !directNotes.isEmpty()) {
            node.put("childrenTruncated", true);
        }
        return node;
    }

    private List<Map<String, Object>> buildPinnedItems(QueryTarget queryTarget,
                                                       ScopeRootDescriptor rootDescriptor,
                                                       Map<String, List<String>> accessibleChildrenMap,
                                                       List<Note> accessibleNotes,
                                                       boolean allowAllCategories) {
        Set<String> includedCategoryIds = new LinkedHashSet<>();
        boolean includeRootNotes = false;
        if (queryTarget.kind() == QueryKind.ROOT_VIEW) {
            if (allowAllCategories) {
                includeRootNotes = true;
                includedCategoryIds.addAll(accessibleChildrenMap.keySet());
                for (List<String> childIds : accessibleChildrenMap.values()) {
                    includedCategoryIds.addAll(childIds);
                }
            } else {
                List<String> startIds = rootDescriptor.singleScopeRoot()
                        ? List.of(rootDescriptor.rootCategoryId())
                        : rootDescriptor.rootEntryCategoryIds();
                includedCategoryIds.addAll(collectDescendantIds(startIds, accessibleChildrenMap));
            }
        } else if (queryTarget.overrideChildrenMap().isEmpty()) {
            includedCategoryIds.addAll(collectDescendantIds(List.of(queryTarget.category().getId()), accessibleChildrenMap));
        } else {
            includedCategoryIds.addAll(collectDescendantIds(rootDescriptor.rootEntryCategoryIds(), accessibleChildrenMap));
        }
        final boolean includeRootNotesFinal = includeRootNotes;
        return accessibleNotes.stream()
                .filter(Note::isPinnedFlag)
                .filter(note -> includePinnedNote(note, includedCategoryIds, includeRootNotesFinal))
                .sorted(PINNED_NOTE_COMPARATOR)
                .map(this::toTreeNote)
                .toList();
    }

    private boolean includePinnedNote(Note note, Set<String> includedCategoryIds, boolean includeRootNotes) {
        if (note.getCategory() == null) {
            return includeRootNotes;
        }
        return includedCategoryIds.contains(note.getCategory().getId());
    }

    private Set<String> collectDescendantIds(Collection<String> startIds, Map<String, List<String>> childrenMap) {
        Set<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String startId : startIds) {
            if (startId != null && result.add(startId)) {
                queue.add(startId);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String childId : childrenMap.getOrDefault(current, List.of())) {
                if (result.add(childId)) {
                    queue.addLast(childId);
                }
            }
        }
        return result;
    }

    private Map<String, List<String>> buildChildrenMap(Collection<Category> categories) {
        Map<String, List<String>> childrenMap = new LinkedHashMap<>();
        for (Category category : categories) {
            if (category.getParent() == null) {
                continue;
            }
            childrenMap.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>())
                    .add(category.getId());
        }
        return childrenMap;
    }

    private Map<String, List<String>> buildAccessibleChildrenMap(Set<String> accessibleCategoryIds,
                                                                 Map<String, List<String>> allChildrenMap) {
        Map<String, List<String>> accessibleChildrenMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : allChildrenMap.entrySet()) {
            List<String> childIds = entry.getValue().stream()
                    .filter(accessibleCategoryIds::contains)
                    .toList();
            if (!childIds.isEmpty() && accessibleCategoryIds.contains(entry.getKey())) {
                accessibleChildrenMap.put(entry.getKey(), childIds);
            }
        }
        return accessibleChildrenMap;
    }

    private List<Note> filterAccessibleNotes(McpApiKeyPrincipal principal, List<Note> notes) {
        if (principal.allowAllCategories()) {
            return notes;
        }
        return notes.stream()
                .filter(note -> note.getCategory() != null)
                .filter(note -> principal.accessibleCategoryIds().contains(note.getCategory().getId()))
                .toList();
    }

    private Map<String, List<Note>> groupNotesByCategory(List<Note> notes) {
        Map<String, List<Note>> notesByCategory = new HashMap<>();
        for (Note note : notes) {
            String categoryId = note.getCategory() == null ? null : note.getCategory().getId();
            notesByCategory.computeIfAbsent(categoryId, ignored -> new ArrayList<>()).add(note);
        }
        return notesByCategory;
    }

    private List<Note> sortNotes(List<Note> notes) {
        List<Note> result = new ArrayList<>(notes);
        result.sort(Comparator.comparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Note::getId));
        return result;
    }

    private Map<String, Object> toTreeNote(Note note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", note.getId());
        map.put("name", note.getTitle());
        map.put("type", "note");
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("pinnedFlag", note.isPinnedFlag());
        return map;
    }

    private record ScopeRootDescriptor(String rootCategoryId,
                                       String actualCategoryId,
                                       boolean virtualRoot,
                                       String rootMode,
                                       String rootCategoryName,
                                       List<String> rootEntryCategoryIds) {

        private boolean singleScopeRoot() {
            return "single_scope_root".equals(rootMode);
        }
    }

    private record TreeBuildContext(Map<String, Category> categoryById,
                                    Map<String, List<String>> accessibleChildrenMap,
                                    Map<String, List<Note>> accessibleNotesByCategory,
                                    Map<String, List<String>> overrideChildrenMap,
                                    Set<String> suppressNoteCategoryIds) {
    }

    private record QueryTarget(QueryKind kind,
                               String currentCategoryId,
                               Category category,
                               Map<String, List<String>> overrideChildrenMap,
                               Set<String> suppressNoteCategoryIds) {

        private static QueryTarget rootView(String currentCategoryId,
                                            Map<String, List<String>> overrideChildrenMap,
                                            Set<String> suppressNoteCategoryIds) {
            return new QueryTarget(QueryKind.ROOT_VIEW, currentCategoryId, null, overrideChildrenMap, suppressNoteCategoryIds);
        }

        private static QueryTarget categoryView(Category category,
                                                String currentCategoryId,
                                                Map<String, List<String>> overrideChildrenMap,
                                                Set<String> suppressNoteCategoryIds) {
            return new QueryTarget(QueryKind.CATEGORY_VIEW, currentCategoryId, category, overrideChildrenMap, suppressNoteCategoryIds);
        }
    }

    private enum QueryKind {
        ROOT_VIEW,
        CATEGORY_VIEW
    }
}

