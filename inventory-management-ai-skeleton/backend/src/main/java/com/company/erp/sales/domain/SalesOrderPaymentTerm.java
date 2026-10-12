package com.company.erp.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Đợt thu tiền theo đơn (BR-SAL-50): tỷ lệ, số tiền (theo tổng đơn), mốc tính hạn, số ngày. */
@Entity
@Table(name = "sales_order_payment_terms")
public class SalesOrderPaymentTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal percent;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "due_basis", nullable = false)
    private PaymentDueBasis dueBasis;

    @Column(name = "due_days", nullable = false)
    private int dueDays;

    @Column(name = "description")
    private String description;

    protected SalesOrderPaymentTerm() {
    }

    public SalesOrderPaymentTerm(int installmentNo, BigDecimal percent, BigDecimal amount, PaymentDueBasis dueBasis,
                                 int dueDays, String description) {
        this.installmentNo = installmentNo;
        apply(percent, amount, dueBasis, dueDays, description);
    }

    void apply(BigDecimal percent, BigDecimal amount, PaymentDueBasis dueBasis, int dueDays, String description) {
        this.percent = percent;
        this.amount = amount;
        this.dueBasis = dueBasis;
        this.dueDays = dueDays;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public int getInstallmentNo() {
        return installmentNo;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentDueBasis getDueBasis() {
        return dueBasis;
    }

    public int getDueDays() {
        return dueDays;
    }

    public String getDescription() {
        return description;
    }
}
