package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa điều chỉnh công nợ (DCCN) hoặc thanh toán tạm ứng (TTTU). Sửa gửi {@code version}; chi nhánh, loại chứng
 * từ, loại công nợ không đổi sau khi lập.
 */
public record DebtAdjustmentRequest(
        @NotNull Long branchId,
        @NotNull @Pattern(regexp = "DCCN|TTTU") String documentType,
        @NotNull DebtType debtType,
        @NotNull Long partnerId,
        Long reconciliationId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<DebtAdjustmentLineRequest> lines,
        Integer version) {
}
