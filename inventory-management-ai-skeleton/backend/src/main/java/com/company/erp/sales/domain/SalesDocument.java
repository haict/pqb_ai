package com.company.erp.sales.domain;

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
 * Cột và vòng đời chung của chứng từ bán hàng (BR mục 5.1, D-54): DRAFT → SUBMITTED → APPROVED; SUBMITTED → REJECTED →
 * sửa → gửi lại; hủy khi chưa ghi sổ. Ghi sổ / đảo ở lớp con. Chỉ sửa ở DRAFT / REJECTED. Nhân viên kinh doanh phụ
 * trách ({@code sales_user_id}) dùng cho phạm vi "chỉ dữ liệu của mình" và doanh số (BR-SAL-51).
 */
@MappedSuperclass
public abstract class SalesDocument extends AuditableEntity {

    protected static final Set<SalesDocumentStatus> EDITABLE =
            EnumSet.of(SalesDocumentStatus.DRAFT, SalesDocumentStatus.REJECTED);

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

    @Column(name = "sales_user_id")
    private Long salesUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SalesDocumentStatus status;

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

    protected SalesDocument() {
    }

    protected SalesDocument(Long companyId, Long branchId, String documentNo) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.documentNo = documentNo;
        this.status = SalesDocumentStatus.DRAFT;
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

    /** Nhân viên kinh doanh phụ trách (lấy theo khách / người lập). */
    public void assignSalesUser(Long salesUserId) {
        this.salesUserId = salesUserId;
    }

    public void submit(Long actorId, Instant now) {
        requireStatus(EDITABLE, "gửi duyệt");
        this.status = SalesDocumentStatus.SUBMITTED;
        this.submittedBy = actorId;
        this.submittedAt = now;
        this.reason = null;
    }

    public void approve(Long actorId, Instant now) {
        requireStatus(EnumSet.of(SalesDocumentStatus.SUBMITTED), "duyệt");
        this.status = SalesDocumentStatus.APPROVED;
        this.approvedBy = actorId;
        this.approvedAt = now;
    }

    public void reject(String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.SUBMITTED), "từ chối");
        this.status = SalesDocumentStatus.REJECTED;
        this.reason = reason;
    }

    /** Hủy khi chưa ghi sổ. */
    public void cancel(String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.DRAFT, SalesDocumentStatus.REJECTED,
                SalesDocumentStatus.SUBMITTED, SalesDocumentStatus.APPROVED), "hủy");
        this.status = SalesDocumentStatus.CANCELLED;
        this.reason = reason;
    }

    /** Chuyển trạng thái riêng của lớp con (đã kiểm tra trạng thái nguồn bằng {@link #requireStatus}). */
    protected void setStatus(SalesDocumentStatus status) {
        this.status = status;
    }

    protected void setReason(String reason) {
        this.reason = reason;
    }

    protected void requireStatus(Set<SalesDocumentStatus> allowed, String action) {
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

    public Long getSalesUserId() {
        return salesUserId;
    }

    public SalesDocumentStatus getStatus() {
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
