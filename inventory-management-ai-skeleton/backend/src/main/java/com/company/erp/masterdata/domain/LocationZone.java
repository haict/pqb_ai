package com.company.erp.masterdata.domain;

/** Loại khu của vị trí kho (BR-MD-40). */
public enum LocationZone {
    NORMAL,
    /** Khu cách ly. */
    QUARANTINE,
    /** Khu hàng hỏng, hết hạn. */
    DAMAGED
}
