package com.company.erp.system.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Ủy quyền duyệt có thời hạn (BR-SYS-11): người được ủy quyền duyệt thay trong khoảng thời gian. */
@Entity
@Table(name = "approval_delegations")
public class ApprovalDelegation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "delegator_user_id", nullable = false, updatable = false)
    private Long delegatorUserId;

    @Column(name = "delegate_user_id", nullable = false, updatable = false)
    private Long delegateUserId;

    @Column(name = "document_type", length = 30, updatable = false)
    private String documentType;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DelegationStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    protected ApprovalDelegation() {
    }

    public ApprovalDelegation(Long companyId, Long delegatorUserId, Long delegateUserId, String documentType,
                              Instant validFrom, Instant validTo, String reason) {
        if (delegatorUserId.equals(delegateUserId)) {
            throw BusinessException.invalidField("delegateUserId", "Không ủy quyền cho chính mình.");
        }
        if (!validFrom.isBefore(validTo)) {
            throw BusinessException.invalidField("validTo", "Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }
        this.companyId = companyId;
        this.delegatorUserId = delegatorUserId;
        this.delegateUserId = delegateUserId;
        this.documentType = documentType;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.reason = reason;
        this.status = DelegationStatus.ACTIVE;
    }

    /** Thu hồi: hết hiệu lực ngay, giữ lại bản ghi để truy vết. */
    public void revoke() {
        if (status != DelegationStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Ủy quyền đã bị thu hồi.");
        }
        this.status = DelegationStatus.REVOKED;
    }

    public boolean isEffective(Instant now, String forDocumentType) {
        return status == DelegationStatus.ACTIVE && !now.isBefore(validFrom) && now.isBefore(validTo)
                && (documentType == null || documentType.equals(forDocumentType));
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getDelegatorUserId() {
        return delegatorUserId;
    }

    public Long getDelegateUserId() {
        return delegateUserId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public DelegationStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
