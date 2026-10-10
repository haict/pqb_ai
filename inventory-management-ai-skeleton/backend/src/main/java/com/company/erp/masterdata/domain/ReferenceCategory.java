package com.company.erp.masterdata.domain;

/** Loại danh mục phụ (BR-MD-45, BR-MD-46). {@code numericValue} có nghĩa với loại ghi trong mô tả. */
public enum ReferenceCategory {
    /** Điều khoản thanh toán; giá trị số = số ngày nợ. */
    PAYMENT_TERM,
    PAYMENT_METHOD,
    DELIVERY_METHOD,
    CONTRACT_TYPE,
    COST_ITEM,
    ADJUSTMENT_REASON,
    SCRAP_REASON,
    RETURN_REASON,
    DISCOUNT_REASON,
    REJECT_REASON,
    /** Lý do chọn lô khác lô FEFO đề xuất (BR-INV-21). */
    LOT_OVERRIDE_REASON,
    QUALITY_CHANGE_REASON
}
