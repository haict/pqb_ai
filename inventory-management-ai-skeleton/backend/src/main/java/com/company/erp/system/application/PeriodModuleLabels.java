package com.company.erp.system.application;

import com.company.erp.common.domain.PeriodModule;

/** Tên phân hệ trong thông báo lỗi tiếng Việt. */
final class PeriodModuleLabels {

    private PeriodModuleLabels() {
    }

    static String of(PeriodModule module) {
        return switch (module) {
            case INVENTORY -> "kho";
            case DEBT -> "công nợ";
            case CASH -> "quỹ";
            case ACCOUNTING -> "kế toán";
        };
    }
}
