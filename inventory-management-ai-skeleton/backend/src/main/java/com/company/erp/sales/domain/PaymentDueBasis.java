package com.company.erp.sales.domain;

/** Mốc tính hạn của đợt thu tiền (BR-SAL-50). */
public enum PaymentDueBasis {
    /** Từ ngày đơn bán. */
    ORDER_DATE,
    /** Sau khi giao hàng (ngày khách nhận lần giao gần nhất). */
    DELIVERY,
    /** Sau ngày hóa đơn. */
    INVOICE,
    /** Sau nghiệm thu (hợp đồng, Giai đoạn 7). */
    ACCEPTANCE
}
