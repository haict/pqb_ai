package com.company.erp.finance.domain;

/** Trạng thái biên bản đối chiếu công nợ: chờ xác nhận, khớp, lệch, hủy. */
public enum ReconciliationStatus {
    PENDING,
    MATCHED,
    DIFFERENT,
    CANCELLED
}
