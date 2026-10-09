package com.company.erp.system.application.dto;

import java.time.Instant;

/** Access token trả trong body; refresh token chỉ nằm trong cookie httpOnly. */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant sessionExpiresAt,
        MeResponse user) {
}
