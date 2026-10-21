package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Một bước duyệt đã chụp vào yêu cầu duyệt trước D-61: thứ tự và người duyệt (vai trò hoặc người cụ thể); chỉ đọc. */
@Embeddable
public class ApprovalStepDefinition {

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, length = 10)
    private ApproverType approverType;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "user_id")
    private Long userId;

    protected ApprovalStepDefinition() {
    }

    public int getStepNo() {
        return stepNo;
    }

    public ApproverType getApproverType() {
        return approverType;
    }

    public Long getRoleId() {
        return roleId;
    }

    public Long getUserId() {
        return userId;
    }
}
