package com.company.erp.finance.domain;

/**
 * Cách phân bổ tiền vào khoản công nợ (BR-AR-03): tự động hóa đơn cũ nhất trước lúc ghi sổ, hoặc theo dòng người lập
 * chỉ định; phần chưa phân bổ là trả trước.
 */
public enum AllocationMode {
    AUTO,
    MANUAL
}
