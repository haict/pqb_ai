package com.company.erp.sales.domain;

/** Loại chứng từ giảm bán hàng; tên enum là mã loại chứng từ. */
public enum SalesReturnType {
    /** Hàng bán bị trả lại (BR-SAL-32): nhập lại kho, giảm doanh thu / công nợ nếu đã có hóa đơn. */
    TLB,
    /** Giảm giá hàng bán (BR-SAL-36): giảm doanh thu, công nợ; không đổi tồn. */
    GGB
}
