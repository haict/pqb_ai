package com.company.erp.finance.domain;

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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Khoản công nợ theo chứng từ nguồn (BR-AR-01, BR-AP-01, ADR-12): số gốc, đã thanh toán, còn lại, hạn thanh toán.
 * Theo dõi theo từng hóa đơn để tính hạn và tuổi nợ. Số tiền chỉ đổi qua ghi sổ chứng từ (thu, chi, đối trừ, điều
 * chỉnh, đảo); không sửa trực tiếp.
 */
@Entity
@Table(name = "debt_items")
public class DebtItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", nullable = false, updatable = false)
    private DebtType debtType;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private Long partnerId;

    @Column(name = "source_type", nullable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "source_line_id", updatable = false)
    private Long sourceLineId;

    @Column(name = "source_no", nullable = false, updatable = false)
    private String sourceNo;

    @Column(name = "invoice_no", updatable = false)
    private String invoiceNo;

    @Column(name = "document_date", nullable = false, updatable = false)
    private LocalDate documentDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "original_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal settledAmount;

    @Column(name = "sales_user_id", updatable = false)
    private Long salesUserId;

    @Column(name = "description", updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DebtItemStatus status;

    protected DebtItem() {
    }

    public record Source(Long companyId, Long branchId, DebtType debtType, Long partnerId, String sourceType,
                         Long sourceId, Long sourceLineId, String sourceNo, String invoiceNo, LocalDate documentDate,
                         LocalDate dueDate, BigDecimal amount, Long salesUserId, String description) {
    }

    public static DebtItem open(Source source) {
        if (source.amount() == null || source.amount().signum() <= 0) {
            throw BusinessException.invalidField("amount", "Số tiền công nợ phải lớn hơn 0.");
        }
        if (source.dueDate().isBefore(source.documentDate())) {
            throw BusinessException.invalidField("dueDate", "Hạn thanh toán không được trước ngày chứng từ.");
        }
        DebtItem item = new DebtItem();
        item.companyId = source.companyId();
        item.branchId = source.branchId();
        item.debtType = source.debtType();
        item.partnerId = source.partnerId();
        item.sourceType = source.sourceType();
        item.sourceId = source.sourceId();
        item.sourceLineId = source.sourceLineId();
        item.sourceNo = source.sourceNo();
        item.invoiceNo = source.invoiceNo();
        item.documentDate = source.documentDate();
        item.dueDate = source.dueDate();
        item.originalAmount = source.amount();
        item.settledAmount = BigDecimal.ZERO;
        item.salesUserId = source.salesUserId();
        item.description = source.description();
        item.status = DebtItemStatus.OPEN;
        return item;
    }

    /** Đảo chứng từ nguồn: chỉ khi chưa có khoản thanh toán / đối trừ nào (BR-GEN-21, chặn khi có phụ thuộc). */
    public void reverse() {
        if (status == DebtItemStatus.REVERSED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Khoản công nợ " + label() + " đã đảo.");
        }
        if (settledAmount.signum() != 0) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Khoản công nợ " + label()
                    + " đã được thanh toán / đối trừ; đảo các chứng từ thanh toán trước.");
        }
        this.status = DebtItemStatus.REVERSED;
    }

    /** Ghi nhận khoản thanh toán / đối trừ vào khoản công nợ; không vượt số còn lại (BR-GEN-52). */
    public void settle(BigDecimal amount) {
        if (status == DebtItemStatus.REVERSED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Khoản công nợ " + label() + " đã đảo.");
        }
        if (amount.signum() <= 0 || amount.compareTo(getRemainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.OVER_ALLOCATION, "Hóa đơn " + label() + " còn "
                    + getRemainingAmount().stripTrailingZeros().toPlainString() + ", không phân bổ được "
                    + amount.stripTrailingZeros().toPlainString() + ".");
        }
        this.settledAmount = settledAmount.add(amount);
        refreshStatus();
    }

    /** Hoàn lại phần đã thanh toán khi đảo chứng từ thanh toán / đối trừ. */
    public void unsettle(BigDecimal amount) {
        if (amount.compareTo(settledAmount) > 0) {
            throw new IllegalStateException("Hoàn phân bổ vượt số đã thanh toán của khoản công nợ #" + id);
        }
        this.settledAmount = settledAmount.subtract(amount);
        refreshStatus();
    }

    private void refreshStatus() {
        if (settledAmount.signum() == 0) {
            this.status = DebtItemStatus.OPEN;
        } else if (settledAmount.compareTo(originalAmount) == 0) {
            this.status = DebtItemStatus.SETTLED;
        } else {
            this.status = DebtItemStatus.PARTIAL;
        }
    }

    /** Số ngày quá hạn tại ngày {@code asOf}; chưa đến hạn trả 0. */
    public long overdueDays(LocalDate asOf) {
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, asOf));
    }

    public String label() {
        return invoiceNo != null ? invoiceNo : sourceNo;
    }

    public BigDecimal getRemainingAmount() {
        return originalAmount.subtract(settledAmount);
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

    public DebtType getDebtType() {
        return debtType;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getSourceLineId() {
        return sourceLineId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public Long getSalesUserId() {
        return salesUserId;
    }

    public String getDescription() {
        return description;
    }

    public DebtItemStatus getStatus() {
        return status;
    }
}
