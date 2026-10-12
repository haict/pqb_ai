package com.company.erp.purchasing.domain;

/** Kết quả kiểm nhận dòng phiếu nhận hàng (BR-PUR-07, BR-PUR-08). */
public enum InspectionResult {
    /** Đạt: nhập kho theo trạng thái ban đầu của hàng. */
    ACCEPTED,
    /** Lỗi: nhập khu cách ly (trạng thái CÁCH LY), chờ xử lý / trả NCC. */
    QUARANTINE,
    /** Lỗi: không nhập kho, trả lại nhà cung cấp. */
    RETURN;

    public boolean stocked() {
        return this != RETURN;
    }
}
