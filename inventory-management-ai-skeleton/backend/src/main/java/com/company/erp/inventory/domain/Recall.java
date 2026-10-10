package com.company.erp.inventory.domain;

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
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Phiếu thu hồi một lô hoặc serial (BR-INV-42). Duyệt xong thì lô / serial bị khóa trên toàn bộ kho. */
@Entity
@Table(name = "recalls")
public class Recall extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "document_no", nullable = false, length = 50, updatable = false)
    private String documentNo;

    @Column(name = "recall_date", nullable = false)
    private LocalDate recallDate;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "lot_id", updatable = false)
    private Long lotId;

    @Column(name = "serial_id", updatable = false)
    private Long serialId;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecallStatus status;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    protected Recall() {
    }

    public Recall(Long companyId, Long branchId, String documentNo, LocalDate recallDate, Long productId, Long lotId,
                  Long serialId, String reason) {
        if (lotId == null && serialId == null) {
            throw BusinessException.invalidField("lotId", "Chọn lô hoặc serial cần thu hồi.");
        }
        this.companyId = companyId;
        this.branchId = branchId;
        this.documentNo = documentNo;
        this.recallDate = recallDate;
        this.productId = productId;
        this.lotId = lotId;
        this.serialId = serialId;
        this.reason = reason;
        this.status = RecallStatus.DRAFT;
    }

    public void submit(Long actorId, Instant now) {
        requireStatus(EnumSet.of(RecallStatus.DRAFT, RecallStatus.REJECTED), "gửi duyệt");
        this.status = RecallStatus.SUBMITTED;
        this.submittedBy = actorId;
        this.submittedAt = now;
    }

    public void activate(Long actorId, Instant now) {
        requireStatus(EnumSet.of(RecallStatus.SUBMITTED), "duyệt");
        this.status = RecallStatus.ACTIVE;
        this.approvedBy = actorId;
        this.approvedAt = now;
    }

    public void reject() {
        requireStatus(EnumSet.of(RecallStatus.SUBMITTED), "từ chối");
        this.status = RecallStatus.REJECTED;
    }

    public void close(Long actorId, Instant now) {
        requireStatus(EnumSet.of(RecallStatus.ACTIVE), "đóng");
        this.status = RecallStatus.CLOSED;
        this.closedBy = actorId;
        this.closedAt = now;
    }

    public void cancel(String reason) {
        requireStatus(EnumSet.of(RecallStatus.DRAFT, RecallStatus.REJECTED, RecallStatus.SUBMITTED), "hủy");
        this.status = RecallStatus.CANCELLED;
        this.cancelReason = reason;
    }

    private void requireStatus(Set<RecallStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                    "Phiếu thu hồi " + documentNo + " ở trạng thái " + status + ", không " + action + " được.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public LocalDate getRecallDate() {
        return recallDate;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getLotId() {
        return lotId;
    }

    public Long getSerialId() {
        return serialId;
    }

    public String getReason() {
        return reason;
    }

    public RecallStatus getStatus() {
        return status;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
