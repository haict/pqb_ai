package com.company.erp.inventory.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chứng từ kho nhóm A (BR mục 5.1). Chuyển trạng thái chỉ qua phương thức domain, kiểm tra trạng thái nguồn
 * (business-integrity.md mục 1): DRAFT → SUBMITTED → APPROVED → POSTED; SUBMITTED → REJECTED → (sửa) → SUBMITTED;
 * chuyển kho khác chi nhánh APPROVED → IN_TRANSIT → POSTED; POSTED → REVERSED qua chứng từ đảo.
 */
@Entity
@Table(name = "inventory_documents")
public class InventoryDocument extends AuditableEntity {

    private static final Set<InventoryDocumentStatus> EDITABLE =
            EnumSet.of(InventoryDocumentStatus.DRAFT, InventoryDocumentStatus.REJECTED);
    private static final Set<InventoryDocumentStatus> CANCELLABLE = EnumSet.of(InventoryDocumentStatus.DRAFT,
            InventoryDocumentStatus.REJECTED, InventoryDocumentStatus.SUBMITTED, InventoryDocumentStatus.APPROVED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private InventoryDocumentType documentType;

    @Column(name = "sub_type", length = 30)
    private String subType;

    @Column(name = "document_no", nullable = false, length = 50, updatable = false)
    private String documentNo;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryDocumentStatus status;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "destination_warehouse_id")
    private Long destinationWarehouseId;

    @Column(name = "transit_warehouse_id")
    private Long transitWarehouseId;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "reason_id")
    private Long reasonId;

    @Column(name = "reason_text", length = 500)
    private String reasonText;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "recall_id")
    private Long recallId;

    @Column(name = "stock_count_id")
    private Long stockCountId;

    @Column(name = "issue_policy_id")
    private Long issuePolicyId;

    @Column(name = "source_type", updatable = false)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "source_no", updatable = false)
    private String sourceNo;

    @Column(name = "reversal_of_id", updatable = false)
    private Long reversalOfId;

    @Column(name = "reversed_by_id")
    private Long reversedById;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

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

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "receive_posting_date")
    private LocalDate receivePostingDate;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("lineNo")
    private List<InventoryDocumentLine> lines = new ArrayList<>();

    protected InventoryDocument() {
    }

    public InventoryDocument(Long companyId, Long branchId, InventoryDocumentType documentType, String documentNo,
                             Header header) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.documentType = documentType;
        this.documentNo = documentNo;
        this.status = InventoryDocumentStatus.DRAFT;
        applyHeader(header);
    }

    /** Thông tin đầu chứng từ sửa được khi còn nháp. */
    public record Header(String subType, LocalDate documentDate, LocalDate postingDate, Long warehouseId,
                         Long destinationWarehouseId, Long transitWarehouseId, Long partnerId, Long reasonId,
                         String reasonText, String note, Long recallId, Long stockCountId, Long issuePolicyId,
                         Long reversalOfId) {

        /** Loại nghiệp vụ do hệ thống gán cho phiếu sinh từ chứng từ nguồn module khác. */
        public Header withSubType(String value) {
            return new Header(value, documentDate, postingDate, warehouseId, destinationWarehouseId,
                    transitWarehouseId, partnerId, reasonId, reasonText, note, recallId, stockCountId, issuePolicyId,
                    reversalOfId);
        }
    }

    /** Sửa đầu chứng từ và thay toàn bộ dòng; chỉ ở DRAFT, REJECTED (BR-GEN-01). */
    public void edit(Header header, List<InventoryDocumentLine> newLines) {
        requireStatus(EDITABLE, "sửa");
        applyHeader(header);
        replaceLines(newLines);
    }

    /**
     * Bỏ toàn bộ dòng trước khi sửa. Phải flush trước khi thêm dòng mới: Hibernate chèn dòng mới trước khi xóa dòng
     * cũ, làm vi phạm unique (document_id, line_no).
     */
    public void clearLines() {
        requireStatus(EDITABLE, "sửa");
        lines.clear();
    }

    public void replaceLines(List<InventoryDocumentLine> newLines) {
        requireStatus(EDITABLE, "sửa");
        lines.clear();
        lines.addAll(newLines);
        totalAmount = newLines.stream().map(InventoryDocumentLine::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal::add).orElse(null);
    }

    public void submit(Long actorId, Instant now) {
        requireStatus(EDITABLE, "gửi duyệt");
        if (lines.isEmpty() && documentType != InventoryDocumentType.PDK) {
            throw BusinessException.invalidField("lines", "Chứng từ phải có ít nhất một dòng.");
        }
        this.status = InventoryDocumentStatus.SUBMITTED;
        this.submittedBy = actorId;
        this.submittedAt = now;
    }

    /** Duyệt xong (hoặc loại không cần duyệt): chứng từ sẵn sàng ghi sổ (BR-GEN-05). */
    public void approve(Long actorId, Instant now) {
        requireStatus(EnumSet.of(InventoryDocumentStatus.SUBMITTED), "duyệt");
        this.status = InventoryDocumentStatus.APPROVED;
        this.approvedBy = actorId;
        this.approvedAt = now;
    }

    public void reject() {
        requireStatus(EnumSet.of(InventoryDocumentStatus.SUBMITTED), "từ chối");
        this.status = InventoryDocumentStatus.REJECTED;
    }

    public void markPosted(Long actorId, Instant now) {
        requireStatus(EnumSet.of(InventoryDocumentStatus.APPROVED), "ghi sổ");
        this.status = InventoryDocumentStatus.POSTED;
        this.postedBy = actorId;
        this.postedAt = now;
    }

    /** Chuyển kho khác chi nhánh: đã xuất khỏi kho nguồn, hàng đang ở kho "đang vận chuyển" (BR-INV-30). */
    public void markShipped(Long actorId, Instant now) {
        requireStatus(EnumSet.of(InventoryDocumentStatus.APPROVED), "xuất chuyển kho");
        this.status = InventoryDocumentStatus.IN_TRANSIT;
        this.postedBy = actorId;
        this.postedAt = now;
    }

    public void markReceived(Long actorId, Instant now, LocalDate receivePostingDate) {
        requireStatus(EnumSet.of(InventoryDocumentStatus.IN_TRANSIT), "nhận hàng chuyển kho");
        this.status = InventoryDocumentStatus.POSTED;
        this.receivedBy = actorId;
        this.receivedAt = now;
        this.receivePostingDate = receivePostingDate;
    }

    public void cancel(Long actorId, Instant now, String reason) {
        requireStatus(CANCELLABLE, "hủy");
        this.status = InventoryDocumentStatus.CANCELLED;
        this.cancelledBy = actorId;
        this.cancelledAt = now;
        this.cancelReason = reason;
    }

    /** Đã có chứng từ đảo (đang chờ hoặc đã ghi sổ) thì không lập chứng từ đảo thứ hai. */
    public void requireReversible() {
        requireStatus(EnumSet.of(InventoryDocumentStatus.POSTED), "đảo");
        if (reversedById != null) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                    "Chứng từ " + documentNo + " đã có chứng từ đảo.");
        }
    }

    public void linkReversal(Long reversalId) {
        requireReversible();
        this.reversedById = reversalId;
    }

    /** Chứng từ đảo bị hủy / từ chối hẳn: bỏ liên kết để có thể lập lại. */
    public void unlinkReversal() {
        this.reversedById = null;
    }

    /**
     * Gắn chứng từ nguồn của module khác (ví dụ phiếu nhận hàng); chỉ khi lập. Chứng từ có nguồn chỉ đảo qua chứng từ
     * nguồn để chỉ tiêu nguồn được cập nhật cùng lúc (BR-GEN-21).
     */
    public void linkSource(String type, Long id, String number) {
        requireStatus(EDITABLE, "gắn chứng từ nguồn");
        this.sourceType = type;
        this.sourceId = id;
        this.sourceNo = number;
    }

    public void markReversed() {
        requireStatus(EnumSet.of(InventoryDocumentStatus.POSTED), "đảo");
        this.status = InventoryDocumentStatus.REVERSED;
    }

    private void applyHeader(Header header) {
        this.subType = header.subType();
        this.documentDate = header.documentDate();
        this.postingDate = header.postingDate();
        this.warehouseId = header.warehouseId();
        this.destinationWarehouseId = header.destinationWarehouseId();
        this.transitWarehouseId = header.transitWarehouseId();
        this.partnerId = header.partnerId();
        this.reasonId = header.reasonId();
        this.reasonText = header.reasonText();
        this.note = header.note();
        this.recallId = header.recallId();
        this.stockCountId = header.stockCountId();
        this.issuePolicyId = header.issuePolicyId();
        this.reversalOfId = header.reversalOfId();
    }

    private void requireStatus(Set<InventoryDocumentStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                    "Chứng từ " + documentNo + " ở trạng thái " + status + ", không " + action + " được.");
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

    public InventoryDocumentType getDocumentType() {
        return documentType;
    }

    public String getSubType() {
        return subType;
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

    public InventoryDocumentStatus getStatus() {
        return status;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getDestinationWarehouseId() {
        return destinationWarehouseId;
    }

    public Long getTransitWarehouseId() {
        return transitWarehouseId;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public Long getReasonId() {
        return reasonId;
    }

    public String getReasonText() {
        return reasonText;
    }

    public String getNote() {
        return note;
    }

    public Long getRecallId() {
        return recallId;
    }

    public Long getStockCountId() {
        return stockCountId;
    }

    public Long getIssuePolicyId() {
        return issuePolicyId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }

    public Long getReversedById() {
        return reversedById;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
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

    public Long getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public LocalDate getReceivePostingDate() {
        return receivePostingDate;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<InventoryDocumentLine> getLines() {
        return List.copyOf(lines);
    }
}
