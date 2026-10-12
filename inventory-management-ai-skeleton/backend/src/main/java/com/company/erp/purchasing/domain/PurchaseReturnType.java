package com.company.erp.purchasing.domain;

/** Loại chứng từ giảm hàng mua (BR-PUR-14); tên enum là mã loại chứng từ. */
public enum PurchaseReturnType {
    /** Trả hàng nhà cung cấp: giảm tồn (xuất trả đúng lô / serial), giảm công nợ nếu hàng đã có hóa đơn. */
    THN,
    /** Giảm giá hàng mua: chỉ giảm công nợ và giá trị nhập, không đổi tồn. */
    GGM
}
