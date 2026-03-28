package cn.suhoan.starlight.service.search;

import java.util.List;
import java.util.Map;

/**
 * 笔记全文搜索服务抽象。
 * 根据用户配置的数据库类型自动选择对应实现。
 */
public interface NoteSearchService {

    /**
     * 在指定用户的笔记中搜索关键词。
     *
     * @param ownerId 用户 ID
     * @param keyword 搜索关键词
     * @param offset  分页偏移量
     * @param limit   最大返回条数
     * @return 搜索结果列表（id, title, snippet, updatedAt）
     */
    List<Map<String, Object>> search(String ownerId, String keyword, int offset, int limit);
}

