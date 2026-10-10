package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Loại chứng từ và cấu hình duyệt của loại (BR-GEN-05, BR-SYS-14). Danh mục do migration của module nghiệp vụ thêm;
 * quản trị chỉ đổi chế độ duyệt và cho phép tự duyệt.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", nullable = false, length = 20)
    private ApprovalMode approvalMode;

    @Column(name = "self_approval_allowed", nullable = false)
    private boolean selfApprovalAllowed;

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

    public void configure(ApprovalMode approvalMode, boolean selfApprovalAllowed, Long actorId, Instant now) {
        this.approvalMode = approvalMode;
        this.selfApprovalAllowed = selfApprovalAllowed;
        this.updatedBy = actorId;
        this.updatedAt = now;
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

    public ApprovalMode getApprovalMode() {
        return approvalMode;
    }

    public boolean isSelfApprovalAllowed() {
        return selfApprovalAllowed;
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
