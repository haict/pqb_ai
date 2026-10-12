package com.company.erp.finance.domain;

/** Loại công nợ: phải thu khách hàng, phải trả nhà cung cấp, tạm ứng nhân viên (BR-CSH-04, TK 141). */
public enum DebtType {
    RECEIVABLE,
    PAYABLE,
    ADVANCE;

    /** Công nợ tăng ở vế Nợ (phải thu, tạm ứng) hay vế Có (phải trả). */
    public boolean increasesOnDebit() {
        return this != PAYABLE;
    }
}
