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
 * MySQL 全文搜索实现。
 * <p>使用 FULLTEXT 索引 + ngram parser（MySQL 5.7+ 内置，原生支持中文分词）。
 * 当关键词太短（少于 ngram_token_size，默认为 2）时自动回退 LIKE。
 * 支持多关键词搜索（空格分隔），使用应用层评分排序。</p>
 *
 * @author suhoan
 */
public class MysqlNoteSearchService implements NoteSearchService {

    private static final Logger log = LoggerFactory.getLogger(MysqlNoteSearchService.class);

    /** 候选集倍率 */
    private static final int CANDIDATE_MULTIPLIER = 5;

    private final EntityManager entityManager;

    public MysqlNoteSearchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 应用启动时尝试创建 FULLTEXT 索引（幂等）。
     */
    public void ensureFulltextIndex() {
        try {
            entityManager.createNativeQuery("""
                            ALTER TABLE sl_note
                            ADD FULLTEXT INDEX ft_note_search (title, plain_text) WITH PARSER ngram
                            """)
                    .executeUpdate();
            log.info("MySQL FULLTEXT ngram index created for full-text search");
        } catch (Exception e) {
            // Duplicate key name = index already exists, safe to ignore
            log.debug("MySQL FULLTEXT index creation skipped: {}", e.getMessage());
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

        // 判断是否所有关键词都足够长可使用 FULLTEXT
        boolean allLongEnough = keywords.stream().allMatch(kw -> kw.length() >= 2);
        if (allLongEnough) {
            return fulltextSearch(ownerId, keywords, keyword, offset, limit, allowedCategoryIds);
        }
        return likeFallback(ownerId, keywords, keyword, offset, limit, allowedCategoryIds);
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
            conditions.add("(LOWER(title) LIKE LOWER(:pattern" + i + ")"
                    + " OR LOWER(plain_text) LIKE LOWER(:pattern" + i + "))");
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

    private List<Map<String, Object>> fulltextSearch(String ownerId, List<String> keywords, String rawQuery,
                                                      int offset, int limit,
                                                      Collection<String> allowedCategoryIds) {
        String categoryCondition = buildCategoryCondition(allowedCategoryIds);
        int candidateLimit = (offset + limit) * CANDIDATE_MULTIPLIER;

        // 使用 BOOLEAN MODE，用 + 前缀要求每个关键词都必须出现
        String booleanQuery = keywords.stream()
                .map(kw -> "+" + kw)
                .collect(Collectors.joining(" "));

        String sql = """
                SELECT id, title, plain_text, updated_at
                FROM sl_note
                WHERE owner_id = :ownerId
                  AND deleted_at IS NULL
                  AND MATCH(title, plain_text) AGAINST(:booleanQuery IN BOOLEAN MODE)
                %s
                ORDER BY MATCH(title, plain_text) AGAINST(:booleanQuery IN BOOLEAN MODE) DESC, updated_at DESC
                LIMIT :candidateLimit
                """.formatted(categoryCondition);

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("booleanQuery", booleanQuery)
                .setParameter("candidateLimit", candidateLimit);
        bindCategoryParameters(query, allowedCategoryIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        // 如果 FULLTEXT BOOLEAN MODE 未返回结果（可能因为 ngram 分词问题），回退 LIKE
        if (rows.isEmpty()) {
            return likeFallback(ownerId, keywords, rawQuery, offset, limit, allowedCategoryIds);
        }
        return scoreAndPaginate(rows, keywords, rawQuery, offset, limit);
    }

    private List<Map<String, Object>> likeFallback(String ownerId, List<String> keywords, String rawQuery,
                                                    int offset, int limit,
                                                    Collection<String> allowedCategoryIds) {
        String categoryCondition = buildCategoryCondition(allowedCategoryIds);
        int candidateLimit = (offset + limit) * CANDIDATE_MULTIPLIER;

        // 构建多关键词 OR 匹配条件
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            conditions.add("(LOWER(title) LIKE LOWER(:pattern" + i + ")"
                    + " OR LOWER(plain_text) LIKE LOWER(:pattern" + i + "))");
        }
        String keywordCondition = "(" + String.join(" OR ", conditions) + ")";

        String sql = "SELECT id, title, plain_text, updated_at FROM sl_note"
                + " WHERE owner_id = :ownerId"
                + " AND deleted_at IS NULL"
                + " AND " + keywordCondition
                + " " + categoryCondition
                + " ORDER BY updated_at DESC"
                + " LIMIT :candidateLimit";

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("candidateLimit", candidateLimit);
        for (int i = 0; i < keywords.size(); i++) {
            query.setParameter("pattern" + i, "%" + SearchSnippetUtil.escapeLike(keywords.get(i)) + "%");
        }
        bindCategoryParameters(query, allowedCategoryIds);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return scoreAndPaginate(rows, keywords, rawQuery, offset, limit);
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

