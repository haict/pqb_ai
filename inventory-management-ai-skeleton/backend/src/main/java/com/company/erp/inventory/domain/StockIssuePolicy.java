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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Chính sách cho phép xuất vượt tồn khả dụng (lấy phần đang giữ chỗ cho chứng từ khác) trong giới hạn số lượng và
 * thời hạn (BR-GEN-31, D-10). Không qua duyệt (D-61): người có quyền {@code stock-policy:activate} (mặc định Giám đốc)
 * kích hoạt từ nháp; trạng thái {@code APPROVED} nghĩa là có hiệu lực. Mỗi chính sách chỉ dùng cho một chứng từ xuất (D-40, D-53). Tồn thực tế
 * vẫn không bao giờ âm.
 */
@Entity
@Table(name = "stock_issue_policies")
public class StockIssuePolicy extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "document_no", nullable = false, length = 50, updatable = false)
    private String documentNo;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "product_id", updatable = false)
    private Long productId;

    @Column(name = "max_quantity", nullable = false, precision = 19, scale = 3, updatable = false)
    private BigDecimal maxQuantity;

    @Column(name = "used_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal usedQuantity;

    @Column(name = "used_by_document_id")
    private Long usedByDocumentId;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false, updatable = false)
    private LocalDate validTo;

    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    protected StockIssuePolicy() {
    }

    @SuppressWarnings("java:S107")
    public StockIssuePolicy(Long companyId, Long branchId, String documentNo, Long warehouseId, Long productId,
                            BigDecimal maxQuantity, LocalDate validFrom, LocalDate validTo, String reason) {
        if (validTo.isBefore(validFrom)) {
            throw BusinessException.invalidField("validTo", "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        this.companyId = companyId;
        this.branchId = branchId;
        this.documentNo = documentNo;
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.maxQuantity = maxQuantity;
        this.usedQuantity = BigDecimal.ZERO;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.reason = reason;
        this.status = PolicyStatus.DRAFT;
    }

    /** Kích hoạt chính sách từ nháp (D-61 thay bước Giám đốc duyệt bằng quyền kích hoạt). */
    public void activate(Long actorId, Instant now) {
        requireStatus(EnumSet.of(PolicyStatus.DRAFT), "kích hoạt");
        this.status = PolicyStatus.APPROVED;
        this.approvedBy = actorId;
        this.approvedAt = now;
    }

    public void cancel(String reason) {
        requireStatus(EnumSet.of(PolicyStatus.DRAFT, PolicyStatus.REJECTED, PolicyStatus.SUBMITTED,
                PolicyStatus.APPROVED), "hủy");
        if (usedByDocumentId != null) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                    "Chính sách " + documentNo + " đã được dùng cho một chứng từ xuất, không hủy được.");
        }
        this.status = PolicyStatus.CANCELLED;
        this.cancelReason = reason;
    }

    /** Chính sách có hiệu lực, chưa dùng (hoặc đã dùng cho đúng chứng từ này) thì chứng từ được tham chiếu. */
    public boolean usableBy(Long documentId) {
        return status == PolicyStatus.APPROVED
                && (usedByDocumentId == null || usedByDocumentId.equals(documentId));
    }

    /**
     * Dùng chính sách cho phần xuất vượt của một chứng từ; ngoài phạm vi, hết hạn, hết hạn mức hoặc đã dùng cho chứng
     * từ khác thì từ chối (D-53).
     */
    public void consume(Long documentId, Long forWarehouseId, Long forProductId, LocalDate postingDate,
                        BigDecimal quantity) {
        if (usedByDocumentId != null && !usedByDocumentId.equals(documentId)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "Chính sách " + documentNo + " đã được dùng cho chứng từ khác; mỗi chính sách dùng cho một "
                            + "chứng từ xuất.");
        }
        boolean applicable = status == PolicyStatus.APPROVED && warehouseId.equals(forWarehouseId)
                && (productId == null || productId.equals(forProductId))
                && !postingDate.isBefore(validFrom) && !postingDate.isAfter(validTo);
        if (!applicable) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "Chính sách " + documentNo + " không áp dụng cho kho, hàng hoặc ngày ghi sổ này.");
        }
        BigDecimal remaining = maxQuantity.subtract(usedQuantity);
        if (quantity.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Chính sách " + documentNo
                    + " chỉ còn cho xuất vượt " + remaining.stripTrailingZeros().toPlainString() + ".");
        }
        this.usedQuantity = usedQuantity.add(quantity);
        this.usedByDocumentId = documentId;
    }

    private void requireStatus(Set<PolicyStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE,
                    "Chính sách " + documentNo + " ở trạng thái " + status + ", không " + action + " được.");
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

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getMaxQuantity() {
        return maxQuantity;
    }

    public BigDecimal getUsedQuantity() {
        return usedQuantity;
    }

    public Long getUsedByDocumentId() {
        return usedByDocumentId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getReason() {
        return reason;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
