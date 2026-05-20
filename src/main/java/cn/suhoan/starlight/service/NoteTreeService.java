package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.Note;
import cn.suhoan.starlight.repository.CategoryRepository;
import cn.suhoan.starlight.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 笔记目录树构建服务。
 * <p>只负责把分类和笔记组装成前端/MCP 需要的树形 Map，不承担笔记增删改业务。</p>
 */
@Service
public class NoteTreeService {

    private static final Logger log = LoggerFactory.getLogger(NoteTreeService.class);
    private static final Comparator<Note> PINNED_NOTE_COMPARATOR = Comparator
            .comparing(Note::getPinnedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Note::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER);

    private final NoteRepository noteRepository;
    private final CategoryRepository categoryRepository;
    private final NoteViewMapper noteViewMapper;
    private final CategoryHierarchyService categoryHierarchyService;

    public NoteTreeService(NoteRepository noteRepository,
                           CategoryRepository categoryRepository,
                           NoteViewMapper noteViewMapper,
                           CategoryHierarchyService categoryHierarchyService) {
        this.noteRepository = noteRepository;
        this.categoryRepository = categoryRepository;
        this.noteViewMapper = noteViewMapper;
        this.categoryHierarchyService = categoryHierarchyService;
    }

    /**
     * 构建正常目录树。
     * <p>accessibleCategoryIds 为 null 表示当前用户完整视图；非 null 时表示 MCP/API Key 的受限分类视图。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildTree(String ownerId,
                                         Set<String> accessibleCategoryIds,
                                         String rootCategoryId,
                                         Integer depth) {
        List<Category> allCategories = categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(ownerId);
        List<Note> allNotes = noteRepository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId);

        String normalizedRootCategoryId = CategoryIdNormalizer.normalizeNullableCategoryId(rootCategoryId);
        int safeDepth = depth == null ? Integer.MAX_VALUE : Math.max(depth, 0);
        boolean restricted = accessibleCategoryIds != null;

        List<Category> visibleCategories = restricted
                ? allCategories.stream().filter(category -> accessibleCategoryIds.contains(category.getId())).toList()
                : allCategories;

        Map<String, Integer> depthMap = categoryHierarchyService.computeCategoryDepths(
                visibleCategories,
                normalizedRootCategoryId,
                safeDepth
        );
        Set<String> includedCategoryIds = depthMap.keySet();

        List<Category> categories = visibleCategories.stream()
                .filter(category -> includedCategoryIds.contains(category.getId()))
                .toList();
        List<Note> notes = allNotes.stream()
                .filter(note -> isNoteIncluded(note, normalizedRootCategoryId, includedCategoryIds))
                .toList();

        Map<String, Map<String, Object>> categoryMap = buildCategoryNodeMap(categories);
        List<Map<String, Object>> roots = assembleCategoryRoots(categories, categoryMap, normalizedRootCategoryId);
        List<Map<String, Object>> pinnedItems = appendNotes(notes, categoryMap, roots, normalizedRootCategoryId);
        roots.sort(Comparator.comparing(item -> item.get("name").toString()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", roots);
        data.put("pinnedItems", pinnedItems);
        data.put("trashCount", restricted ? 0 : noteRepository.countByOwnerIdAndDeletedAtIsNotNull(ownerId)
                + categoryRepository.countByOwnerIdAndDeletedAtIsNotNull(ownerId));

        log.debug("笔记树构建完成: ownerId={}, restricted={}, rootCategoryId={}, depth={}, categoryCount={}, noteCount={}, pinnedCount={}",
                ownerId, restricted, normalizedRootCategoryId, safeDepth, categories.size(), notes.size(), pinnedItems.size());
        return data;
    }

    /**
     * 构建回收站树。
     * <p>被删除的分类会继续保留父子层级；属于已删除分类的笔记挂到对应分类下。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildTrashTree(String ownerId) {
        List<Category> allCategories = categoryRepository.findByOwnerIdOrderByNameAsc(ownerId);
        List<Category> deletedCategories = allCategories.stream()
                .filter(category -> category.getDeletedAt() != null)
                .toList();
        List<Note> deletedNotes = noteRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(ownerId);

        Map<String, Map<String, Object>> categoryNodeMap = new HashMap<>();
        Set<String> deletedCategoryIds = new LinkedHashSet<>();
        for (Category category : deletedCategories) {
            categoryNodeMap.put(category.getId(), noteViewMapper.toTrashCategoryNode(category));
            deletedCategoryIds.add(category.getId());
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (Category category : deletedCategories) {
            Map<String, Object> node = categoryNodeMap.get(category.getId());
            Category parent = category.getParent();
            if (parent != null && deletedCategoryIds.contains(parent.getId())) {
                castChildren(categoryNodeMap.get(parent.getId())).add(node);
            } else {
                roots.add(node);
            }
        }

        for (Note note : deletedNotes) {
            if (note.getCategory() != null && deletedCategoryIds.contains(note.getCategory().getId())) {
                castChildren(categoryNodeMap.get(note.getCategory().getId())).add(noteViewMapper.toTrashTreeNote(note));
            } else {
                roots.add(noteViewMapper.toTrashTreeNote(note));
            }
        }

        sortTrashItems(roots);
        decorateTrashCategoryStats(roots);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", roots);
        result.put("noteCount", deletedNotes.size());
        result.put("categoryCount", deletedCategories.size());
        result.put("totalCount", deletedNotes.size() + deletedCategories.size());

        log.debug("回收站树构建完成: ownerId={}, categoryCount={}, noteCount={}",
                ownerId, deletedCategories.size(), deletedNotes.size());
        return result;
    }

    /** 创建分类节点，并预先记录站点发布信息，供后续计算 inheritedSiteToken。 */
    private Map<String, Map<String, Object>> buildCategoryNodeMap(List<Category> categories) {
        Map<String, Map<String, Object>> categoryMap = new HashMap<>();
        Map<String, String> parentIdMap = new HashMap<>();
        Map<String, String> siteTokenMap = new HashMap<>();

        for (Category category : categories) {
            categoryMap.put(category.getId(), noteViewMapper.toCategoryNode(category));
            if (category.getParent() != null) {
                parentIdMap.put(category.getId(), category.getParent().getId());
            }
            if (category.getSiteToken() != null) {
                siteTokenMap.put(category.getId(), category.getSiteToken());
            }
        }

        // 星迹书阁站点标识允许子分类继承，前端用 inheritedSiteToken 展示继承状态。
        for (Category category : categories) {
            Map<String, Object> node = categoryMap.get(category.getId());
            if (category.getSiteToken() != null) {
                continue;
            }
            String parentId = parentIdMap.get(category.getId());
            while (parentId != null) {
                if (siteTokenMap.containsKey(parentId)) {
                    node.put("inheritedSiteToken", siteTokenMap.get(parentId));
                    node.put("inheritedFromId", parentId);
                    break;
                }
                parentId = parentIdMap.get(parentId);
            }
        }
        return categoryMap;
    }

    /** 将分类节点挂到父分类下，或挂到当前查询根。 */
    private List<Map<String, Object>> assembleCategoryRoots(List<Category> categories,
                                                            Map<String, Map<String, Object>> categoryMap,
                                                            String normalizedRootCategoryId) {
        List<Map<String, Object>> roots = new ArrayList<>();
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
        return roots;
    }

    /** 将笔记挂入分类节点；置顶笔记另行返回给前端顶部区域。 */
    private List<Map<String, Object>> appendNotes(List<Note> notes,
                                                  Map<String, Map<String, Object>> categoryMap,
                                                  List<Map<String, Object>> roots,
                                                  String normalizedRootCategoryId) {
        List<Map<String, Object>> pinnedItems = notes.stream()
                .filter(Note::isPinnedFlag)
                .sorted(PINNED_NOTE_COMPARATOR)
                .map(noteViewMapper::toTreeNote)
                .toList();
        for (Note note : notes) {
            if (note.isPinnedFlag()) {
                continue;
            }
            Map<String, Object> node = noteViewMapper.toTreeNote(note);
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
        return pinnedItems;
    }

    /** 判断笔记是否应出现在当前根节点与深度限制下。 */
    private boolean isNoteIncluded(Note note, String rootCategoryId, Set<String> includedCategoryIds) {
        if (note.getCategory() == null) {
            return rootCategoryId == null;
        }
        return includedCategoryIds.contains(note.getCategory().getId());
    }

    /** 对回收站树做稳定排序：分类在前、笔记在后，并递归处理子节点。 */
    private void sortTrashItems(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            if ("category".equals(item.get("type"))) {
                sortTrashItems(castChildren(item));
            }
        }
        items.sort((left, right) -> {
            int typeOrder = Boolean.compare(
                    !"category".equals(left.get("type")),
                    !"category".equals(right.get("type"))
            );
            if (typeOrder != 0) {
                return typeOrder;
            }
            return String.valueOf(left.get("name")).compareToIgnoreCase(String.valueOf(right.get("name")));
        });
    }

    /** 为回收站分类节点补充直接子项统计信息。 */
    private void decorateTrashCategoryStats(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            if (!"category".equals(item.get("type"))) {
                continue;
            }
            List<Map<String, Object>> children = castChildren(item);
            long childCategoryCount = children.stream().filter(child -> "category".equals(child.get("type"))).count();
            long childNoteCount = children.stream().filter(child -> "note".equals(child.get("type"))).count();
            item.put("childCategoryCount", childCategoryCount);
            item.put("childNoteCount", childNoteCount);
            item.put("hasChildren", !children.isEmpty());
            decorateTrashCategoryStats(children);
        }
    }

    /** 安全地将 children 属性转为 List。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castChildren(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }
}
