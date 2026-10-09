package com.company.erp.system.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.util.List;

public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean systemRole,
        RecordStatus status,
        List<String> permissions,
        int version) {
}
