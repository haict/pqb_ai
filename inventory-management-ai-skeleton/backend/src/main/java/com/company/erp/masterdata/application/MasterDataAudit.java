package com.company.erp.masterdata.application;

/** Mã hành động và loại thực thể ghi nhật ký audit của module danh mục. */
final class MasterDataAudit {

    static final String CREATE = "CREATE";
    static final String UPDATE = "UPDATE";
    static final String CHANGE_STATUS = "CHANGE_STATUS";
    static final String DELETE = "DELETE";
    static final String IMPORT = "IMPORT";

    static final String COUNTRY = "COUNTRY";
    static final String REFERENCE_ITEM = "REFERENCE_ITEM";
    static final String TAX_RATE = "TAX_RATE";
    static final String PRODUCT_CATEGORY = "PRODUCT_CATEGORY";
    static final String PRODUCT = "PRODUCT";
    static final String PRODUCT_UNIT = "PRODUCT_UNIT";
    static final String STOCK_LEVEL = "PRODUCT_STOCK_LEVEL";
    static final String WAREHOUSE = "WAREHOUSE";
    static final String LOCATION = "WAREHOUSE_LOCATION";
    static final String PARTNER = "PARTNER";
    static final String PRICE_LIST = "PRICE_LIST";
    static final String PRICE_LIST_ITEM = "PRICE_LIST_ITEM";
    static final String COMPLIANCE_TYPE = "COMPLIANCE_DOCUMENT_TYPE";
    static final String COMPLIANCE_DOCUMENT = "COMPLIANCE_DOCUMENT";

    private MasterDataAudit() {
    }
}
