package com.company.erp.system.application.dto;

import com.company.erp.common.domain.RecordStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ngừng hoạt động / kích hoạt lại danh mục (PATCH /{id}/status). */
public record ChangeStatusRequest(
        @NotNull RecordStatus status,
        @NotNull Integer version,
        @Size(max = 500) String reason) {
}
