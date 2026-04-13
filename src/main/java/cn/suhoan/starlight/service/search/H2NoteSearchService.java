package cn.suhoan.starlight.service.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * H2 数据库全文搜索实现（LIKE 兜底方案，也作为未知数据库的默认实现）。
 * <p>使用原生 SQL + CAST 绕过 H2 的 LOWER(CLOB) 不兼容问题。
 * 支持多关键词搜索（空格分隔），使用应用层评分排序。</p>
 *
 * @author suhoan
 */
public class H2NoteSearchService implements NoteSearchService {

    /** 候选集倍率：从数据库多取 N 倍候选以便应用层评分排序 */
    private static final int CANDIDATE_MULTIPLIER = 5;

    private final EntityManager entityManager;

    public H2NoteSearchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String ownerId, String keyword, int offset, int limit,
                                            Collection<String> allowedCategoryIds) {
        if (allowedCategoryIds != null && allowedCategoryIds.isEmpty()) {
            return List.of();
        }

        List<String> keywords = SearchSnippetUtil.splitKeywords(keyword);
        if (keywords.isEmpty()) {
            return List.of();
        }

        // 构建分类过滤条件
        String categoryCondition = "";
        if (allowedCategoryIds != null) {
            String placeholders = IntStream.range(0, allowedCategoryIds.size())
                    .mapToObj(index -> ":categoryId" + index)
                    .collect(Collectors.joining(", "));
            categoryCondition = " AND category_id IN (" + placeholders + ")";
        }

        // 构建多关键词 OR 匹配条件（任意一个关键词命中即入选候选集）
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            conditions.add("(LOWER(title) LIKE :pattern" + i
                    + " OR LOWER(CAST(plain_text AS VARCHAR)) LIKE :pattern" + i + ")");
        }
        String keywordCondition = "(" + String.join(" OR ", conditions) + ")";

        // 候选集大小：取 (offset + limit) * CANDIDATE_MULTIPLIER 以确保评分排序后分页准确
        int candidateLimit = (offset + limit) * CANDIDATE_MULTIPLIER;

        String sql = "SELECT id, title, CAST(plain_text AS VARCHAR) AS plain_text, updated_at"
                + " FROM sl_note"
                + " WHERE owner_id = :ownerId"
                + " AND deleted_at IS NULL"
                + " AND " + keywordCondition
                + categoryCondition
                + " ORDER BY updated_at DESC"
                + " LIMIT :candidateLimit";

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("candidateLimit", candidateLimit);

        // 绑定每个关键词的 LIKE 参数
        for (int i = 0; i < keywords.size(); i++) {
            query.setParameter("pattern" + i, "%" + SearchSnippetUtil.escapeLike(keywords.get(i)).toLowerCase() + "%");
        }
        // 绑定分类参数
        if (allowedCategoryIds != null) {
            int index = 0;
            for (String categoryId : allowedCategoryIds) {
                query.setParameter("categoryId" + index++, categoryId);
            }
        }

        List<Object[]> rows = query.getResultList();
        return scoreAndPaginate(rows, keywords, keyword, offset, limit);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchInNote(String ownerId, String noteId, String keyword) {
        List<String> keywords = SearchSnippetUtil.splitKeywords(keyword);
        if (keywords.isEmpty()) {
            return List.of();
        }

        // 构建多关键词 OR 匹配条件
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            conditions.add("(LOWER(title) LIKE :pattern" + i
                    + " OR LOWER(CAST(plain_text AS VARCHAR)) LIKE :pattern" + i + ")");
        }
        String keywordCondition = "(" + String.join(" OR ", conditions) + ")";

        String sql = "SELECT id, title, CAST(plain_text AS VARCHAR) AS plain_text, updated_at"
                + " FROM sl_note"
                + " WHERE owner_id = :ownerId AND id = :noteId"
                + " AND deleted_at IS NULL"
                + " AND " + keywordCondition;

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("noteId", noteId);
        for (int i = 0; i < keywords.size(); i++) {
            query.setParameter("pattern" + i, "%" + SearchSnippetUtil.escapeLike(keywords.get(i)).toLowerCase() + "%");
        }

        List<Object[]> rows = query.getResultList();
        return scoreAndPaginate(rows, keywords, keyword, 0, 1);
    }

    /**
     * 对候选集进行应用层评分、排序、分页并构建结果。
     */
    private List<Map<String, Object>> scoreAndPaginate(List<Object[]> rows, List<String> keywords,
                                                        String rawQuery, int offset, int limit) {
        // 计算每条记录的得分
        List<Map<String, Object>> scored = rows.stream()
                .map(row -> {
                    String id = (String) row[0];
                    String title = (String) row[1];
                    String plainText = (String) row[2];
                    LocalDateTime updatedAt = toLocalDateTime(row[3]);
                    int score = NoteSearchScorer.score(title, plainText, keywords, rawQuery);
                    return SearchSnippetUtil.toScoredSearchResult(id, title, plainText, updatedAt, keywords, score);
                })
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("score")).reversed())
                .toList();

        // 分页截取
        int fromIndex = Math.min(offset, scored.size());
        int toIndex = Math.min(offset + limit, scored.size());
        return scored.subList(fromIndex, toIndex);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
