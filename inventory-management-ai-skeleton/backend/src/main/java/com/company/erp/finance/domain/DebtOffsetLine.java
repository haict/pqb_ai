package com.company.erp.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Dòng bù trừ: khoản công nợ được trừ và số tiền. */
@Entity
@Table(name = "debt_offset_lines")
public class DebtOffsetLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "debt_item_id", nullable = false)
    private Long debtItemId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    protected DebtOffsetLine() {
    }

    public DebtOffsetLine(int lineNo, Long debtItemId, BigDecimal amount, String description) {
        this.lineNo = lineNo;
        this.debtItemId = debtItemId;
        this.amount = amount;
        this.description = description;
    }

    void copyFrom(DebtOffsetLine other) {
        this.debtItemId = other.debtItemId;
        this.amount = other.amount;
        this.description = other.description;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }
}
