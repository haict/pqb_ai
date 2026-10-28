package com.company.erp.ai.domain;

/**
 * Loại chứng từ nhận dạng (docs/14 mục 1.2) và quyền lập form đích tương ứng (docs/14 mục 6.1): hóa đơn GTGT của nhà
 * cung cấp, đơn đặt hàng doanh nghiệp gửi nhà cung cấp điền form đơn mua (AI-1b); phiếu đặt hàng của khách điền form
 * đơn bán (AI-2).
 */
public enum OcrDocumentType {
    PURCHASE_INVOICE("purchase-order:create"),
    PURCHASE_ORDER("purchase-order:create"),
    SALES_ORDER("sales-order:create");

    private final String formPermission;

    OcrDocumentType(String formPermission) {
        this.formPermission = formPermission;
    }

    public String formPermission() {
        return formPermission;
    }
}
