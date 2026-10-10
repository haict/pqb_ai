package com.company.erp.masterdata.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Nhãn hiệu (BR-MD-03, P-03). */
@Entity
@Table(name = "brands")
public class Brand extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected Brand() {
    }

    public Brand(Long companyId, String code, String name) {
        super(companyId, code, name);
    }

    @Override
    public Long getId() {
        return id;
    }
}
