package com.company.erp.finance.domain;

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
 * Cột và vòng đời chung của chứng từ ghi sổ module công nợ, quỹ (BR mục 5.1, nhóm A): DRAFT → SUBMITTED → APPROVED →
 * POSTED; SUBMITTED → REJECTED → sửa → gửi lại; hủy khi chưa ghi sổ; đảo khi đã ghi sổ. Chỉ sửa ở DRAFT / REJECTED.
 */
@MappedSuperclass
public abstract class FinanceDocument extends AuditableEntity {

    protected static final Set<FinanceDocumentStatus> EDITABLE =
            EnumSet.of(FinanceDocumentStatus.DRAFT, FinanceDocumentStatus.REJECTED);

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

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FinanceDocumentStatus status;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_date")
    private LocalDate reverseDate;

    @Column(name = "reason")
    private String reason;

    protected FinanceDocument() {
    }

    protected FinanceDocument(Long companyId, Long branchId, String documentNo) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.documentNo = documentNo;
        this.status = FinanceDocumentStatus.DRAFT;
    }

    /** Mã loại chứng từ trong {@code sys_document_types} (luồng duyệt, số chứng từ, nguồn sổ phụ, bút toán). */
    public abstract String typeCode();

    /** Giá trị chứng từ để xét ngưỡng duyệt. */
    public abstract BigDecimal approvalAmount();

    /** Ngày, diễn giải chung khi lập / sửa (chỉ DRAFT / REJECTED). */
    protected void replaceHeader(LocalDate documentDate, LocalDate postingDate, String description) {
        requireStatus(EDITABLE, "sửa");
        this.documentDate = documentDate;
        this.postingDate = postingDate;
        this.description = description;
    }

    public void submit(Long actorId, Instant now) {
        requireStatus(EDITABLE, "gửi duyệt");
        this.status = FinanceDocumentStatus.SUBMITTED;
        this.submittedBy = actorId;
        this.submittedAt = now;
        this.reason = null;
    }

    public void approve(Long actorId, Instant now) {
        requireStatus(EnumSet.of(FinanceDocumentStatus.SUBMITTED), "duyệt");
        this.status = FinanceDocumentStatus.APPROVED;
        this.approvedBy = actorId;
        this.approvedAt = now;
    }

    public void reject(String reason) {
        requireStatus(EnumSet.of(FinanceDocumentStatus.SUBMITTED), "từ chối");
        this.status = FinanceDocumentStatus.REJECTED;
        this.reason = reason;
    }

    public void cancel(String reason) {
        requireStatus(EnumSet.of(FinanceDocumentStatus.DRAFT, FinanceDocumentStatus.REJECTED,
                FinanceDocumentStatus.SUBMITTED, FinanceDocumentStatus.APPROVED), "hủy");
        this.status = FinanceDocumentStatus.CANCELLED;
        this.reason = reason;
    }

    public void markPosted(Long actorId, Instant now) {
        markPosted(EnumSet.of(FinanceDocumentStatus.APPROVED), actorId, now);
    }

    /** Ghi sổ từ các trạng thái cho phép (chứng từ không qua duyệt được ghi sổ thẳng từ DRAFT). */
    protected void markPosted(Set<FinanceDocumentStatus> allowed, Long actorId, Instant now) {
        requireStatus(allowed, "ghi sổ");
        this.status = FinanceDocumentStatus.POSTED;
        this.postedBy = actorId;
        this.postedAt = now;
    }

    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(FinanceDocumentStatus.POSTED), "đảo");
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        this.status = FinanceDocumentStatus.REVERSED;
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        this.reason = reason;
    }

    protected void requireStatus(Set<FinanceDocumentStatus> allowed, String action) {
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

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public String getDescription() {
        return description;
    }

    public FinanceDocumentStatus getStatus() {
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

    public Long getPostedBy() {
        return postedBy;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Long getReversedBy() {
        return reversedBy;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public LocalDate getReverseDate() {
        return reverseDate;
    }

    public String getReason() {
        return reason;
    }
}
