package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Cột và vòng đời chung của chứng từ mua hàng (BR mục 5.1, D-54): DRAFT → SUBMITTED → APPROVED; SUBMITTED → REJECTED →
 * sửa → gửi lại; hủy khi chưa ghi sổ. Ghi sổ / đảo ở lớp con. Chỉ sửa ở DRAFT / REJECTED.
 */
@MappedSuperclass
public abstract class PurchaseDocument extends AuditableEntity {

    protected static final Set<PurchaseDocumentStatus> EDITABLE =
            EnumSet.of(PurchaseDocumentStatus.DRAFT, PurchaseDocumentStatus.REJECTED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "document_no", nullable = false, updatable = false)
    private String documentNo;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PurchaseDocumentStatus status;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "reason")
    private String reason;

    protected PurchaseDocument() {
    }

    protected PurchaseDocument(Long companyId, Long branchId, String documentNo) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.documentNo = documentNo;
        this.status = PurchaseDocumentStatus.DRAFT;
    }

    /** Mã loại chứng từ trong {@code sys_document_types} (luồng duyệt, số chứng từ). */
    public abstract String typeCode();

    /** Giá trị chứng từ để xét ngưỡng duyệt; {@code null} khi không áp dụng. */
    public abstract BigDecimal approvalAmount();

    protected void replaceHeader(LocalDate documentDate, String description) {
        requireStatus(EDITABLE, "sửa");
        this.documentDate = documentDate;
        this.description = description;
    }

    public void submit(Long actorId, Instant now) {
        requireStatus(EDITABLE, "gửi duyệt");
        this.status = PurchaseDocumentStatus.SUBMITTED;
        this.submittedBy = actorId;
        this.submittedAt = now;
        this.reason = null;
    }

    public void approve(Long actorId, Instant now) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.SUBMITTED), "duyệt");
        this.status = PurchaseDocumentStatus.APPROVED;
        this.approvedBy = actorId;
        this.approvedAt = now;
    }

    public void reject(String reason) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.SUBMITTED), "từ chối");
        this.status = PurchaseDocumentStatus.REJECTED;
        this.reason = reason;
    }

    /** Hủy khi chưa ghi sổ. */
    public void cancel(String reason) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.DRAFT, PurchaseDocumentStatus.REJECTED,
                PurchaseDocumentStatus.SUBMITTED, PurchaseDocumentStatus.APPROVED), "hủy");
        this.status = PurchaseDocumentStatus.CANCELLED;
        this.reason = reason;
    }

    /** Chuyển trạng thái riêng của lớp con (đã kiểm tra trạng thái nguồn bằng {@link #requireStatus}). */
    protected void setStatus(PurchaseDocumentStatus status) {
        this.status = status;
    }

    protected void setReason(String reason) {
        this.reason = reason;
    }

    protected void requireStatus(Set<PurchaseDocumentStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ " + documentNo
                    + " đang ở trạng thái " + status + ", không " + action + " được.");
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

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public String getDescription() {
        return description;
    }

    public PurchaseDocumentStatus getStatus() {
        return status;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getReason() {
        return reason;
    }
}
