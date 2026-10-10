package com.company.erp.system.domain;

import com.company.erp.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.YearMonth;

/** Kỳ tháng của công ty (BR-SYS-30). Năm tài chính trùng năm dương lịch. Trạng thái khóa ở {@link PeriodLock}. */
@Entity
@Table(name = "fiscal_periods")
public class FiscalPeriod extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "period_year", nullable = false, updatable = false)
    private int year;

    @Column(name = "period_month", nullable = false, updatable = false)
    private int month;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    protected FiscalPeriod() {
    }

    public FiscalPeriod(Long companyId, YearMonth yearMonth) {
        this.companyId = companyId;
        this.year = yearMonth.getYear();
        this.month = yearMonth.getMonthValue();
        this.startDate = yearMonth.atDay(1);
        this.endDate = yearMonth.atEndOfMonth();
    }

    public YearMonth yearMonth() {
        return YearMonth.of(year, month);
    }

    /** Tên hiển thị trong thông báo lỗi, ví dụ {@code 10/2026}. */
    public String label() {
        return String.format("%02d/%04d", month, year);
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
