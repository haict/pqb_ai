package com.company.erp.system.domain;

/** Loại phạm vi dữ liệu (BR-SYS-04). */
public enum DataScopeType {
    /** Mọi chi nhánh, kho của công ty. */
    COMPANY,
    /** Một chi nhánh và các kho thuộc chi nhánh. */
    BRANCH,
    /** Một kho (dùng từ Giai đoạn 2). */
    WAREHOUSE
}
