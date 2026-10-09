package com.company.erp.system.application;

import java.time.Instant;

/**
 * Kết quả đăng nhập / làm mới phiên. {@code refreshToken} là giá trị gốc, chỉ trả cho client qua cookie
 * httpOnly và không bao giờ được ghi log.
 */
public record SessionTokens(
        Long userId,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant sessionExpiresAt) {

    @Override
    public String toString() {
        return "SessionTokens[userId=" + userId + "]";
    }
}
