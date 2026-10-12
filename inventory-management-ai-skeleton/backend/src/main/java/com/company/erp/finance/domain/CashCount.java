package com.company.erp.finance.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kiểm kê quỹ tiền mặt (KKQ, BR-CSH-05): số đếm thực tế; khi ghi sổ so với số dư sổ quỹ đến hết ngày kiểm kê, chênh
 * lệch thừa / thiếu sinh dòng sổ quỹ và bút toán (chứng từ có duyệt).
 */
@Entity
@Table(name = "cash_counts")
public class CashCount extends FinanceDocument {

    public static final String TYPE_CODE = "KKQ";

    @Column(name = "cash_account_id", nullable = false)
    private Long cashAccountId;

    @Column(name = "counted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal countedAmount;

    @Column(name = "book_balance", precision = 19, scale = 4)
    private BigDecimal bookBalance;

    @Column(name = "difference_amount", precision = 19, scale = 4)
    private BigDecimal differenceAmount;

    protected CashCount() {
    }

    public CashCount(Long companyId, Long branchId, String documentNo) {
        super(companyId, branchId, documentNo);
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return countedAmount;
    }

    public void replaceContent(LocalDate documentDate, LocalDate postingDate, String description, Long cashAccountId,
                               BigDecimal countedAmount) {
        if (countedAmount == null || countedAmount.signum() < 0) {
            throw BusinessException.invalidField("countedAmount", "Số tiền kiểm kê không được âm.");
        }
        if (postingDate.isBefore(documentDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày kiểm kê.");
        }
        replaceHeader(documentDate, postingDate, description);
        this.cashAccountId = cashAccountId;
        this.countedAmount = countedAmount;
    }

    /** Số dư sổ quỹ đến hết ngày kiểm kê tại lúc ghi sổ; trả chênh lệch = thực đếm − sổ. */
    public BigDecimal recordBookBalance(BigDecimal balance) {
        this.bookBalance = balance;
        this.differenceAmount = countedAmount.subtract(balance);
        return differenceAmount;
    }

    public Long getCashAccountId() {
        return cashAccountId;
    }

    public BigDecimal getCountedAmount() {
        return countedAmount;
    }

    public BigDecimal getBookBalance() {
        return bookBalance;
    }

    public BigDecimal getDifferenceAmount() {
        return differenceAmount;
    }
}
