package com.company.erp.accounting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Dòng bút toán: đúng một vế Nợ hoặc Có, kèm chiều phân tích và quy tắc hạch toán đã dùng. Chỉ INSERT. */
@Entity
@Table(name = "journal_entry_lines")
public class JournalEntryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "debit_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    @Column(name = "transaction_code", nullable = false, updatable = false)
    private String transactionCode;

    @Column(name = "posting_rule_id", updatable = false)
    private Long postingRuleId;

    @Column(name = "partner_id", updatable = false)
    private Long partnerId;

    @Column(name = "product_id", updatable = false)
    private Long productId;

    @Column(name = "warehouse_id", updatable = false)
    private Long warehouseId;

    @Column(name = "department_id", updatable = false)
    private Long departmentId;

    @Column(name = "employee_id", updatable = false)
    private Long employeeId;

    @Column(name = "source_line_id", updatable = false)
    private Long sourceLineId;

    @Column(name = "description", updatable = false)
    private String description;

    protected JournalEntryLine() {
    }

    JournalEntryLine(int lineNo, JournalLineDraft draft) {
        this.lineNo = lineNo;
        this.accountId = draft.accountId();
        this.debitAmount = draft.debit() ? draft.amount() : BigDecimal.ZERO;
        this.creditAmount = draft.debit() ? BigDecimal.ZERO : draft.amount();
        this.transactionCode = draft.transactionCode();
        this.postingRuleId = draft.postingRuleId();
        this.partnerId = draft.partnerId();
        this.productId = draft.productId();
        this.warehouseId = draft.warehouseId();
        this.departmentId = draft.departmentId();
        this.employeeId = draft.employeeId();
        this.sourceLineId = draft.sourceLineId();
        this.description = draft.description();
    }

    /** Dòng ngược: cùng tài khoản, chiều phân tích; đổi vế Nợ / Có (BR-GEN-21). */
    JournalLineDraft reversed() {
        boolean wasDebit = debitAmount.signum() > 0;
        return new JournalLineDraft(accountId, !wasDebit, wasDebit ? debitAmount : creditAmount, transactionCode,
                postingRuleId, partnerId, productId, warehouseId, departmentId, employeeId, sourceLineId,
                description);
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public Long getPostingRuleId() {
        return postingRuleId;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Long getSourceLineId() {
        return sourceLineId;
    }

    public String getDescription() {
        return description;
    }
}
