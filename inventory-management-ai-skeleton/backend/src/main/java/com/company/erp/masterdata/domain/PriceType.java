package com.company.erp.masterdata.domain;

/** Loại bảng giá (BR-MD-42). */
public enum PriceType {
    /** Giá mua tham khảo: chỉ người có quyền xem giá mua thấy (BR-SYS-05). */
    PURCHASE_REFERENCE,
    /** Giá niêm yết. */
    LIST,
    /** Giá sàn: bán dưới giá sàn phải duyệt (D-04). */
    FLOOR,
    /** Bảng giá bán theo nhóm khách / khách / đại lý. */
    SALES
}
