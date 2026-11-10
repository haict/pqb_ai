package com.company.erp.system.domain;

/**
 * Chế độ hạch toán của bản cài đặt (D-73). {@code FULL}: ghi sổ đơn mua / đơn bán gồm kho, công nợ, bút toán (D-54).
 * {@code INVENTORY_ONLY}: chỉ ghi kho, cho khách không dùng công nợ, kế toán, thu chi.
 */
public enum PostingMode {
    FULL,
    INVENTORY_ONLY
}
