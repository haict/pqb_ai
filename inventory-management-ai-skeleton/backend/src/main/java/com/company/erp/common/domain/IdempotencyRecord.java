package com.company.erp.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Kết quả đã xử lý của một {@code Idempotency-Key} (docs/03 mục 4.14).
 * Được ghi trong cùng transaction với nghiệp vụ: nếu nghiệp vụ rollback thì bản ghi cũng mất
 * và client được phép gửi lại cùng key.
 */
@Entity
@Table(name = "sys_idempotency_keys")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    // SHA-256 dạng hex luôn đủ 64 ký tự nên lưu CHAR(64).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(Long userId, String idempotencyKey, String requestHash, String endpoint,
                             Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.endpoint = endpoint;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void complete(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    /** Cùng key chỉ hợp lệ khi cùng endpoint và cùng nội dung yêu cầu. */
    public boolean matches(String endpoint, String requestHash) {
        return this.endpoint.equals(endpoint) && this.requestHash.equals(requestHash);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Integer responseStatus() {
        return responseStatus;
    }

    public String responseBody() {
        return responseBody;
    }
}
