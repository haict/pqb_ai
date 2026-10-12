package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa chứng từ công nợ đầu kỳ. {@code version} bắt buộc khi sửa (phát hiện sửa đồng thời); loại công nợ và chi
 * nhánh không đổi sau khi lập. Ngày hạch toán để trống thì bằng ngày chứng từ.
 */
public record DebtOpeningRequest(
        @NotNull Long branchId,
        @NotNull DebtType debtType,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 500) String description,
        @NotEmpty @Size(max = 1000) List<@Valid @NotNull DebtOpeningLineRequest> lines,
        Integer version) {
}
