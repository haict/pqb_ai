package com.company.erp.system.domain;

import com.company.erp.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Giá trị tham số của một công ty (BR-SYS-13). Chưa có dòng nghĩa là dùng giá trị mặc định của định nghĩa. */
@Entity
@Table(name = "sys_parameters")
public class Parameter extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "param_key", nullable = false, length = 80, updatable = false)
    private String key;

    @Column(name = "param_value", nullable = false, length = 500)
    private String value;

    protected Parameter() {
    }

    public Parameter(Long companyId, String key, String value) {
        this.companyId = companyId;
        this.key = key;
        this.value = value;
    }

    public void changeValue(String value) {
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
