package com.company.erp.accounting.domain;

/** Nguồn gốc bút toán: tự sinh từ chứng từ, nhập tay (P3), giá vốn (Giai đoạn 8), kết chuyển cuối kỳ (P3). */
public enum JournalEntryKind {
    AUTO,
    MANUAL,
    COST,
    CLOSING
}
