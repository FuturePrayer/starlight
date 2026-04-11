package cn.suhoan.starlight.service;

import cn.suhoan.starlight.config.StarlightProperties;
import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.entity.UserAccount;
import cn.suhoan.starlight.repository.CategoryRepository;
import cn.suhoan.starlight.repository.NoteRepository;
import cn.suhoan.starlight.repository.NoteShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 笔记服务。
 * <p>处理笔记和分类的 CRUD 操作，以及笔记树形结构的构建。</p>
 *
 * @author suhoan
 */
@Service
@Transactional
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);
    private static final Comparator<Note> PINNED_NOTE_COMPARATOR = Comparator
            .comparing(Note::getPinnedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Note::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER);

    private final NoteRepository noteRepository;
    private final CategoryRepository categoryRepository;
    private final NoteShareRepository noteShareRepository;
    private final MarkdownService markdownService;
    private final StarlightProperties starlightProperties;

    public NoteService(NoteRepository noteRepository,
                       CategoryRepository categoryRepository,
                       NoteShareRepository noteShareRepository,
                       MarkdownService markdownService,
                       StarlightProperties starlightProperties) {
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
        this.noteShareRepository = noteShareRepository;
        this.markdownService = markdownService;
        this.starlightProperties = starlightProperties;
    }

    /**
     * 创建新笔记。
     * <p>自动渲染 Markdown 为 HTML，生成大纲 JSON 和纯文本索引。</p>
     *
     * @param owner           笔记所有者
     * @param title           笔记标题（可为空，自动从内容提取）
     * @param markdownContent Markdown 原始内容
     * @param categoryId      所属分类 ID（可为空）
     * @return 新创建的笔记
     */
    public Note createNote(UserAccount owner, String title, String markdownContent, String categoryId) {
        Note note = new Note();
        note.setOwner(owner);
        note.setTitle(normalizeTitle(title, markdownContent));
        note.setMarkdownContent(markdownContent == null ? "" : markdownContent);
        note.setRenderedHtml(markdownService.renderToHtml(note.getMarkdownContent()));
        note.setOutlineJson(markdownService.buildOutlineJson(note.getMarkdownContent()));
        note.setPlainText(markdownService.stripToPlainText(note.getMarkdownContent()));
        note.setCategory(resolveCategory(owner.getId(), categoryId));
        Note saved = noteRepository.save(note);
        log.info("笔记创建成功: noteId={}, ownerId={}, title={}", saved.getId(), owner.getId(), saved.getTitle());
        return saved;
    }

    /**
     * 更新笔记内容。
     * <p>重新渲染 Markdown 并更新搜索索引。</p>
     *
     * @param owner           当前用户（必须是笔记所有者）
     * @param noteId          笔记 ID
     * @param title           新标题
     * @param markdownContent 新的 Markdown 内容
     * @param categoryId      新的分类 ID
     * @return 更新后的笔记
     */
    public Note updateNote(UserAccount owner, String noteId, String title, String markdownContent, String categoryId) {
        Note note = getOwnedNote(owner.getId(), noteId);
        note.setTitle(normalizeTitle(title, markdownContent));
        note.setMarkdownContent(markdownContent == null ? "" : markdownContent);
        note.setRenderedHtml(markdownService.renderToHtml(note.getMarkdownContent()));
        note.setOutlineJson(markdownService.buildOutlineJson(note.getMarkdownContent()));
        note.setPlainText(markdownService.stripToPlainText(note.getMarkdownContent()));
        note.setCategory(resolveCategory(owner.getId(), categoryId));
        Note saved = noteRepository.save(note);
        log.info("笔记更新成功: noteId={}, ownerId={}", saved.getId(), owner.getId());
        return saved;
    }

    /**
     * 获取指定用户拥有的笔记，如不存在则抛出 404 异常。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @return 笔记实体
     * @throws ResponseStatusException 当笔记不存在或不属于该用户时
     */
    @Transactional(readOnly = true)
    public Note getOwnedNote(String ownerId, String noteId) {
        return noteRepository.findByIdAndOwnerIdAndDeletedAtIsNull(noteId, ownerId)
                .orElseThrow(() -> {
                    log.warn("笔记未找到或不属于该用户: noteId={}, ownerId={}", noteId, ownerId);
                    return new ResponseStatusException(NOT_FOUND, "笔记不存在");
                });
    }

    /**
     * 获取指定用户拥有的笔记（包含回收站内容）。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @return 笔记实体
     */
    @Transactional(readOnly = true)
    public Note getOwnedNoteIncludingDeleted(String ownerId, String noteId) {
        return noteRepository.findByIdAndOwnerId(noteId, ownerId)
                .orElseThrow(() -> {
                    log.warn("笔记未找到或不属于该用户（含回收站）: noteId={}, ownerId={}", noteId, ownerId);
                    return new ResponseStatusException(NOT_FOUND, "笔记不存在");
                });
    }

    /**
     * 获取指定用户回收站中的笔记。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @return 回收站中的笔记实体
     */
    @Transactional(readOnly = true)
    public Note getOwnedTrashNote(String ownerId, String noteId) {
        return noteRepository.findByIdAndOwnerIdAndDeletedAtIsNotNull(noteId, ownerId)
                .orElseThrow(() -> {
                    log.warn("回收站笔记未找到: noteId={}, ownerId={}", noteId, ownerId);
                    return new ResponseStatusException(NOT_FOUND, "回收站中不存在该笔记");
                });
    }

    /**
     * 获取指定用户的所有笔记摘要列表，按更新时间倒序排列。
     *
     * @param ownerId 用户 ID
     * @return 笔记摘要列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUserNotes(String ownerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Note note : noteRepository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId)) {
            result.add(toSummary(note));
        }
        return result;
    }

    /**
     * 获取当前用户回收站中的笔记列表。
     *
     * @param ownerId 用户 ID
     * @return 回收站摘要列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTrashNotes(String ownerId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Note note : noteRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(ownerId)) {
            result.add(toSummary(note));
        }
        return result;
    }

    /**
     * 获取笔记详情。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @return 笔记详情 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getNoteDetail(String ownerId, String noteId) {
        return toDetail(getOwnedNote(ownerId, noteId));
    }

    /**
     * 获取回收站中笔记的详情。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @return 回收站笔记详情
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrashNoteDetail(String ownerId, String noteId) {
        return toDetail(getOwnedTrashNote(ownerId, noteId));
    }

    /**
     * 删除笔记。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     */
    public void deleteNote(String ownerId, String noteId) {
        Note note = getOwnedNote(ownerId, noteId);
        LocalDateTime deletedAt = LocalDateTime.now();
        note.setDeletedAt(deletedAt);
        noteRepository.save(note);
        log.info("笔记已移入回收站: noteId={}, ownerId={}, purgeAt={}", noteId, ownerId, calculatePurgeAt(deletedAt));
    }

    /**
     * 恢复回收站中的笔记。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @return 恢复后的笔记
     */
    public Note restoreNote(String ownerId, String noteId) {
        Note note = getOwnedTrashNote(ownerId, noteId);
        note.setDeletedAt(null);
        Note saved = noteRepository.save(note);
        log.info("笔记已从回收站恢复: noteId={}, ownerId={}", noteId, ownerId);
        return saved;
    }

    /**
     * 彻底删除回收站中的笔记。
     * <p>会同时移除关联分享记录，避免外键约束失败。</p>
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     */
    public void purgeNote(String ownerId, String noteId) {
        Note note = getOwnedTrashNote(ownerId, noteId);
        long shareCount = noteShareRepository.countByNoteId(noteId);
        if (shareCount > 0) {
            noteShareRepository.deleteByNoteId(noteId);
            noteShareRepository.flush();
        }
        noteRepository.delete(note);
        log.info("回收站笔记已彻底删除: noteId={}, ownerId={}, removedShareCount={}", noteId, ownerId, shareCount);
    }

    /**
     * 更新笔记置顶状态。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @param pinned  是否置顶
     * @return 更新后的笔记
     */
    public Note setPinned(String ownerId, String noteId, boolean pinned) {
        Note note = getOwnedNote(ownerId, noteId);
        note.setPinnedFlag(pinned);
        note.setPinnedAt(pinned ? LocalDateTime.now() : null);
        Note saved = noteRepository.save(note);
        log.info("笔记置顶状态已更新: noteId={}, ownerId={}, pinned={}", noteId, ownerId, pinned);
        return saved;
    }

    /**
     * 构建用户的笔记树形结构。
     * <p>将分类和笔记组装成前端可渲染的树形结构，根节点按名称排序。</p>
     *
     * @param ownerId 用户 ID
     * @return 包含树形结构数据的 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildTree(String ownerId) {
        return buildTree(ownerId, null);
    }

    /**
     * 构建指定分类权限范围内的树形结构。
     * <p>当 accessibleCategoryIds 为 null 时表示不限制分类范围。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildTree(String ownerId, Set<String> accessibleCategoryIds) {
        return buildTree(ownerId, accessibleCategoryIds, null, Integer.MAX_VALUE);
    }

    /**
     * 构建指定分类权限范围与深度限制下的树形结构。
     * <p>当 rootCategoryId 为空时表示查询根目录；当 depth 为 null 时表示不限制深度。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildTree(String ownerId,
                                         Set<String> accessibleCategoryIds,
                                         String rootCategoryId,
                                         Integer depth) {
        List<Category> allCategories = categoryRepository.findByOwnerIdOrderByNameAsc(ownerId);
        List<Note> allNotes = noteRepository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId);

        String normalizedRootCategoryId = normalizeNullableCategoryId(rootCategoryId);
        int safeDepth = depth == null ? Integer.MAX_VALUE : Math.max(depth, 0);

        boolean restricted = accessibleCategoryIds != null;
        List<Category> visibleCategories = restricted
                ? allCategories.stream().filter(category -> accessibleCategoryIds.contains(category.getId())).toList()
                : allCategories;

        Map<String, Integer> depthMap = computeCategoryDepths(visibleCategories, normalizedRootCategoryId, safeDepth);
        Set<String> includedCategoryIds = depthMap.keySet();

        List<Category> categories = visibleCategories.stream()
                .filter(category -> includedCategoryIds.contains(category.getId()))
                .toList();
        List<Note> notes = restricted
                ? allNotes.stream()
                .filter(note -> isNoteIncluded(note, normalizedRootCategoryId, includedCategoryIds))
                .toList()
                : allNotes.stream()
                .filter(note -> isNoteIncluded(note, normalizedRootCategoryId, includedCategoryIds))
                .toList();

        Map<String, Map<String, Object>> categoryMap = new HashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();

        // 构建分类的父子关系映射，用于继承 siteToken 标记
        Map<String, String> parentIdMap = new HashMap<>();
        Map<String, String> siteTokenMap = new HashMap<>();

        for (Category category : categories) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", category.getId());
            node.put("name", category.getName());
            node.put("type", "category");
            node.put("siteToken", category.getSiteToken());
            node.put("children", new ArrayList<Map<String, Object>>());
            categoryMap.put(category.getId(), node);

            // 记录父子关系和 siteToken 信息
            if (category.getParent() != null) {
                parentIdMap.put(category.getId(), category.getParent().getId());
            }
            if (category.getSiteToken() != null) {
                siteTokenMap.put(category.getId(), category.getSiteToken());
            }
        }

        // 为每个分类计算 inheritedSiteToken（从祖先继承的站点标识）
        for (Category category : categories) {
            Map<String, Object> node = categoryMap.get(category.getId());
            if (category.getSiteToken() == null) {
                // 沿父链向上查找，看是否有祖先开启了星迹书阁
                String pid = parentIdMap.get(category.getId());
                while (pid != null) {
                    if (siteTokenMap.containsKey(pid)) {
                        node.put("inheritedSiteToken", siteTokenMap.get(pid));
                        node.put("inheritedFromId", pid);
                        break;
                    }
                    pid = parentIdMap.get(pid);
                }
            }
        }

        for (Category category : categories) {
            Map<String, Object> node = categoryMap.get(category.getId());
            if (normalizedRootCategoryId != null && normalizedRootCategoryId.equals(category.getId())) {
                roots.add(node);
                continue;
            }
            if (category.getParent() != null) {
                Map<String, Object> parent = categoryMap.get(category.getParent().getId());
                if (parent != null) {
                    castChildren(parent).add(node);
                    continue;
                }
            }
            roots.add(node);
        }

        List<Map<String, Object>> pinnedItems = notes.stream()
                .filter(Note::isPinnedFlag)
                .sorted(PINNED_NOTE_COMPARATOR)
                .map(this::toTreeNote)
                .toList();
        for (Note note : notes) {
            if (note.isPinnedFlag()) {
                continue;
            }
            Map<String, Object> node = toTreeNote(note);
            if (note.getCategory() != null) {
                Map<String, Object> categoryNode = categoryMap.get(note.getCategory().getId());
                if (categoryNode != null) {
                    castChildren(categoryNode).add(node);
                    continue;
                }
            }
            if (normalizedRootCategoryId == null) {
                roots.add(node);
            }
        }

        roots.sort(Comparator.comparing(item -> item.get("name").toString()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", roots);
        data.put("pinnedItems", pinnedItems);
        data.put("trashCount", restricted ? 0 : noteRepository.countByOwnerIdAndDeletedAtIsNotNull(ownerId));
        return data;
    }

    /** 查询当前用户拥有的分类。 */
    @Transactional(readOnly = true)
    public Category getOwnedCategory(String ownerId, String categoryId) {
        Category category = resolveCategory(ownerId, categoryId);
        if (category == null) {
            throw new ResponseStatusException(NOT_FOUND, "分类不存在");
        }
        return category;
    }

    /**
     * 定时清理超出保留期的回收站笔记。
     * <p>默认每天凌晨执行一次，保留 30 天，可通过配置覆盖。</p>
     */
    @Scheduled(cron = "${starlight.note-trash.cleanup-cron:0 20 3 * * *}")
    public void cleanupExpiredTrashOnSchedule() {
        cleanupExpiredTrash();
    }

    /**
     * 手动触发回收站过期清理。
     *
     * @return 清理掉的笔记数量
     */
    public int cleanupExpiredTrash() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(getRetentionDays());
        List<Note> expiredNotes = noteRepository.findByDeletedAtBefore(cutoff);
        if (expiredNotes.isEmpty()) {
            log.debug("回收站自动清理完成，本次无过期笔记: cutoff={}", cutoff);
            return 0;
        }

        int removedShareCount = 0;
        for (Note note : expiredNotes) {
            long shareCount = noteShareRepository.countByNoteId(note.getId());
            if (shareCount > 0) {
                noteShareRepository.deleteByNoteId(note.getId());
                removedShareCount += (int) shareCount;
            }
        }
        if (removedShareCount > 0) {
            noteShareRepository.flush();
        }
        for (Note note : expiredNotes) {
            noteRepository.delete(note);
        }
        noteRepository.flush();
        log.info("回收站自动清理完成: noteCount={}, removedShareCount={}, cutoff={}", expiredNotes.size(), removedShareCount, cutoff);
        return expiredNotes.size();
    }

    /**
     * 创建新分类。
     *
     * @param owner    分类所有者
     * @param name     分类名称
     * @param parentId 父分类 ID（可为空表示顶级分类）
     * @return 新创建的分类
     * @throws IllegalArgumentException 当分类名称为空时
     */
    public Category createCategory(UserAccount owner, String name, String parentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        Category category = new Category();
        category.setOwner(owner);
        category.setName(name.trim());
        category.setParent(resolveCategory(owner.getId(), parentId));
        Category saved = categoryRepository.save(category);
        log.info("分类创建成功: categoryId={}, name={}, ownerId={}", saved.getId(), saved.getName(), owner.getId());
        return saved;
    }

    /**
     * 更新分类名称或父级位置。
     */
    public Category updateCategory(UserAccount owner, String categoryId, String name, String parentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        Category category = resolveCategory(owner.getId(), categoryId);
        Category newParent = resolveCategory(owner.getId(), parentId);
        if (newParent != null) {
            if (category.getId().equals(newParent.getId())) {
                throw new IllegalArgumentException("分类不能移动到自身下方");
            }
            Set<String> descendants = collectDescendantCategoryIds(owner.getId(), category.getId());
            if (descendants.contains(newParent.getId())) {
                throw new IllegalArgumentException("分类不能移动到自己的子分类下");
            }
        }
        category.setName(name.trim());
        category.setParent(newParent);
        Category saved = categoryRepository.save(category);
        log.info("分类更新成功: categoryId={}, ownerId={}, parentId={}", saved.getId(), owner.getId(),
                saved.getParent() == null ? null : saved.getParent().getId());
        return saved;
    }

    /**
     * 删除空分类。
     * <p>若分类下仍存在子分类或笔记，则拒绝删除，避免误操作。</p>
     */
    public void deleteCategory(String ownerId, String categoryId) {
        Category category = resolveCategory(ownerId, categoryId);
        Set<String> descendants = collectDescendantCategoryIds(ownerId, categoryId);
        if (descendants.size() > 1) {
            throw new IllegalArgumentException("请先清空子分类后再删除该分类");
        }
        if (noteRepository.countByCategoryIdIn(descendants) > 0) {
            throw new IllegalArgumentException("请先移除分类下的笔记后再删除该分类");
        }
        categoryRepository.delete(category);
        log.info("分类删除成功: categoryId={}, ownerId={}", categoryId, ownerId);
    }

    /**
     * 根据分类 ID 和所有者 ID 解析分类实体。
     *
     * @param ownerId    用户 ID
     * @param categoryId 分类 ID（为 null 或空白时返回 null）
     * @return 分类实体，或 null
     * @throws ResponseStatusException 当分类不存在时
     */
    private Category resolveCategory(String ownerId, String categoryId) {
        String normalizedCategoryId = normalizeNullableCategoryId(categoryId);
        if (normalizedCategoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndOwnerId(normalizedCategoryId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "分类不存在"));
    }

    /**
     * 归一化可空分类 ID。
     * <p>兼容 MCP / Web 客户端把“无分类”错误传成字符串 "null" 的场景。</p>
     */
    public static String normalizeNullableCategoryId(String categoryId) {
        if (categoryId == null) {
            return null;
        }
        String normalized = categoryId.trim();
        if (normalized.isBlank() || "null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    /** 收集某个分类及其所有子分类 ID。 */
    private Set<String> collectDescendantCategoryIds(String ownerId, String rootCategoryId) {
        List<Category> categories = categoryRepository.findByOwnerIdOrderByNameAsc(ownerId);
        Map<String, List<String>> childrenMap = new HashMap<>();
        for (Category category : categories) {
            if (category.getParent() == null) {
                continue;
            }
            childrenMap.computeIfAbsent(category.getParent().getId(), key -> new ArrayList<>())
                    .add(category.getId());
        }
        Set<String> result = new LinkedHashSet<>();
        ArrayList<String> queue = new ArrayList<>();
        result.add(rootCategoryId);
        queue.add(rootCategoryId);
        while (!queue.isEmpty()) {
            String currentId = queue.removeFirst();
            for (String childId : childrenMap.getOrDefault(currentId, List.of())) {
                if (result.add(childId)) {
                    queue.add(childId);
                }
            }
        }
        return result;
    }

    /** 计算分类相对根节点的深度。 */
    private Map<String, Integer> computeCategoryDepths(List<Category> categories, String rootCategoryId, int safeDepth) {
        Map<String, Integer> depthMap = new HashMap<>();
        Map<String, List<Category>> childrenMap = new HashMap<>();
        Category rootCategory = null;
        for (Category category : categories) {
            if (rootCategoryId != null && rootCategoryId.equals(category.getId())) {
                rootCategory = category;
            }
            if (category.getParent() != null) {
                childrenMap.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>())
                        .add(category);
            }
        }

        if (rootCategoryId != null && rootCategory == null) {
            return depthMap;
        }

        ArrayList<Category> queue = new ArrayList<>();
        if (rootCategory != null) {
            depthMap.put(rootCategory.getId(), 0);
            queue.add(rootCategory);
        } else {
            for (Category category : categories) {
                if (category.getParent() == null) {
                    depthMap.put(category.getId(), 0);
                    queue.add(category);
                }
            }
        }

        while (!queue.isEmpty()) {
            Category current = queue.removeFirst();
            int currentDepth = depthMap.getOrDefault(current.getId(), 0);
            if (currentDepth >= safeDepth) {
                continue;
            }
            for (Category child : childrenMap.getOrDefault(current.getId(), List.of())) {
                if (!depthMap.containsKey(child.getId())) {
                    depthMap.put(child.getId(), currentDepth + 1);
                    queue.add(child);
                }
            }
        }
        return depthMap;
    }

    /** 判断笔记是否应出现在当前根节点与深度限制下。 */
    private boolean isNoteIncluded(Note note, String rootCategoryId, Set<String> includedCategoryIds) {
        if (note.getCategory() == null) {
            return rootCategoryId == null;
        }
        return includedCategoryIds.contains(note.getCategory().getId());
    }

    /**
     * 标准化笔记标题。
     * <p>如果标题为空，则从 Markdown 内容的第一行提取（截断至80字符），否则返回默认标题。</p>
     */
    private String normalizeTitle(String title, String markdownContent) {
        String value = title == null ? "" : title.trim();
        if (!value.isBlank()) {
            return value;
        }
        String source = markdownContent == null ? "" : markdownContent.strip();
        if (!source.isBlank()) {
            String firstLine = source.lines().findFirst().orElse("未命名笔记").trim();
            return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
        }
        return "未命名笔记";
    }

    /** 将笔记转换为摘要 Map（用于列表展示） */
    private Map<String, Object> toSummary(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", note.getId());
        map.put("title", note.getTitle());
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("deletedAt", note.getDeletedAt());
        map.put("purgeAt", calculatePurgeAt(note.getDeletedAt()));
        map.put("pinnedFlag", note.isPinnedFlag());
        return map;
    }

    /**
     * 将笔记转换为详情 Map（用于笔记详情页展示）。
     */
    public Map<String, Object> toDetail(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", note.getId());
        map.put("title", note.getTitle());
        map.put("markdownContent", note.getMarkdownContent());
        map.put("renderedHtml", note.getRenderedHtml());
        map.put("outlineJson", note.getOutlineJson());
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("createdAt", note.getCreatedAt());
        map.put("deletedAt", note.getDeletedAt());
        map.put("purgeAt", calculatePurgeAt(note.getDeletedAt()));
        map.put("pinnedFlag", note.isPinnedFlag());
        return map;
    }

    /** 将笔记转换为目录树节点。 */
    private Map<String, Object> toTreeNote(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", note.getId());
        map.put("name", note.getTitle());
        map.put("type", "note");
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("pinnedFlag", note.isPinnedFlag());
        return map;
    }

    /** 根据删除时间计算自动清理时间。 */
    private LocalDateTime calculatePurgeAt(LocalDateTime deletedAt) {
        if (deletedAt == null) {
            return null;
        }
        return deletedAt.plusDays(getRetentionDays());
    }

    /** 获取回收站保留天数，至少为 1 天。 */
    private int getRetentionDays() {
        return Math.max(starlightProperties.getNoteTrash().getRetentionDays(), 1);
    }

    /** 安全地将 children 属性转为 List */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castChildren(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }
}

