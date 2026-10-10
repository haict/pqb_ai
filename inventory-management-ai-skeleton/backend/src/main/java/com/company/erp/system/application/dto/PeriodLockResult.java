package com.company.erp.system.application.dto;

import com.company.erp.common.domain.PeriodModule;
import java.util.List;

/** Kết quả khóa/mở kỳ: các chi nhánh đã đổi trạng thái. */
public record PeriodLockResult(
        Long fiscalPeriodId,
        PeriodModule module,
        boolean locked,
        List<Long> branchIds) {
}
