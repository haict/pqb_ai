package com.company.erp.finance.domain;

/** Loại bù trừ công nợ (BR-AR-04). */
public enum OffsetType {
    /** Tiền trả trước chưa phân bổ trừ vào hóa đơn cùng loại công nợ của đối tượng. */
    PREPAYMENT,
    /** Phải thu trừ với phải trả của cùng một đối tượng. */
    AR_AP
}
