package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Loại chứng từ (đánh số chứng từ, nguồn sổ phụ, quy tắc hạch toán). Danh mục do migration của module nghiệp vụ thêm,
 * chỉ đọc. Từ D-61 không còn chế độ duyệt: cột {@code approval_mode}, {@code self_approval_allowed} giữ trong bảng
 * nhưng không dùng.
 */
@Entity
@Table(name = "sys_document_types")
public class DocumentTypeSetting {

    @Id
    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "module", nullable = false, length = 30, updatable = false)
    private String module;

    @Column(name = "name", nullable = false, length = 200, updatable = false)
    private String name;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected DocumentTypeSetting() {
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
