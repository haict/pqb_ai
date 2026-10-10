package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Thuế suất GTGT có thời hạn áp dụng (BR-MD-42, BR-GEN-42). `[Chưa kiểm chứng]` Danh sách và thời hạn do kế toán
 * trưởng xác nhận; hệ thống chỉ lưu cấu hình.
 */
@Entity
@Table(name = "tax_rates")
public class TaxRate extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    protected TaxRate() {
    }

    public TaxRate(Long companyId, String code, String name, BigDecimal rate, LocalDate validFrom, LocalDate validTo) {
        super(companyId, code, name);
        update(name, rate, validFrom, validTo);
    }

    public final void update(String name, BigDecimal rate, LocalDate validFrom, LocalDate validTo) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw BusinessException.invalidField("validTo", "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        rename(name);
        this.rate = rate;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean isEffectiveOn(LocalDate date) {
        return isActive() && !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    @Override
    public Long getId() {
        return id;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
