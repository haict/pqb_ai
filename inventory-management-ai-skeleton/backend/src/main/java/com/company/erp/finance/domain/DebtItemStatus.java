package com.company.erp.finance.domain;

/** Trạng thái khoản công nợ theo số đã thanh toán; đảo chứng từ nguồn thì {@code REVERSED}. */
public enum DebtItemStatus {
    OPEN,
    PARTIAL,
    SETTLED,
    REVERSED
}
