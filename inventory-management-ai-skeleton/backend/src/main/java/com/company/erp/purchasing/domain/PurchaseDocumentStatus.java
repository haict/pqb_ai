package com.company.erp.purchasing.domain;

/**
 * Trạng thái chứng từ module mua hàng (BR mục 5.1). Mọi chứng từ mua là chứng từ ghi sổ (đơn mua, trả hàng / giảm giá,
 * phân bổ chi phí, D-54): DRAFT → POSTED → REVERSED; CANCELLED khi chưa ghi sổ. Không qua duyệt (D-61): SUBMITTED,
 * REJECTED, APPROVED chỉ còn ở chứng từ lập trước D-61.
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
