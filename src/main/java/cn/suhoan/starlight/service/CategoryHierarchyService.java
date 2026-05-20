package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类层级结构服务。
 * <p>封装分类子树收集、深度计算等纯结构逻辑，避免导入、回收站和目录树服务各自维护一份遍历代码。</p>
 */
@Service
public class CategoryHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(CategoryHierarchyService.class);

    private final CategoryRepository categoryRepository;

    public CategoryHierarchyService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** 收集正常分类子树 ID，用于分类移动校验。 */
    @Transactional(readOnly = true)
    public Set<String> collectActiveDescendantIds(String ownerId, String rootCategoryId) {
        Set<String> ids = collectDescendantIds(
                categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(ownerId),
                rootCategoryId
        );
        log.debug("正常分类子树收集完成: ownerId={}, rootCategoryId={}, count={}", ownerId, rootCategoryId, ids.size());
        return ids;
    }

    /** 收集包含回收站数据的分类子树 ID，用于永久删除和 Git 导入清理。 */
    @Transactional(readOnly = true)
    public Set<String> collectAllDescendantIds(String ownerId, String rootCategoryId) {
        Set<String> ids = collectDescendantIds(categoryRepository.findByOwnerIdOrderByNameAsc(ownerId), rootCategoryId);
        log.debug("完整分类子树收集完成: ownerId={}, rootCategoryId={}, count={}", ownerId, rootCategoryId, ids.size());
        return ids;
    }

    /**
     * 基于已加载的分类集合收集子树 ID。
     * <p>调用方已持有分类列表时使用该方法，避免重复查询数据库。</p>
     */
    public Set<String> collectDescendantIds(List<Category> categories, String rootCategoryId) {
        Map<String, List<String>> childrenMap = new HashMap<>();
        for (Category category : categories) {
            if (category.getParent() == null) {
                continue;
            }
            childrenMap.computeIfAbsent(category.getParent().getId(), ignored -> new ArrayList<>())
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

    /**
     * 计算分类相对查询根的深度。
     * <p>用于 MCP/API Key 目录树查询的 depth 限制；rootCategoryId 为空时从顶级分类开始。</p>
     */
    public Map<String, Integer> computeCategoryDepths(List<Category> categories, String rootCategoryId, int maxDepth) {
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
            if (currentDepth >= maxDepth) {
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

    /** 计算分类在整棵树中的深度，根分类深度为 0。 */
    public int depth(Category category) {
        int depth = 0;
        Category current = category == null ? null : category.getParent();
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }
}
