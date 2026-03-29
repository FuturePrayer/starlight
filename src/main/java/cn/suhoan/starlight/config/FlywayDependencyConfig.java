package cn.suhoan.starlight.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 确保 Flyway 先于 JPA 校验执行。
 * <p>在开启 {@code ddl-auto=validate} 时，如果实体管理器过早初始化，
 * Hibernate 会先进行表结构校验，从而在迁移前报“缺表”。</p>
 */
@Configuration
public class FlywayDependencyConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(FlywayDependencyConfig.class);

    private final DataSource dataSource;

    public FlywayDependencyConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 在容器启动阶段执行 Flyway 迁移。
     * <p>这里直接使用 Flyway API，避免不同 Spring Boot 版本的自动装配差异。
     * 同时也规避了 JPA 在迁移前做校验导致的“缺表”问题。</p>
     */
    @Override
    public void afterPropertiesSet() {
        String vendor = detectVendor(dataSource);
        log.info("初始化 Flyway 迁移器: vendor={}", vendor);
        Flyway flyway = Flyway.configure()
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .locations("classpath:db/migration/common", "classpath:db/migration/" + vendor)
                .dataSource(dataSource)
                .load();
        log.info("开始执行 Flyway 数据库迁移");
        flyway.migrate();
        log.info("Flyway 数据库迁移完成");
    }

    /**
     * 根据 JDBC 元数据识别数据库厂商，用于拼接 Flyway vendor 目录。
     */
    private String detectVendor(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (productName.contains("mysql") || productName.contains("mariadb")) {
                return "mysql";
            }
            if (productName.contains("postgresql")) {
                return "postgresql";
            }
        } catch (Exception exception) {
            log.warn("识别数据库类型失败，Flyway 将回退到 H2 目录", exception);
        }
        return "h2";
    }
}



