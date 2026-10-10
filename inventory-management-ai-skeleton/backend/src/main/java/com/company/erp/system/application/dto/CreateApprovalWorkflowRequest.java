package com.company.erp.system.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Luồng duyệt mới. {@code branchId} trống: mọi chi nhánh. {@code minAmount}, {@code minDiscountPercent} trống:
 * không xét ngưỡng.
 */
public record CreateApprovalWorkflowRequest(
        @NotNull Long companyId,
        @NotBlank @Size(max = 30) String documentType,
        @NotBlank @Size(max = 200) String name,
        Long branchId,
        @PositiveOrZero BigDecimal minAmount,
        @DecimalMin("0") @DecimalMax("100") BigDecimal minDiscountPercent,
        @NotNull Integer priority,
        @NotEmpty @Size(max = 10) List<@Valid @NotNull ApprovalStepRequest> steps) {
}
