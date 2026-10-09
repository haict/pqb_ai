package com.company.erp.common.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình chống xử lý trùng.
 *
 * @param ttl thời gian giữ kết quả của một key; sau thời hạn này key được phép dùng lại
 */
@ConfigurationProperties(prefix = "erp.idempotency")
public record IdempotencyProperties(Duration ttl) {

    public IdempotencyProperties {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("erp.idempotency.ttl phải lớn hơn 0");
        }
    }
}
