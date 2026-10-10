package com.company.erp.system.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Giới hạn số lần đăng nhập sai theo địa chỉ IP trong một cửa sổ thời gian (security.md mục 7).
 *
 * @param maxFailures số lần sai tối đa trong {@code window}; vượt thì từ chối đăng nhập từ IP đó
 * @param window      độ dài cửa sổ trượt
 */
@ConfigurationProperties(prefix = "erp.auth.ip-rate-limit")
public record LoginRateLimitProperties(int maxFailures, Duration window) {

    public LoginRateLimitProperties {
        if (maxFailures < 1 || window == null || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("erp.auth.ip-rate-limit cấu hình không hợp lệ");
        }
    }
}
