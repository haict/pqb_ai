package com.company.erp.common.domain;

/** Phân hệ được khóa kỳ riêng (BR-SYS-30). Module ghi sổ kiểm tra kỳ của phân hệ mình trước khi ghi. */
public enum PeriodModule {
    /** Kho: phiếu nhập, xuất, chuyển, điều chỉnh. Khóa sau khi tính giá vốn (BR-SYS-31). */
    INVENTORY,
    /** Công nợ phải thu, phải trả. */
    DEBT,
    /** Quỹ tiền mặt, tiền gửi. */
    CASH,
    /** Kế toán tổng hợp. Khóa sau khi kỳ kho đã khóa (BR-SYS-31). */
    ACCOUNTING
}
