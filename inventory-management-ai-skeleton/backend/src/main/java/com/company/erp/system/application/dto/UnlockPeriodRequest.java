package com.company.erp.system.application.dto;

import com.company.erp.common.domain.PeriodModule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Mở lại kỳ đã khóa; bắt buộc lý do (BR-SYS-32). */
public record UnlockPeriodRequest(
        @NotNull PeriodModule module,
        @NotEmpty @Size(max = 100) List<@NotNull Long> branchIds,
        @NotBlank @Size(max = 500) String reason) {
}
