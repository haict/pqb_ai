package com.company.erp.system.application.dto;

import com.company.erp.common.domain.PeriodModule;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Khóa kỳ cho một phân hệ ở một hoặc nhiều chi nhánh (tất cả hoặc không chi nhánh nào). */
public record LockPeriodRequest(
        @NotNull PeriodModule module,
        @NotEmpty @Size(max = 100) List<@NotNull Long> branchIds,
        @Size(max = 500) String reason) {
}
