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
import java.time.LocalDate;

/**
 * Dòng điều chỉnh công nợ: giảm một khoản công nợ ({@code debtItemId}) hoặc tăng (khoản công nợ mới, số hóa đơn /
 * hạn tùy chọn), kèm tài khoản đối ứng.
 */
@Entity
@Table(name = "debt_adjustment_lines")
public class DebtAdjustmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private AdjustmentDirection direction;

    @Column(name = "debt_item_id")
    private Long debtItemId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "cost_item_id")
    private Long costItemId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "description")
    private String description;

    protected DebtAdjustmentLine() {
    }

    public record Content(AdjustmentDirection direction, Long debtItemId, Long accountId, Long costItemId,
                          BigDecimal amount, String invoiceNo, LocalDate dueDate, String description) {
    }

    public DebtAdjustmentLine(int lineNo, Content content) {
        this.lineNo = lineNo;
        assign(content);
    }

    void copyFrom(DebtAdjustmentLine other) {
        assign(new Content(other.direction, other.debtItemId, other.accountId, other.costItemId, other.amount,
                other.invoiceNo, other.dueDate, other.description));
    }

    private void assign(Content content) {
        this.direction = content.direction();
        this.debtItemId = content.debtItemId();
        this.accountId = content.accountId();
        this.costItemId = content.costItemId();
        this.amount = content.amount();
        this.invoiceNo = content.invoiceNo();
        this.dueDate = content.dueDate();
        this.description = content.description();
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public AdjustmentDirection getDirection() {
        return direction;
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

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getDescription() {
        return description;
    }
}
