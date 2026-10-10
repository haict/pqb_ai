package com.company.erp.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Nhà sản xuất (BR-MD-03, P-03). */
@Entity
@Table(name = "manufacturers")
public class Manufacturer extends CodeNameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    protected Manufacturer() {
    }

    public Manufacturer(Long companyId, String code, String name, String countryCode) {
        super(companyId, code, name);
        this.countryCode = countryCode;
    }

    public void changeCountry(String countryCode) {
        this.countryCode = countryCode;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }
}
