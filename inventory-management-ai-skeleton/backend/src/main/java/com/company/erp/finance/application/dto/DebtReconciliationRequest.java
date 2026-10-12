package com.company.erp.finance.application.dto;

import com.company.erp.finance.domain.DebtType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Lập biên bản đối chiếu: chốt số dư hệ thống tại {@code asOfDate}; {@code branchId} trống = toàn công ty. */
public record DebtReconciliationRequest(
        @NotNull Long companyId,
        Long branchId,
        @NotNull DebtType debtType,
        @NotNull Long partnerId,
        @NotNull LocalDate periodFrom,
        @NotNull LocalDate asOfDate,
        @Size(max = 1000) String note) {
}
