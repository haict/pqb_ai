package com.company.erp.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Mục danh mục phụ: điều khoản, phương thức, loại hợp đồng, khoản mục, lý do (BR-MD-45, BR-MD-46). */
@Entity
@Table(name = "reference_items")
public class ReferenceItem extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30, updatable = false)
    private ReferenceCategory category;

    @Column(name = "numeric_value", precision = 19, scale = 4)
    private BigDecimal numericValue;

    protected ReferenceItem() {
    }

    public ReferenceItem(Long companyId, ReferenceCategory category, String code, String name,
                         BigDecimal numericValue) {
        super(companyId, code, name);
        this.category = category;
        this.numericValue = numericValue;
    }

    public void update(String name, BigDecimal numericValue) {
        rename(name);
        this.numericValue = numericValue;
    }

    @Override
    public Long getId() {
        return id;
    }

    public ReferenceCategory getCategory() {
        return category;
    }

    public BigDecimal getNumericValue() {
        return numericValue;
    }
}
