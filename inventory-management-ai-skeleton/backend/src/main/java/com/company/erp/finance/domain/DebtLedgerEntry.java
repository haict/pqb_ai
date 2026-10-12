package com.company.erp.finance.domain;

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
 * Dòng sổ chi tiết công nợ (bất biến, chỉ INSERT). Phải thu, tạm ứng tăng ở vế Nợ, phải trả tăng ở vế Có; số dư theo đối tượng
 * tính từ sổ này (docs/03 mục 4.9).
 */
@Entity
@Table(name = "debt_ledger_entries")
public class DebtLedgerEntry {

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

    @Column(name = "debt_item_id", updatable = false)
    private Long debtItemId;

    @Column(name = "posting_date", nullable = false, updatable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private DebtEntryType entryType;

    @Column(name = "debit_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

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

    protected DebtLedgerEntry() {
    }

    public record Reference(String type, Long id, Long lineId, String number) {
    }

    /**
     * Dòng làm tăng ({@code increase = true}) hoặc giảm công nợ của khoản; vế Nợ / Có suy ra từ loại công nợ.
     */
    public static DebtLedgerEntry of(DebtItem item, LocalDate postingDate, DebtEntryType entryType, boolean increase,
                                     BigDecimal amount, Reference reference, String description, Long actorId,
                                     Instant now) {
        DebtLedgerEntry entry = new DebtLedgerEntry();
        entry.companyId = item.getCompanyId();
        entry.branchId = item.getBranchId();
        entry.debtType = item.getDebtType();
        entry.partnerId = item.getPartnerId();
        entry.debtItemId = item.getId();
        entry.postingDate = postingDate;
        entry.entryType = entryType;
        boolean debit = item.getDebtType().increasesOnDebit() == increase;
        entry.debitAmount = debit ? amount : BigDecimal.ZERO;
        entry.creditAmount = debit ? BigDecimal.ZERO : amount;
        entry.referenceType = reference.type();
        entry.referenceId = reference.id();
        entry.referenceLineId = reference.lineId();
        entry.referenceNo = reference.number();
        entry.description = description;
        entry.createdBy = actorId;
        entry.createdAt = now;
        return entry;
    }

    /**
     * Dòng không gắn khoản công nợ (BR-AR-03, 04): ghi trả trước ({@code increase = false}, giảm công nợ của đối tượng)
     * hoặc dùng trả trước để bù trừ ({@code increase = true}). Vế Nợ / Có suy ra từ loại công nợ.
     */
    public static DebtLedgerEntry unallocated(Party party, LocalDate postingDate, DebtEntryType entryType,
                                              boolean increase, BigDecimal amount, Reference reference,
                                              String description, Long actorId, Instant now) {
        DebtLedgerEntry entry = new DebtLedgerEntry();
        entry.companyId = party.companyId();
        entry.branchId = party.branchId();
        entry.debtType = party.debtType();
        entry.partnerId = party.partnerId();
        entry.postingDate = postingDate;
        entry.entryType = entryType;
        boolean debit = party.debtType().increasesOnDebit() == increase;
        entry.debitAmount = debit ? amount : BigDecimal.ZERO;
        entry.creditAmount = debit ? BigDecimal.ZERO : amount;
        entry.referenceType = reference.type();
        entry.referenceId = reference.id();
        entry.referenceLineId = reference.lineId();
        entry.referenceNo = reference.number();
        entry.description = description;
        entry.createdBy = actorId;
        entry.createdAt = now;
        return entry;
    }

    /** Đối tượng công nợ trong một chi nhánh. */
    public record Party(Long companyId, Long branchId, DebtType debtType, Long partnerId) {
    }

    /** Dòng ngược đúng dòng gốc (BR-GEN-21) vào ngày đảo. */
    public DebtLedgerEntry reversed(LocalDate reversalDate, Reference reference, Long actorId, Instant now) {
        DebtLedgerEntry entry = new DebtLedgerEntry();
        entry.companyId = companyId;
        entry.branchId = branchId;
        entry.debtType = debtType;
        entry.partnerId = partnerId;
        entry.debtItemId = debtItemId;
        entry.postingDate = reversalDate;
        entry.entryType = DebtEntryType.REVERSAL;
        entry.debitAmount = creditAmount;
        entry.creditAmount = debitAmount;
        entry.referenceType = reference.type();
        entry.referenceId = reference.id();
        entry.referenceLineId = reference.lineId();
        entry.referenceNo = reference.number();
        entry.description = description;
        entry.reversalOfId = id;
        entry.createdBy = actorId;
        entry.createdAt = now;
        return entry;
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

    public Long getDebtItemId() {
        return debtItemId;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public DebtEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
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
