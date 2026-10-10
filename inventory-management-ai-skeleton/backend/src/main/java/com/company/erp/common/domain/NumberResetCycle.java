package com.company.erp.common.domain;

/** Chu kỳ đặt lại số thứ tự của số chứng từ (BR-SYS-10). Kỳ luôn được in trong số để số không trùng giữa các chu kỳ. */
public enum NumberResetCycle {
    /** Đặt lại đầu năm; số chứng từ có {@code YYYY}. */
    YEARLY,
    /** Đặt lại đầu tháng; số chứng từ có {@code YYYYMM}. */
    MONTHLY
}
