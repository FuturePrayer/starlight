package cn.suhoan.starlight.config;

import cn.suhoan.starlight.service.search.H2NoteSearchService;
import cn.suhoan.starlight.service.search.MysqlNoteSearchService;
import cn.suhoan.starlight.service.search.NoteSearchService;
import cn.suhoan.starlight.service.search.PostgresNoteSearchService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * 根据数据库类型自动选择全文搜索实现。
 * 通过 JDBC DatabaseMetaData 检测数据库产品名称，无需用户手动配置 Profile。
 *
 * @author suhoan
 */
@Configuration
public class SearchConfig {

    private static final Logger log = LoggerFactory.getLogger(SearchConfig.class);

    @Bean
    @DependsOn("flywayDependencyConfig")
    public NoteSearchService noteSearchService(DataSource dataSource,
                                                EntityManager entityManager,
                                                PlatformTransactionManager txManager) {
        String dbType = detectDatabase(dataSource);
        log.info("Detected database type for search: {}", dbType);

        return switch (dbType) {
            case "mysql" -> {
                var service = new MysqlNoteSearchService(entityManager);
                runInTransaction(txManager, service::ensureFulltextIndex);
                yield service;
            }
            case "postgresql" -> {
                var service = new PostgresNoteSearchService(entityManager);
                runInTransaction(txManager, service::tryEnableTrgmIndex);
                yield service;
            }
            default -> {
                log.info("Using default LIKE-based search (H2 / unknown database)");
                yield new H2NoteSearchService(entityManager);
            }
        };
    }

    private String detectDatabase(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName().toLowerCase();
            if (productName.contains("mysql") || productName.contains("mariadb")) {
                return "mysql";
            }
            if (productName.contains("postgresql")) {
                return "postgresql";
            }
            return "h2";
        } catch (Exception e) {
            log.warn("Failed to detect database type, falling back to H2: {}", e.getMessage());
            return "h2";
        }
    }

    private void runInTransaction(PlatformTransactionManager txManager, Runnable action) {
        try {
            new TransactionTemplate(txManager).executeWithoutResult(_ -> action.run());
        } catch (Exception e) {
            log.warn("Search index initialization skipped: {}", e.getMessage());
        }
    }
}

