package com.company.erp.sales.domain;

/** Mốc tính hạn của đợt thu tiền (BR-SAL-50). */
public enum PaymentDueBasis {
    /** Từ ngày đơn bán. */
    ORDER_DATE,
    /** Sau ngày khách nhận hàng. */
    DELIVERY,
    /** Sau ngày hóa đơn (ngày hóa đơn điện tử, chưa có thì ngày ghi sổ đơn). */
    INVOICE,
    /** Sau nghiệm thu (hợp đồng, Giai đoạn 7). */
    ACCEPTANCE
}
