package cn.suhoan.starlight.service.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * PostgreSQL 全文搜索实现。
 * <p>使用 ILIKE 进行模糊匹配（天然支持中文）。
 * 可选启用 pg_trgm 扩展 + GIN 索引以加速 ILIKE 查询。
 * 支持多关键词搜索（空格分隔），使用应用层评分排序。</p>
 *
 * @author suhoan
 */
public class PostgresNoteSearchService implements NoteSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostgresNoteSearchService.class);

    /** 候选集倍率 */
    private static final int CANDIDATE_MULTIPLIER = 5;

    private final EntityManager entityManager;

    public PostgresNoteSearchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 尝试启用 pg_trgm 扩展并创建 GIN 三元组索引以加速 ILIKE。
     * pg_trgm 是 PostgreSQL 内置扩展，支持中文三元组匹配。
     * 如果权限不足或不支持，静默忽略 — ILIKE 仍然可以工作，只是不走索引。
     */
    public void tryEnableTrgmIndex() {
        try {
            entityManager.createNativeQuery("CREATE EXTENSION IF NOT EXISTS pg_trgm")
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            CREATE INDEX IF NOT EXISTS idx_note_title_trgm
                            ON sl_note USING gin (title gin_trgm_ops)
                            """)
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            CREATE INDEX IF NOT EXISTS idx_note_plaintext_trgm
                            ON sl_note USING gin (plain_text gin_trgm_ops)
                            """)
                    .executeUpdate();
            log.info("PostgreSQL pg_trgm GIN indexes ensured for full-text search");
        } catch (Exception e) {
            log.debug("pg_trgm index creation skipped: {}", e.getMessage());
        }
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

        String categoryCondition = buildCategoryCondition(allowedCategoryIds);
        int candidateLimit = (offset + limit) * CANDIDATE_MULTIPLIER;

        // 构建多关键词 OR 匹配条件
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            conditions.add("(title ILIKE :pattern" + i + " OR plain_text ILIKE :pattern" + i + ")");
        }
        String keywordCondition = "(" + String.join(" OR ", conditions) + ")";

        String sql = "SELECT id, title, plain_text, updated_at FROM sl_note"
                + " WHERE owner_id = :ownerId"
                + " AND deleted_at IS NULL"
                + " AND " + keywordCondition
                + " " + categoryCondition
                + " ORDER BY updated_at DESC"
                + " OFFSET :offset LIMIT :candidateLimit";

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("offset", 0) // 候选集从头开始取
                .setParameter("candidateLimit", candidateLimit);

        for (int i = 0; i < keywords.size(); i++) {
            query.setParameter("pattern" + i, "%" + SearchSnippetUtil.escapeLike(keywords.get(i)) + "%");
        }
        bindCategoryParameters(query, allowedCategoryIds);

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

        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            conditions.add("(title ILIKE :pattern" + i + " OR plain_text ILIKE :pattern" + i + ")");
        }
        String keywordCondition = "(" + String.join(" OR ", conditions) + ")";

        String sql = "SELECT id, title, plain_text, updated_at FROM sl_note"
                + " WHERE owner_id = :ownerId AND id = :noteId"
                + " AND deleted_at IS NULL"
                + " AND " + keywordCondition;

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("noteId", noteId);
        for (int i = 0; i < keywords.size(); i++) {
            query.setParameter("pattern" + i, "%" + SearchSnippetUtil.escapeLike(keywords.get(i)) + "%");
        }

        List<Object[]> rows = query.getResultList();
        return scoreAndPaginate(rows, keywords, keyword, 0, 1);
    }

    /**
     * 对候选集进行应用层评分、排序、分页。
     */
    private List<Map<String, Object>> scoreAndPaginate(List<Object[]> rows, List<String> keywords,
                                                        String rawQuery, int offset, int limit) {
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

        int fromIndex = Math.min(offset, scored.size());
        int toIndex = Math.min(offset + limit, scored.size());
        return scored.subList(fromIndex, toIndex);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private String buildCategoryCondition(Collection<String> allowedCategoryIds) {
        if (allowedCategoryIds == null) {
            return "";
        }
        String placeholders = IntStream.range(0, allowedCategoryIds.size())
                .mapToObj(index -> ":categoryId" + index)
                .collect(Collectors.joining(", "));
        return "AND category_id IN (" + placeholders + ")";
    }

    private void bindCategoryParameters(Query query, Collection<String> allowedCategoryIds) {
        if (allowedCategoryIds == null) {
            return;
        }
        int index = 0;
        for (String categoryId : allowedCategoryIds) {
            query.setParameter("categoryId" + index++, categoryId);
        }
    }
}

