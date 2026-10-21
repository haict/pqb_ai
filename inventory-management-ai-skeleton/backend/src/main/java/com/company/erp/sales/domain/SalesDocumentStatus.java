package com.company.erp.sales.domain;

/**
 * Trạng thái chứng từ bán hàng (BR mục 5.1, D-54). Mọi chứng từ bán là chứng từ ghi sổ (đơn bán, trả hàng / giảm giá):
 * DRAFT → POSTED → REVERSED; CANCELLED khi chưa ghi sổ. Không qua duyệt (D-61): SUBMITTED, REJECTED, APPROVED chỉ còn ở
 * chứng từ lập trước D-61.
 */
public enum SalesDocumentStatus {
    DRAFT,
    SUBMITTED,
    REJECTED,
    APPROVED,
    CANCELLED,
    POSTED,
    REVERSED
}
