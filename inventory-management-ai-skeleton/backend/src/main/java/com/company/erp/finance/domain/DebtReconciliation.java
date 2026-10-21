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
import java.time.Instant;
import java.time.LocalDate;

/**
 * Biên bản đối chiếu công nợ (BBDC, BR-AR-06): chốt số dư hệ thống tại ngày đối chiếu, ghi nhận số đối tượng xác nhận.
 * Lệch thì xử lý bằng chứng từ điều chỉnh công nợ (BR-AR-07), không sửa số dư.
 */
@Entity
@Table(name = "debt_reconciliations")
public class DebtReconciliation extends AuditableEntity {

    public static final String TYPE_CODE = "BBDC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(name = "document_no", nullable = false, updatable = false)
    private String documentNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", nullable = false, updatable = false)
    private DebtType debtType;

    @Column(name = "partner_id", nullable = false, updatable = false)
    private Long partnerId;

    @Column(name = "period_from", nullable = false, updatable = false)
    private LocalDate periodFrom;

    @Column(name = "as_of_date", nullable = false, updatable = false)
    private LocalDate asOfDate;

    @Column(name = "system_balance", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal systemBalance;

    @Column(name = "confirmed_balance", precision = 19, scale = 4)
    private BigDecimal confirmedBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconciliationStatus status;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "note")
    private String note;

    protected DebtReconciliation() {
    }

    public record Snapshot(Long companyId, Long branchId, String documentNo, DebtType debtType, Long partnerId,
                           LocalDate periodFrom, LocalDate asOfDate, BigDecimal systemBalance, String note) {
    }

    public DebtReconciliation(Snapshot snapshot) {
        if (snapshot.asOfDate().isBefore(snapshot.periodFrom())) {
            throw BusinessException.invalidField("asOfDate", "Ngày đối chiếu phải từ ngày bắt đầu kỳ trở đi.");
        }
        this.companyId = snapshot.companyId();
        this.branchId = snapshot.branchId();
        this.documentNo = snapshot.documentNo();
        this.debtType = snapshot.debtType();
        this.partnerId = snapshot.partnerId();
        this.periodFrom = snapshot.periodFrom();
        this.asOfDate = snapshot.asOfDate();
        this.systemBalance = snapshot.systemBalance();
        this.note = snapshot.note();
        this.status = ReconciliationStatus.PENDING;
    }

    /** Ghi nhận số đối tượng xác nhận: khớp hay lệch với số dư hệ thống đã chốt. */
    public void confirm(BigDecimal balance, String contact, String newNote, Long actorId, Instant now) {
        requirePending("ghi nhận xác nhận");
        this.confirmedBalance = balance;
        this.contactName = contact;
        this.note = newNote != null ? newNote : note;
        this.confirmedBy = actorId;
        this.confirmedAt = now;
        this.status = balance.compareTo(systemBalance) == 0 ? ReconciliationStatus.MATCHED
                : ReconciliationStatus.DIFFERENT;
    }

    public void cancel(String reason) {
        requirePending("hủy");
        this.status = ReconciliationStatus.CANCELLED;
        this.note = reason;
    }

    /** Chênh lệch = số đối tượng xác nhận − số dư hệ thống; {@code null} khi chưa xác nhận. */
    public BigDecimal difference() {
        return confirmedBalance == null ? null : confirmedBalance.subtract(systemBalance);
    }

    private void requirePending(String action) {
        if (status != ReconciliationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Biên bản " + documentNo + " đang ở trạng "
                    + "thái " + status + ", không " + action + " được.");
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

    public DebtType getDebtType() {
        return debtType;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public BigDecimal getSystemBalance() {
        return systemBalance;
    }

    public BigDecimal getConfirmedBalance() {
        return confirmedBalance;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public String getContactName() {
        return contactName;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getNote() {
        return note;
    }
}
