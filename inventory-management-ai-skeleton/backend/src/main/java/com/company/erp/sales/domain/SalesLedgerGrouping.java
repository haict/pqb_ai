package com.company.erp.sales.domain;

/**
 * "Thống kê theo" của sổ chi tiết bán hàng (S16-DNN): nhóm ngoài khối từng hàng hóa. {@code NONE}: chỉ theo hàng
 * hóa; {@code CUSTOMER}: theo khách hàng; {@code PRODUCT_CATEGORY}: theo nhóm hàng trực tiếp của hàng.
 */
public enum SalesLedgerGrouping {
    NONE,
    CUSTOMER,
    PRODUCT_CATEGORY
}
