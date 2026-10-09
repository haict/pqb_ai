package com.company.erp.support;

import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Xóa sạch schema rồi migrate lại từ đầu, để mỗi lần chạy test kiểm chứng toàn bộ migration
 * trên database trống. Từ chối chạy nếu schema đang kết nối không có hậu tố {@code _test}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CleanMigrateConfig {

    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            requireTestSchema(flyway);
            flyway.clean();
            flyway.migrate();
        };
    }

    private static void requireTestSchema(Flyway flyway) {
        String schema;
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            schema = connection.getCatalog();
        } catch (SQLException ex) {
            throw new IllegalStateException("Không kết nối được database test: " + ex.getMessage(), ex);
        }
        if (schema == null || !schema.endsWith("_test")) {
            throw new IllegalStateException("Từ chối xóa schema '" + schema
                    + "': test tích hợp chỉ chạy trên schema có hậu tố _test");
        }
    }
}
