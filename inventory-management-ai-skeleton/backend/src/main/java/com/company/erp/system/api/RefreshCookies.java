package com.company.erp.system.api;

import com.company.erp.system.application.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie chứa refresh token: httpOnly (JS không đọc được), SameSite=Strict, chỉ gửi tới /api/v1/auth.
 */
@Component
class RefreshCookies {

    private static final String AUTH_PATH = "/api/v1/auth";

    private final AuthProperties properties;
    private final Clock clock;

    RefreshCookies(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    String name() {
        return properties.refreshCookieName();
    }

    ResponseCookie issue(HttpServletRequest request, String refreshToken, Instant sessionExpiresAt) {
        Duration maxAge = Duration.between(Instant.now(clock), sessionExpiresAt);
        return base(request, refreshToken).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    ResponseCookie clear(HttpServletRequest request) {
        return base(request, "").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(HttpServletRequest request, String value) {
        return ResponseCookie.from(properties.refreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(request.getContextPath() + AUTH_PATH);
    }
}
