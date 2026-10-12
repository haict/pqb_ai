package com.company.erp.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Dòng chứng từ thu chi: yêu cầu phân bổ vào một khoản công nợ ({@code debtItemId}, thu tiền khách / trả nhà cung
 * cấp phân bổ chỉ định) hoặc tài khoản đối ứng ({@code accountId}, thu chi khác).
 */
@Entity
@Table(name = "cash_document_lines")
public class CashDocumentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "debt_item_id")
    private Long debtItemId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "cost_item_id")
    private Long costItemId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    protected CashDocumentLine() {
    }

    private CashDocumentLine(int lineNo, Long debtItemId, Long accountId, Long costItemId, BigDecimal amount,
                             String description) {
        this.lineNo = lineNo;
        assign(debtItemId, accountId, costItemId, amount, description);
    }

    public static CashDocumentLine allocation(int lineNo, Long debtItemId, BigDecimal amount, String description) {
        return new CashDocumentLine(lineNo, debtItemId, null, null, amount, description);
    }

    public static CashDocumentLine counterAccount(int lineNo, Long accountId, Long costItemId, BigDecimal amount,
                                                  String description) {
        return new CashDocumentLine(lineNo, null, accountId, costItemId, amount, description);
    }

    /** Chép nội dung của dòng khác (cùng số thứ tự) khi sửa chứng từ. */
    void copyFrom(CashDocumentLine other) {
        assign(other.debtItemId, other.accountId, other.costItemId, other.amount, other.description);
    }

    private void assign(Long debtItemId, Long accountId, Long costItemId, BigDecimal amount, String description) {
        this.debtItemId = debtItemId;
        this.accountId = accountId;
        this.costItemId = costItemId;
        this.amount = amount;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getDebtItemId() {
        return debtItemId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getCostItemId() {
        return costItemId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }
}
