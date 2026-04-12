package cn.suhoan.starlight.service;

import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类授权范围服务。
 * <p>负责展开分类树，计算某些根分类对应的全部可访问子分类。</p>
 */
@Service
@Transactional(readOnly = true)
public class CategoryAccessService {

    private final CategoryRepository categoryRepository;

    public CategoryAccessService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** 查询指定用户的全部分类。 */
    public List<Category> listUserCategories(String ownerId) {
        return categoryRepository.findByOwnerIdAndDeletedAtIsNullOrderByNameAsc(ownerId);
    }

    /** 按 ID 构建分类映射。 */
    public Map<String, Category> mapUserCategories(String ownerId) {
        Map<String, Category> map = new LinkedHashMap<>();
        for (Category category : listUserCategories(ownerId)) {
            map.put(category.getId(), category);
        }
        return map;
    }

    /**
     * 展开根分类集合，包含所有后代分类。
     */
    public Set<String> expandAuthorizedCategoryIds(String ownerId, Collection<String> rootCategoryIds) {
        Map<String, Category> categoryMap = mapUserCategories(ownerId);
        Map<String, List<String>> childrenMap = buildChildrenMap(categoryMap.values());
        Set<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String rootCategoryId : rootCategoryIds) {
            if (rootCategoryId != null && categoryMap.containsKey(rootCategoryId) && result.add(rootCategoryId)) {
                queue.add(rootCategoryId);
            }
        }
        while (!queue.isEmpty()) {
            String categoryId = queue.removeFirst();
            for (String childId : childrenMap.getOrDefault(categoryId, List.of())) {
                if (result.add(childId)) {
                    queue.addLast(childId);
                }
            }
        }
        return result;
    }

    private Map<String, List<String>> buildChildrenMap(Collection<Category> categories) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Category category : categories) {
            if (category.getParent() == null) {
                continue;
            }
            map.computeIfAbsent(category.getParent().getId(), key -> new java.util.ArrayList<>())
                    .add(category.getId());
        }
        return map;
    }
}

