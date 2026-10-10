package com.company.erp.inventory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Thu hồi một lô hoặc một serial của hàng (BR-INV-42). Chi nhánh lập phiếu: chi nhánh làm việc. */
public record RecallRequest(
        @NotNull Long branchId,
        @NotNull LocalDate recallDate,
        @NotNull Long productId,
        Long lotId,
        Long serialId,
        @NotBlank @Size(max = 1000) String reason) {
}
