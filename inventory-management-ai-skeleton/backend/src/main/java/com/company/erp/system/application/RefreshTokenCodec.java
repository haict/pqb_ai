package com.company.erp.system.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Sinh refresh token ngẫu nhiên 256 bit và băm SHA-256 để lưu (không lưu token gốc). */
@Component
class RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", ex);
        }
    }
}
