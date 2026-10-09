package com.company.erp.system.domain;

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
 * Refresh token đã phát hành (chỉ lưu băm). Mỗi phiên đăng nhập là một {@code familyId};
 * mỗi lần làm mới, token cũ bị đánh dấu đã dùng và token mới cùng family được phát hành.
 */
@Entity
@Table(name = "sys_refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "family_id", nullable = false, length = 36, updatable = false)
    private String familyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "session_expires_at", nullable = false, updatable = false)
    private Instant sessionExpiresAt;

    @Column(name = "idle_expires_at", nullable = false, updatable = false)
    private Instant idleExpiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 30)
    private String revokeReason;

    @Column(name = "created_ip", length = 45, updatable = false)
    private String createdIp;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    protected RefreshToken() {
    }

    public RefreshToken(Long userId, String familyId, String tokenHash, Instant issuedAt, Instant sessionExpiresAt,
                        Instant idleExpiresAt, String createdIp, String userAgent) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.sessionExpiresAt = sessionExpiresAt;
        this.idleExpiresAt = idleExpiresAt;
        this.createdIp = createdIp;
        this.userAgent = userAgent;
    }

    /** Còn dùng được: chưa dùng, chưa thu hồi, chưa quá hạn phiên và hạn không thao tác. */
    public boolean isUsableAt(Instant now) {
        return usedAt == null && revokedAt == null
                && now.isBefore(sessionExpiresAt) && now.isBefore(idleExpiresAt);
    }

    /** Token đã được dùng để làm mới mà vẫn bị gửi lại: dấu hiệu bị đánh cắp. */
    public boolean isReplayed() {
        return usedAt != null;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public void revoke(Instant now, RevokeReason reason) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.revokeReason = reason.name();
        }
    }

    public Long getUserId() {
        return userId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Instant getSessionExpiresAt() {
        return sessionExpiresAt;
    }

    public enum RevokeReason {
        LOGOUT,
        REUSE_DETECTED,
        PASSWORD_CHANGED,
        USER_DEACTIVATED
    }
}
