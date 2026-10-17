package com.company.erp.system.application;

import com.company.erp.common.domain.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra license ký số Ed25519 (D-59). License có dạng {@code base64(payload JSON).base64(chữ ký)}; chữ ký tính trên
 * đúng chuỗi byte của payload. Khóa công khai tin cậy nằm trong resource {@value #KEY_RESOURCES} đóng gói cùng ứng
 * dụng (không đọc từ cấu hình ngoài, để khách không thay được khóa); private key chỉ nhà cung cấp giữ.
 */
@Component
public class LicenseVerifier {

    static final String KEY_RESOURCES = "classpath*:license/public-keys.properties";
    private static final Logger log = LoggerFactory.getLogger(LicenseVerifier.class);
    private static final String ALGORITHM = "Ed25519";

    private final ObjectMapper objectMapper;
    private final Map<String, PublicKey> trustedKeys;

    public LicenseVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.trustedKeys = loadTrustedKeys();
    }

    /** Kiểm tra chữ ký và đọc nội dung; sai thì lỗi trên trường {@code license}. */
    public LicensePayload verify(String license) {
        String[] parts = license == null ? new String[0] : license.trim().split("\\.");
        if (parts.length != 2) {
            throw invalid("License sai định dạng.");
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = Base64.getDecoder().decode(parts[0]);
            signature = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw invalid("License sai định dạng.");
        }
        JsonNode json = parse(payload);
        String keyId = text(json, "keyId");
        PublicKey key = trustedKeys.get(keyId);
        if (key == null) {
            throw invalid("License được ký bằng khóa không được tin cậy (" + keyId + ").");
        }
        if (!signatureValid(key, payload, signature)) {
            throw invalid("Chữ ký license không hợp lệ.");
        }
        return toPayload(keyId, json);
    }

    private JsonNode parse(byte[] payload) {
        try {
            return objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw invalid("Nội dung license không đọc được.");
        }
    }

    private static boolean signatureValid(PublicKey key, byte[] payload, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(payload);
            return verifier.verify(signature);
        } catch (GeneralSecurityException ex) {
            return false;
        }
    }

    private static LicensePayload toPayload(String keyId, JsonNode json) {
        List<String> taxCodes = new ArrayList<>();
        json.path("taxCodes").forEach(node -> taxCodes.add(node.asText().trim()));
        if (taxCodes.isEmpty()) {
            throw invalid("License không có mã số thuế công ty nào.");
        }
        try {
            return new LicensePayload(keyId, text(json, "licenseId"), json.path("customerName").asText(null),
                    List.copyOf(taxCodes), LocalDate.parse(text(json, "validTo")),
                    Math.max(0, json.path("graceDays").asInt(0)), Instant.parse(text(json, "issuedAt")));
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException business) {
                throw business;
            }
            throw invalid("Nội dung license không hợp lệ.");
        }
    }

    private static String text(JsonNode json, String field) {
        String value = json.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw invalid("License thiếu trường " + field + ".");
        }
        return value;
    }

    private static BusinessException invalid(String message) {
        return BusinessException.invalidField("license", message);
    }

    private static Map<String, PublicKey> loadTrustedKeys() {
        Map<String, PublicKey> keys = new HashMap<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(KEY_RESOURCES)) {
                Properties properties = new Properties();
                try (InputStream input = resource.getInputStream()) {
                    properties.load(input);
                }
                for (String keyId : properties.stringPropertyNames()) {
                    keys.put(keyId, publicKey(properties.getProperty(keyId).trim()));
                }
            }
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Không đọc được khóa công khai kiểm tra license", ex);
        }
        if (keys.isEmpty()) {
            log.warn("Chưa có khóa công khai kiểm tra license; hệ thống sẽ ở chế độ chỉ đọc.");
        }
        return Map.copyOf(keys);
    }

    private static PublicKey publicKey(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance(ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }
}
