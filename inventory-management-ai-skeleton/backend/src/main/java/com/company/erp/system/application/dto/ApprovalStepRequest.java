package com.company.erp.system.application.dto;

import com.company.erp.system.domain.ApproverType;
import jakarta.validation.constraints.NotNull;

/** Một bước duyệt: theo vai trò ({@code roleId}) hoặc người cụ thể ({@code userId}). Thứ tự theo vị trí trong danh sách. */
public record ApprovalStepRequest(
        @NotNull ApproverType approverType,
        Long roleId,
        Long userId) {
}
