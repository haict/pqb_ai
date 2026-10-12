package com.company.erp.finance.domain;

/** Loại dòng sổ chi tiết công nợ (docs/03 mục 4.9). */
public enum DebtEntryType {
    /** Công nợ đầu kỳ chuyển từ hệ thống cũ. */
    OPENING,
    /** Tạm ứng nhân viên (phiếu chi / ủy nhiệm chi tạm ứng). */
    ADVANCE,
    INVOICE,
    RECEIPT,
    PAYMENT,
    RETURN,
    DISCOUNT,
    OFFSET,
    ADJUSTMENT,
    /** Tiền trả trước chưa phân bổ vào khoản công nợ. */
    PREPAYMENT,
    REVERSAL
}
