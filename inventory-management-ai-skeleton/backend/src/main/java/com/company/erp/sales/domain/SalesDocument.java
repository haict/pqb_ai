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
 * Cột và vòng đời chung của chứng từ bán hàng (BR mục 5.1): DRAFT → SUBMITTED → APPROVED; SUBMITTED → REJECTED →
 * sửa → gửi lại; hủy khi chưa phát sinh chứng từ đích. Chứng từ điều phối (đơn bán) chuyển IN_PROGRESS / COMPLETED theo
 * tiến độ dòng, đóng phần còn lại (CLOSED) có lý do. Chỉ sửa ở DRAFT / REJECTED. Nhân viên kinh doanh phụ trách
 * ({@code sales_user_id}) dùng cho phạm vi "chỉ dữ liệu của mình" và doanh số (BR-SAL-51).
 */
@MappedSuperclass
public abstract class SalesDocument extends AuditableEntity {

    protected static final Set<SalesDocumentStatus> EDITABLE =
            EnumSet.of(SalesDocumentStatus.DRAFT, SalesDocumentStatus.REJECTED);
    /** Đã duyệt, còn chờ chứng từ đích. */
    public static final Set<SalesDocumentStatus> OPEN =
            EnumSet.of(SalesDocumentStatus.APPROVED, SalesDocumentStatus.IN_PROGRESS);

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

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

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

    /** Mọi dòng đã nhận đủ / đóng: dùng để chuyển COMPLETED. */
    protected abstract boolean fulfilled();

    /** Có dòng đã phát sinh chứng từ đích (không hủy được, chỉ đóng phần còn lại). */
    protected abstract boolean hasProgress();

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

    /** Hủy khi chưa phát sinh chứng từ đích. */
    public void cancel(String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.DRAFT, SalesDocumentStatus.REJECTED,
                SalesDocumentStatus.SUBMITTED, SalesDocumentStatus.APPROVED), "hủy");
        if (hasProgress()) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Chứng từ " + documentNo
                    + " đã phát sinh chứng từ đích, không hủy được; dùng đóng phần còn lại.");
        }
        this.status = SalesDocumentStatus.CANCELLED;
        this.reason = reason;
    }

    /** Đóng phần còn lại có lý do (BR-PUR-05); dòng chưa đủ được đóng ở lớp con trước khi gọi. */
    protected void markClosed(Long actorId, Instant now, String reason) {
        markClosed(OPEN, actorId, now, reason);
    }

    /** Đóng từ các trạng thái {@code allowed} (báo giá thay bằng phiên bản mới). */
    protected void markClosed(Set<SalesDocumentStatus> allowed, Long actorId, Instant now, String reason) {
        requireStatus(allowed, "đóng");
        this.status = SalesDocumentStatus.CLOSED;
        this.closedBy = actorId;
        this.closedAt = now;
        this.reason = reason;
    }

    /** Cập nhật IN_PROGRESS / COMPLETED theo tiến độ dòng sau khi chứng từ đích thay đổi. */
    public void refreshProgress() {
        if (!OPEN.contains(status) && status != SalesDocumentStatus.COMPLETED) {
            return;
        }
        if (fulfilled()) {
            status = SalesDocumentStatus.COMPLETED;
        } else {
            status = hasProgress() ? SalesDocumentStatus.IN_PROGRESS : SalesDocumentStatus.APPROVED;
        }
    }

    /** Chuyển trạng thái riêng của lớp con (đã kiểm tra trạng thái nguồn bằng {@link #requireStatus}). */
    protected void setStatus(SalesDocumentStatus status) {
        this.status = status;
    }

    protected void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isOpen() {
        return OPEN.contains(status);
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

    public Long getClosedBy() {
        return closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getReason() {
        return reason;
    }
}
