package com.company.erp.sales.domain;

/** Quyết định xử lý hàng lỗi (BR-SAL-34): đổi, sửa, trả hàng, bảo hành (liên kết M09), từ chối. */
public enum ComplaintDecision {
    EXCHANGE,
    REPAIR,
    RETURN,
    WARRANTY,
    REJECT
}
