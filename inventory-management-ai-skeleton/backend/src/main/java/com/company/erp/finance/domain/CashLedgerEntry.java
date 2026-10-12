package com.company.erp.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Dòng sổ quỹ / sổ tiền gửi (bất biến, chỉ INSERT, BR-CSH-01). Tiền vào ở vế Nợ, tiền ra ở vế Có; số dư quỹ = Σ Nợ −
 * Σ Có, phải khớp {@code cash_accounts.balance}.
 */
@Entity
@Table(name = "cash_ledger_entries")
public class CashLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "cash_account_id", nullable = false, updatable = false)
    private Long cashAccountId;

    @Column(name = "posting_date", nullable = false, updatable = false)
    private LocalDate postingDate;

    @Column(name = "transaction_code", nullable = false, updatable = false)
    private String transactionCode;

    @Column(name = "debit_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    @Column(name = "partner_id", updatable = false)
    private Long partnerId;

    @Column(name = "counterparty_name", updatable = false)
    private String counterpartyName;

    @Column(name = "reference_type", nullable = false, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private Long referenceId;

    @Column(name = "reference_line_id", updatable = false)
    private Long referenceLineId;

    @Column(name = "reference_no", nullable = false, updatable = false)
    private String referenceNo;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "reversal_of_id", updatable = false)
    private Long reversalOfId;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CashLedgerEntry() {
    }

    /** Chứng từ nguồn và đối tượng nộp / nhận tiền của dòng sổ. */
    public record Reference(String type, Long id, Long lineId, String number, Long partnerId,
                            String counterpartyName) {
    }

    /** Dòng tiền vào ({@code inflow = true}) hoặc ra của quỹ. */
    public static CashLedgerEntry of(CashAccount account, LocalDate postingDate, String transactionCode,
                                     boolean inflow, BigDecimal amount, Reference reference, String description,
                                     Long actorId, Instant now) {
        CashLedgerEntry entry = new CashLedgerEntry();
        entry.companyId = account.getCompanyId();
        entry.branchId = account.getBranchId();
        entry.cashAccountId = account.getId();
        entry.postingDate = postingDate;
        entry.transactionCode = transactionCode;
        entry.debitAmount = inflow ? amount : BigDecimal.ZERO;
        entry.creditAmount = inflow ? BigDecimal.ZERO : amount;
        entry.partnerId = reference.partnerId();
        entry.counterpartyName = reference.counterpartyName();
        entry.referenceType = reference.type();
        entry.referenceId = reference.id();
        entry.referenceLineId = reference.lineId();
        entry.referenceNo = reference.number();
        entry.description = description;
        entry.createdBy = actorId;
        entry.createdAt = now;
        return entry;
    }

    /** Dòng ngược đúng dòng gốc (BR-GEN-21) vào ngày đảo. */
    public CashLedgerEntry reversed(LocalDate reversalDate, Long actorId, Instant now) {
        CashLedgerEntry entry = new CashLedgerEntry();
        entry.companyId = companyId;
        entry.branchId = branchId;
        entry.cashAccountId = cashAccountId;
        entry.postingDate = reversalDate;
        entry.transactionCode = transactionCode;
        entry.debitAmount = creditAmount;
        entry.creditAmount = debitAmount;
        entry.partnerId = partnerId;
        entry.counterpartyName = counterpartyName;
        entry.referenceType = referenceType;
        entry.referenceId = referenceId;
        entry.referenceLineId = referenceLineId;
        entry.referenceNo = referenceNo;
        entry.description = description;
        entry.reversalOfId = id;
        entry.createdBy = actorId;
        entry.createdAt = now;
        return entry;
    }

    /** Ảnh hưởng tới số dư quỹ: Nợ − Có. */
    public BigDecimal netAmount() {
        return debitAmount.subtract(creditAmount);
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

    public Long getCashAccountId() {
        return cashAccountId;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public Long getReferenceLineId() {
        return referenceLineId;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public String getDescription() {
        return description;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }
}
