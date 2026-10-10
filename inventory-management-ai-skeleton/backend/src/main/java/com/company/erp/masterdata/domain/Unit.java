package com.company.erp.masterdata.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Đơn vị tính (BR-MD-20). */
@Entity
@Table(name = "units")
public class Unit extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    protected Unit() {
    }

    public Unit(Long companyId, String code, String name) {
        super(companyId, code, name);
    }

    @Override
    public Long getId() {
        return id;
    }
}
