package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.OffsetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa chứng từ bù trừ công nợ. {@code debtType} bắt buộc với bù trừ trả trước, bỏ trống với bù trừ phải thu -
 * phải trả. Sửa gửi {@code version}; chi nhánh, loại bù trừ không đổi sau khi lập.
 */
public record DebtOffsetRequest(
        @NotNull Long branchId,
        @NotNull OffsetType offsetType,
        DebtType debtType,
        @NotNull Long partnerId,
        @NotNull LocalDate documentDate,
        LocalDate postingDate,
        @Size(max = 500) String description,
        @NotEmpty @Valid @Size(max = 200) List<DebtOffsetLineRequest> lines,
        Integer version) {
}
