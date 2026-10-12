package com.company.erp.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng công nợ đầu kỳ: số còn nợ của một hóa đơn cũ với một đối tượng. */
@Entity
@Table(name = "debt_opening_lines")
public class DebtOpeningLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "invoice_no", nullable = false)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "sales_user_id")
    private Long salesUserId;

    @Column(name = "note")
    private String note;

    protected DebtOpeningLine() {
    }

    public DebtOpeningLine(int lineNo, Long partnerId, String invoiceNo, LocalDate invoiceDate, LocalDate dueDate,
                           BigDecimal amount, Long salesUserId, String note) {
        this.lineNo = lineNo;
        assign(partnerId, invoiceNo, invoiceDate, dueDate, amount, salesUserId, note);
    }

    /** Chép nội dung của dòng khác (cùng số thứ tự) khi sửa chứng từ. */
    void copyFrom(DebtOpeningLine other) {
        assign(other.partnerId, other.invoiceNo, other.invoiceDate, other.dueDate, other.amount, other.salesUserId,
                other.note);
    }

    private void assign(Long partnerId, String invoiceNo, LocalDate invoiceDate, LocalDate dueDate,
                        BigDecimal amount, Long salesUserId, String note) {
        this.partnerId = partnerId;
        this.invoiceNo = invoiceNo;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.amount = amount;
        this.salesUserId = salesUserId;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getSalesUserId() {
        return salesUserId;
    }

    public String getNote() {
        return note;
    }
}
