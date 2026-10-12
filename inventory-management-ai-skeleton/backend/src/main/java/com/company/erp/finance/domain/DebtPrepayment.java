package com.company.erp.finance.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tiền trả trước chưa phân bổ của một đối tượng trong chi nhánh (BR-AR-03, 04): bản đọc nhanh của các dòng sổ chi tiết
 * công nợ không gắn khoản công nợ; chỉ đổi sau khi khóa dòng FOR UPDATE; không âm.
 */
@Entity
@Table(name = "debt_prepayments")
public class DebtPrepayment {

    @Id
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

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DebtPrepayment() {
    }

    public void add(BigDecimal value, Instant now) {
        this.amount = amount.add(value);
        this.updatedAt = now;
    }

    /** Dùng / hoàn trả trước; không vượt số còn. */
    public void subtract(BigDecimal value, Instant now, ErrorCode error, String message) {
        if (value.compareTo(amount) > 0) {
            throw new BusinessException(error, message + " Tiền trả trước còn "
                    + amount.stripTrailingZeros().toPlainString() + ".");
        }
        this.amount = amount.subtract(value);
        this.updatedAt = now;
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

    public BigDecimal getAmount() {
        return amount;
    }
}
