package cn.suhoan.starlight.service.search;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 笔记全文搜索服务抽象。
 * <p>支持多关键词搜索与评分排序。根据用户配置的数据库类型自动选择对应实现。</p>
 *
 * @author suhoan
 */
public interface NoteSearchService {

    /**
     * 在指定用户的笔记中搜索关键词（不限分类范围）。
     *
     * @param ownerId  用户 ID
     * @param keyword  搜索关键词（支持空格分隔的多关键词）
     * @param offset   分页偏移量
     * @param limit    最大返回条数
     * @return 搜索结果列表（id, title, snippet, updatedAt, score）
     */
    default List<Map<String, Object>> search(String ownerId, String keyword, int offset, int limit) {
        return search(ownerId, keyword, offset, limit, null);
    }

    /**
     * 在指定用户可访问的分类范围内搜索关键词。
     *
     * @param ownerId             用户 ID
     * @param keyword             搜索关键词（支持空格分隔的多关键词）
     * @param offset              分页偏移量
     * @param limit               最大返回条数
     * @param allowedCategoryIds  可访问分类 ID 集合；传入 null 表示不限制分类
     * @return 搜索结果列表（id, title, snippet, updatedAt, score）
     */
    List<Map<String, Object>> search(String ownerId, String keyword, int offset, int limit,
                                     Collection<String> allowedCategoryIds);

    /**
     * 在指定的单条笔记中搜索关键词。
     *
     * @param ownerId 用户 ID
     * @param noteId  笔记 ID
     * @param keyword 搜索关键词（支持空格分隔的多关键词）
     * @return 搜索结果列表（0 或 1 条），命中时返回包含高亮和评分的结果
     */
    List<Map<String, Object>> searchInNote(String ownerId, String noteId, String keyword);
}
