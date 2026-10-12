package com.company.erp.purchasing.domain;

/**
 * Nguồn đề nghị mua (BR-PUR-01). Giai đoạn 5 chỉ lập thủ công; từ đơn bán thiếu hàng (Giai đoạn 6), hợp đồng trúng
 * thầu (Giai đoạn 7), cảnh báo dưới định mức (Giai đoạn 10) thêm khi có chứng từ nguồn.
 */
public enum RequisitionSource {
    MANUAL,
    /** Đơn bán thiếu hàng (BR-SAL-15). */
    SALES_ORDER
}
