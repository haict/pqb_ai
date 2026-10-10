package com.company.erp.system.application;

import com.company.erp.common.domain.PeriodModule;
import java.time.LocalDate;

/** Kỳ sắp được khóa, truyền cho {@link PeriodCloseCheck}. */
public record PeriodCloseContext(
        Long companyId,
        Long branchId,
        PeriodModule module,
        LocalDate startDate,
        LocalDate endDate) {
}
