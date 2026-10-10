package com.company.erp.system.application.dto;

import java.time.LocalDate;
import java.util.List;

/** Kỳ tháng kèm trạng thái khóa từng phân hệ của một chi nhánh. */
public record FiscalPeriodResponse(
        Long id,
        Long companyId,
        int year,
        int month,
        LocalDate startDate,
        LocalDate endDate,
        List<PeriodLockStatus> locks) {
}
