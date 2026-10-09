package com.company.erp.system.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        Long companyId,
        Long defaultBranchId,
        RecordStatus status,
        boolean ownDataOnly,
        boolean mustChangePassword,
        Instant lockedUntil,
        Instant lastLoginAt,
        List<RoleSummary> roles,
        List<DataScopeDto> dataScopes,
        int version) {

    public record RoleSummary(Long id, String code, String name) {
    }
}
