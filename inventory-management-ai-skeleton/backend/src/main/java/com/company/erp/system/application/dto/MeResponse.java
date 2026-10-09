package com.company.erp.system.application.dto;

import java.util.List;

/** Thông tin người dùng đăng nhập, quyền và chi nhánh được làm việc (GET /auth/me). */
public record MeResponse(
        Long id,
        String username,
        String fullName,
        Long companyId,
        Long defaultBranchId,
        boolean mustChangePassword,
        boolean ownDataOnly,
        List<String> permissions,
        List<BranchSummary> branches) {

    public record BranchSummary(Long id, Long companyId, String code, String name) {
    }
}
