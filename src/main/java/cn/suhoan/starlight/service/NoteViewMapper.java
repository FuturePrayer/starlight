package cn.suhoan.starlight.service;

import cn.suhoan.starlight.config.StarlightProperties;
import cn.suhoan.starlight.entity.Category;
import cn.suhoan.starlight.entity.Note;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 笔记与分类的前端视图映射器。
 * <p>集中维护 API 返回 Map 的字段形状，避免业务服务和树构建逻辑重复拼装展示字段。</p>
 */
@Component
public class NoteViewMapper {

    private final StarlightProperties starlightProperties;

    public NoteViewMapper(StarlightProperties starlightProperties) {
        this.starlightProperties = starlightProperties;
    }

    /** 将笔记转换为摘要 Map（用于列表展示）。 */
    public Map<String, Object> toSummary(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", note.getId());
        map.put("title", note.getTitle());
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("deletedAt", note.getDeletedAt());
        map.put("purgeAt", calculatePurgeAt(note.getDeletedAt()));
        map.put("pinnedFlag", note.isPinnedFlag());
        map.put("restorable", isTrashNoteRestorable(note));
        return map;
    }

    /** 将笔记转换为详情 Map（用于笔记详情页展示）。 */
    public Map<String, Object> toDetail(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", note.getId());
        map.put("title", note.getTitle());
        map.put("markdownContent", note.getMarkdownContent());
        map.put("outlineJson", note.getOutlineJson());
        map.put("categoryId", note.getCategory() == null ? null : note.getCategory().getId());
        map.put("updatedAt", note.getUpdatedAt());
        map.put("createdAt", note.getCreatedAt());
        map.put("deletedAt", note.getDeletedAt());
        map.put("purgeAt", calculatePurgeAt(note.getDeletedAt()));
        map.put("pinnedFlag", note.isPinnedFlag());
        map.put("restorable", isTrashNoteRestorable(note));
        return map;
    }

    /** 将正常分类转换为目录树节点。 */
    public Map<String, Object> toCategoryNode(Category category) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", category.getId());
        node.put("name", category.getName());
        node.put("type", "category");
        node.put("siteToken", category.getSiteToken());
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }

    /** 将回收站分类转换为树节点。 */
    public Map<String, Object> toTrashCategoryNode(Category category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        map.put("type", "category");
        map.put("parentId", category.getParent() == null ? null : category.getParent().getId());
        map.put("deletedAt", category.getDeletedAt());
        map.put("purgeAt", calculatePurgeAt(category.getDeletedAt()));
        map.put("restorable", isTrashCategoryRestorable(category));
        map.put("children", new ArrayList<Map<String, Object>>());
        return map;
    }

    /** 将回收站中的笔记转换为树节点。 */
    public Map<String, Object> toTrashTreeNote(Note note) {
        Map<String, Object> map = new LinkedHashMap<>(toSummary(note));
        map.put("name", note.getTitle());
        map.put("type", "note");
        return map;
    }

    /** 将笔记转换为目录树节点。 */
    public Map<String, Object> toTreeNote(Note note) {
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
    public LocalDateTime calculatePurgeAt(LocalDateTime deletedAt) {
        if (deletedAt == null) {
            return null;
        }
        return deletedAt.plusDays(getRetentionDays());
    }

    /** 判断回收站笔记当前是否允许直接恢复。 */
    public boolean isTrashNoteRestorable(Note note) {
        if (note == null || note.getDeletedAt() == null) {
            return false;
        }
        return note.getCategory() == null || note.getCategory().getDeletedAt() == null;
    }

    /** 判断回收站分类当前是否允许直接恢复。 */
    public boolean isTrashCategoryRestorable(Category category) {
        if (category == null || category.getDeletedAt() == null) {
            return false;
        }
        return category.getParent() == null || category.getParent().getDeletedAt() == null;
    }

    /** 获取回收站保留天数，至少为 1 天。 */
    private int getRetentionDays() {
        return Math.max(starlightProperties.getNoteTrash().getRetentionDays(), 1);
    }
}
