package com.company.erp.masterdata.domain;

/** Vai trò của đối tác (BR-MD-30, BR-MD-35). Chỉ đối tác có vai trò phù hợp được chọn trên chứng từ (P-07). */
public enum PartnerRole {
    CUSTOMER,
    SUPPLIER,
    AGENT,
    /** Bên mời thầu / chủ đầu tư. */
    TENDER_OWNER,
    EMPLOYEE,
    OTHER
}
