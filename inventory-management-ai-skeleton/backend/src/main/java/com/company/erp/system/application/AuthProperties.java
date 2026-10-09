package com.company.erp.system.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình xác thực (D-38, BR-SYS-23). Thiếu secret JWT hoặc secret quá ngắn thì ứng dụng không khởi động.
 */
@ConfigurationProperties(prefix = "erp.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration sessionIdleTimeout,
        Duration sessionMaxDuration,
        String refreshCookieName,
        boolean refreshCookieSecure,
        Password password,
        Lockout lockout,
        BootstrapAdmin bootstrapAdmin) {

    private static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "erp.auth.jwt-secret (biến ERP_JWT_SECRET) bắt buộc và tối thiểu " + MIN_SECRET_BYTES + " byte");
        }
    }

    /** Chính sách mật khẩu (security.md mục 1). */
    public record Password(int minLength, boolean requireLetter, boolean requireDigit) {
    }

    /** Khóa tạm tài khoản khi đăng nhập sai nhiều lần. */
    public record Lockout(int maxFailedAttempts, Duration lockDuration) {
    }

    /** Quản trị viên đầu tiên cho môi trường chưa có người dùng. */
    public record BootstrapAdmin(String username, String password) {
    }
}
