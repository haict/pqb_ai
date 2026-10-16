package com.company.erp.masterdata.domain;

/** Loại hàng (BR-MD-02); quyết định cờ quản lý mặc định (BR-MD-04). */
public enum ProductType {
    /** Máy móc - thiết bị: mặc định quản lý serial. */
    MACHINE,
    /** Vật tư tiêu hao: mặc định lô và hạn dùng. */
    CONSUMABLE,
    /** Thuốc thử - hóa chất: mặc định lô và hạn dùng. */
    REAGENT,
    SPARE_PART,
    TOOL,
    /** Dịch vụ: không quản lý tồn. */
    SERVICE,
    OTHER
}
