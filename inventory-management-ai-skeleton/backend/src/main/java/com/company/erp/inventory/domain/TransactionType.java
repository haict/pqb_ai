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
    /** Dòng đảo, chiều ngược dòng gốc ({@code reversal_of_id}). */
    REVERSAL
}
