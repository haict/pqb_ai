package com.company.erp.system.application.dto;

import com.company.erp.system.domain.ApproverType;

/** Bước duyệt kèm tên vai trò / người duyệt để hiển thị. */
public record ApprovalStepResponse(
        int stepNo,
        ApproverType approverType,
        Long roleId,
        String roleName,
        Long userId,
        String userName) {
}
