package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Quyền dạng {@code <chức năng>:<hành động>}; dữ liệu tham chiếu do migration định nghĩa. */
@Entity
@Immutable
@Table(name = "sys_permissions")
public class Permission {

    @Id
    private Long id;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "module", nullable = false, length = 30)
    private String module;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Permission() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
