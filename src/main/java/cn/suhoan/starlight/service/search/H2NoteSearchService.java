package cn.suhoan.starlight.service.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * H2 数据库全文搜索实现（LIKE 兜底方案，也作为未知数据库的默认实现）。
 * 使用原生 SQL + CAST 绕过 H2 的 LOWER(CLOB) 不兼容问题。
 *
 * @author suhoan
 */
public class H2NoteSearchService implements NoteSearchService {

    private final EntityManager entityManager;

    public H2NoteSearchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String ownerId, String keyword, int offset, int limit) {
        // H2 的 LOWER() 不支持 CLOB，需要先 CAST 为 VARCHAR
        String sql = """
                SELECT id, title, CAST(plain_text AS VARCHAR) AS plain_text, updated_at
                FROM sl_note
                WHERE owner_id = :ownerId
                  AND (LOWER(title) LIKE :pattern
                       OR LOWER(CAST(plain_text AS VARCHAR)) LIKE :pattern)
                ORDER BY
                  CASE WHEN LOWER(title) LIKE :pattern THEN 0 ELSE 1 END,
                  updated_at DESC
                LIMIT :limit OFFSET :offset
                """;

        String pattern = "%" + SearchSnippetUtil.escapeLike(keyword).toLowerCase() + "%";
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
