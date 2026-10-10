package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Quốc gia / xuất xứ theo mã ISO 3166-1 alpha-2, dùng chung mọi công ty. */
@Entity
@Table(name = "countries")
public class Country extends AuditableEntity {

    @Id
    @Column(name = "code", length = 2)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    protected Country() {
    }

    public Country(String code, String name) {
        this.code = code;
        this.name = name;
        this.status = RecordStatus.ACTIVE;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
