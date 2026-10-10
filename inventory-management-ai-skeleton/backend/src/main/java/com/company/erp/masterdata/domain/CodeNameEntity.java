package com.company.erp.masterdata.domain;

import com.company.erp.common.domain.AuditableEntity;
import com.company.erp.common.domain.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

/** Danh mục dạng mã - tên theo công ty: mã duy nhất trong công ty, không đổi; ngừng hoạt động thay vì xóa. */
@MappedSuperclass
public abstract class CodeNameEntity extends AuditableEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecordStatus status;

    protected CodeNameEntity() {
    }

    protected CodeNameEntity(Long companyId, String code, String name) {
        this.companyId = companyId;
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

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    public abstract Long getId();

    public Long getCompanyId() {
        return companyId;
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
