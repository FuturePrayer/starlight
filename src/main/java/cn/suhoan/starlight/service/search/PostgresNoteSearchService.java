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
 * PostgreSQL 全文搜索实现。
 * 使用 ILIKE 进行模糊匹配（天然支持中文）。
 * 可选启用 pg_trgm 扩展 + GIN 索引以加速 ILIKE 查询。
 */
public class PostgresNoteSearchService implements NoteSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostgresNoteSearchService.class);

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
    public List<Map<String, Object>> search(String ownerId, String keyword, int offset, int limit) {
        String sql = """
                SELECT id, title, plain_text, updated_at
                FROM sl_note
                WHERE owner_id = :ownerId
                  AND (title ILIKE :pattern OR plain_text ILIKE :pattern)
                ORDER BY
                  CASE WHEN title ILIKE :pattern THEN 0 ELSE 1 END,
                  updated_at DESC
                OFFSET :offset LIMIT :limit
                """;

        String pattern = "%" + SearchSnippetUtil.escapeLike(keyword) + "%";
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("ownerId", ownerId)
                .setParameter("pattern", pattern)
                .setParameter("offset", offset)
                .setParameter("limit", limit);

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

