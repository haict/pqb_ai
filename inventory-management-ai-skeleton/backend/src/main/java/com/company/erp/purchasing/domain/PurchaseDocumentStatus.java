package com.company.erp.purchasing.domain;

/**
 * Trạng thái chứng từ module mua hàng (BR mục 5.1). Mọi chứng từ mua là chứng từ ghi sổ (đơn mua, trả hàng / giảm giá,
 * phân bổ chi phí, D-54): DRAFT → SUBMITTED → APPROVED → POSTED → REVERSED; SUBMITTED → REJECTED → sửa → gửi lại;
 * CANCELLED khi chưa ghi sổ.
 */
public enum PurchaseDocumentStatus {
    DRAFT,
    SUBMITTED,
    REJECTED,
    APPROVED,
    CANCELLED,
    POSTED,
    REVERSED
}
