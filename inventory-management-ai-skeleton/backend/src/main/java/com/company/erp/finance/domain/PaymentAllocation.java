package com.company.erp.finance.domain;

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

/**
 * Phân bổ thanh toán vào một khoản công nợ (BR-AR-03): nguồn tổng quát (phiếu thu / chi, đối trừ). Truy vết hai chiều
 * hóa đơn ↔ chứng từ thanh toán; đảo chứng từ nguồn chuyển {@code REVERSED}.
 */
@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "debt_item_id", nullable = false, updatable = false)
    private Long debtItemId;

    @Column(name = "source_type", nullable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @Column(name = "source_line_id", updatable = false)
    private Long sourceLineId;

    @Column(name = "source_no", nullable = false, updatable = false)
    private String sourceNo;

    @Column(name = "posting_date", nullable = false, updatable = false)
    private LocalDate postingDate;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentAllocationStatus status;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAllocation() {
    }

    public record Source(String type, Long id, Long lineId, String number) {
    }

    public static PaymentAllocation of(DebtItem item, Source source, LocalDate postingDate, BigDecimal amount,
                                       Long actorId, Instant now) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.companyId = item.getCompanyId();
        allocation.branchId = item.getBranchId();
        allocation.debtItemId = item.getId();
        allocation.sourceType = source.type();
        allocation.sourceId = source.id();
        allocation.sourceLineId = source.lineId();
        allocation.sourceNo = source.number();
        allocation.postingDate = postingDate;
        allocation.amount = amount;
        allocation.status = PaymentAllocationStatus.ACTIVE;
        allocation.createdBy = actorId;
        allocation.createdAt = now;
        return allocation;
    }

    public void reverse(Long actorId, Instant now) {
        if (status != PaymentAllocationStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phân bổ của " + sourceNo + " đã đảo.");
        }
        this.status = PaymentAllocationStatus.REVERSED;
        this.reversedBy = actorId;
        this.reversedAt = now;
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

    public Long getDebtItemId() {
        return debtItemId;
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

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentAllocationStatus getStatus() {
        return status;
    }
}
