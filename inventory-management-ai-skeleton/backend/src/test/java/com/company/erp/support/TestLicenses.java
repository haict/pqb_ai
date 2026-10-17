package com.company.erp.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/** Ký license cho test bằng khóa "test" (chỉ có trong resource test), cùng định dạng công cụ tools/license-signer. */
public final class TestLicenses {

    private TestLicenses() {
    }

    public static String sign(String licenseId, List<String> taxCodes, LocalDate validTo, int graceDays,
                              Instant issuedAt) {
        return sign(testKey(), "test", licenseId, taxCodes, validTo, graceDays, issuedAt);
    }

    /** License ký bằng một khóa ngẫu nhiên (không được tin cậy) nhưng ghi keyId "test". */
    public static String signWithUntrustedKey(String licenseId, LocalDate validTo) {
        try {
            PrivateKey key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate();
            return sign(key, "test", licenseId, List.of("*"), validTo, 0, Instant.now());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Bản sửa ngày hết hạn trong payload của license hợp lệ, giữ nguyên chữ ký cũ. */
    public static String tamperValidTo(String license, LocalDate newValidTo) {
        String[] parts = license.split("\\.");
        String payload = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8)
                .replaceFirst("\"validTo\":\"[0-9-]+\"", "\"validTo\":\"" + newValidTo + "\"");
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[1];
    }

    private static String sign(PrivateKey key, String keyId, String licenseId, List<String> taxCodes,
                               LocalDate validTo, int graceDays, Instant issuedAt) {
        String codes = taxCodes.stream().map(code -> "\"" + code + "\"").collect(Collectors.joining(","));
        String payload = "{\"keyId\":\"" + keyId + "\",\"licenseId\":\"" + licenseId
                + "\",\"customerName\":\"Khách kiểm thử\",\"taxCodes\":[" + codes + "],\"validTo\":\"" + validTo
                + "\",\"graceDays\":" + graceDays + ",\"issuedAt\":\"" + issuedAt + "\"}";
        try {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(bytes);
            return Base64.getEncoder().encodeToString(bytes) + "." + Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static PrivateKey testKey() {
        try (InputStream input = TestLicenses.class.getResourceAsStream("/license/test.private.key")) {
            byte[] encoded = Base64.getDecoder().decode(new String(input.readAllBytes(), StandardCharsets.UTF_8).trim());
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Không đọc được khóa test", ex);
        }
    }
}
