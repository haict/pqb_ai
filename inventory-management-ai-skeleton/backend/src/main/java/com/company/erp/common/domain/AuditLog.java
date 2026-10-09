package com.company.erp.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một dòng nhật ký thao tác (docs/03 mục 4.15). Chỉ INSERT; {@link Immutable} chặn Hibernate cập nhật.
 */
@Entity
@Immutable
@Table(name = "sys_audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "document_no", length = 50)
    private String documentNo;

    @Column(name = "reason", length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value")
    private String beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value")
    private String afterValue;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "branch_id")
    private Long branchId;

    protected AuditLog() {
    }

    public AuditLog(Instant occurredAt, Long actorUserId, String action, String entityType, Long entityId,
                    String documentNo, String reason, String beforeValue, String afterValue,
                    String requestId, String ipAddress, Long companyId, Long branchId) {
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.documentNo = documentNo;
        this.reason = reason;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.companyId = companyId;
        this.branchId = branchId;
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getReason() {
        return reason;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public String getRequestId() {
        return requestId;
    }
}
