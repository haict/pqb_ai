package com.company.erp.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng công nợ đầu kỳ: số còn nợ của một đối tượng đến một hạn thanh toán (D-60: không theo dõi hóa đơn). */
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

    public DebtOpeningLine(int lineNo, Long partnerId, LocalDate dueDate, BigDecimal amount, Long salesUserId,
                           String note) {
        this.lineNo = lineNo;
        assign(partnerId, dueDate, amount, salesUserId, note);
    }

    /** Chép nội dung của dòng khác (cùng số thứ tự) khi sửa chứng từ. */
    void copyFrom(DebtOpeningLine other) {
        assign(other.partnerId, other.dueDate, other.amount, other.salesUserId, other.note);
    }

    private void assign(Long partnerId, LocalDate dueDate, BigDecimal amount, Long salesUserId, String note) {
        this.partnerId = partnerId;
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
