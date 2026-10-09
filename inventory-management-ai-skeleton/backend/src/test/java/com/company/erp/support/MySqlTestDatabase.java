package com.company.erp.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Nguồn MySQL 8 thật cho test tích hợp (testing.md mục 1), theo thứ tự ưu tiên:
 * <ol>
 *   <li>Biến môi trường {@code ERP_TEST_DB_URL}, {@code ERP_TEST_DB_USERNAME}, {@code ERP_TEST_DB_PASSWORD}
 *       (dùng trên CI).</li>
 *   <li>File cấu hình máy local {@code ~/.erp/application-local.yml} (hoặc {@code ERP_CONFIG_FILE}),
 *       phần {@code on-profile: test}.</li>
 * </ol>
 * Schema phải có hậu tố {@code _test} vì test xóa sạch schema trước khi migrate ({@link CleanMigrateConfig}).
 * Không có nguồn nào thì test tích hợp bị bỏ qua (skipped), không báo đạt.
 */
public final class MySqlTestDatabase {

    private static final String ENV_URL = System.getenv("ERP_TEST_DB_URL");

    private MySqlTestDatabase() {
    }

    /** Dùng bởi {@code @EnabledIf} của {@link MySqlIntegrationTest}. */
    public static boolean isAvailable() {
        return ENV_URL != null || Files.isRegularFile(localConfigFile());
    }

    private static Path localConfigFile() {
        String configured = System.getenv("ERP_CONFIG_FILE");
        if (configured != null) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".erp", "application-local.yml");
    }

    /** Biến môi trường ghi đè cấu hình trong file local. */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            if (ENV_URL == null) {
                return;
            }
            TestPropertyValues.of(
                    "spring.datasource.url=" + ENV_URL,
                    "spring.datasource.username=" + System.getenv("ERP_TEST_DB_USERNAME"),
                    "spring.datasource.password=" + System.getenv("ERP_TEST_DB_PASSWORD")
            ).applyTo(context.getEnvironment());
        }
    }
}
