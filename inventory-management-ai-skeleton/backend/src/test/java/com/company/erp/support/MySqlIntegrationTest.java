package com.company.erp.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Test tích hợp trên MySQL 8 thật với toàn bộ Spring context (xem {@link MySqlTestDatabase}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// Secret chỉ dùng cho test, không dùng ở môi trường nào khác.
@SpringBootTest(properties = "erp.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@ActiveProfiles("test")
@EnabledIf(value = "com.company.erp.support.MySqlTestDatabase#isAvailable",
        disabledReason = "Không có ~/.erp/application-local.yml và không đặt ERP_TEST_DB_URL")
@ContextConfiguration(initializers = MySqlTestDatabase.Initializer.class)
@Import(CleanMigrateConfig.class)
public @interface MySqlIntegrationTest {
}
