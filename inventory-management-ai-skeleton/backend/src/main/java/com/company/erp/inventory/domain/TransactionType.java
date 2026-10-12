package com.company.erp.inventory.domain;

/** Loại biến động trên sổ nhật ký kho (docs/03 mục 4.3). Phân hệ mua, bán bổ sung loại của mình. */
public enum TransactionType {
    OPENING,
    RECALL_IN,
    INTERNAL_ISSUE,
    SCRAP,
    TRANSFER_OUT,
    TRANSFER_IN,
    ADJUST_IN,
    ADJUST_OUT,
    COUNT_GAIN,
    COUNT_LOSS,
    STATUS_OUT,
    STATUS_IN,
    /** Xuất trả nhà cung cấp (BR-PUR-14). */
    PURCHASE_RETURN,
    /** Xuất bán theo đơn bán (BR-SAL-21). */
    SALES_ISSUE,
    /** Nhập hàng bán bị trả lại (BR-SAL-32). */
    SALES_RETURN,
    /** Dòng đảo, chiều ngược dòng gốc ({@code reversal_of_id}). */
    REVERSAL
}
