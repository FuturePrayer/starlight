package cn.suhoan.starlight.service.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MySQL 全文搜索实现。
 * 使用 FULLTEXT 索引 + ngram parser（MySQL 5.7+ 内置，原生支持中文分词）。
 * 当关键词太短（少于 ngram_token_size，默认为 2）时自动回退 LIKE。
 */
public class MysqlNoteSearchService implements NoteSearchService {

    private static final Logger log = LoggerFactory.getLogger(MysqlNoteSearchService.class);

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
    public List<Map<String, Object>> search(String ownerId, String keyword, int offset, int limit) {
        // ngram_token_size 默认为 2，太短的关键词走 LIKE
        if (keyword.length() < 2) {
            return likeFallback(ownerId, keyword, offset, limit);
        }
        return fulltextSearch(ownerId, keyword, offset, limit);
    }

    private List<Map<String, Object>> fulltextSearch(String ownerId, String keyword, int offset, int limit) {
        String sql = """
                SELECT id, title, plain_text, updated_at,
                       MATCH(title, plain_text) AGAINST(:keyword IN BOOLEAN MODE) AS score
                FROM sl_note
                WHERE owner_id = :ownerId
                  AND MATCH(title, plain_text) AGAINST(:keyword IN BOOLEAN MODE)
                ORDER BY score DESC, updated_at DESC
                LIMIT :limit OFFSET :offset
                """;

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("keyword", keyword)
                .setParameter("offset", offset)
                .setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> SearchSnippetUtil.toSearchResult(
                        (String) row[0], (String) row[1], (String) row[2],
                        toLocalDateTime(row[3]), keyword))
                .toList();
    }

    private List<Map<String, Object>> likeFallback(String ownerId, String keyword, int offset, int limit) {
        String sql = """
                SELECT id, title, plain_text, updated_at
                FROM sl_note
                WHERE owner_id = :ownerId
                  AND (LOWER(title) LIKE LOWER(:pattern)
                       OR LOWER(plain_text) LIKE LOWER(:pattern))
                ORDER BY
                  CASE WHEN LOWER(title) LIKE LOWER(:pattern) THEN 0 ELSE 1 END,
                  updated_at DESC
                LIMIT :limit OFFSET :offset
                """;

        String pattern = "%" + SearchSnippetUtil.escapeLike(keyword) + "%";
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("pattern", pattern)
                .setParameter("offset", offset)
                .setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> SearchSnippetUtil.toSearchResult(
                        (String) row[0], (String) row[1], (String) row[2],
                        toLocalDateTime(row[3]), keyword))
                .toList();
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}

