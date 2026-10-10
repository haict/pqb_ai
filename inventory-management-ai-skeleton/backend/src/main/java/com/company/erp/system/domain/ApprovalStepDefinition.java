package com.company.erp.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/** Một bước duyệt: thứ tự và người duyệt (vai trò hoặc người cụ thể). */
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

    public ApprovalStepDefinition(int stepNo, ApproverType approverType, Long roleId, Long userId) {
        Objects.requireNonNull(approverType, "approverType");
        boolean validRole = approverType == ApproverType.ROLE && roleId != null && userId == null;
        boolean validUser = approverType == ApproverType.USER && userId != null && roleId == null;
        if (!validRole && !validUser) {
            throw new IllegalArgumentException("Bước duyệt phải có đúng vai trò hoặc người duyệt theo loại");
        }
        this.stepNo = stepNo;
        this.approverType = approverType;
        this.roleId = roleId;
        this.userId = userId;
    }

    public ApprovalStepDefinition copy() {
        return new ApprovalStepDefinition(stepNo, approverType, roleId, userId);
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
