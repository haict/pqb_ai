package com.company.erp.system.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Nội dung license đã kiểm tra chữ ký (D-59). {@code taxCodes} chứa {@value #ANY_COMPANY} nghĩa là mọi công ty (chỉ
 * nên cấp cho môi trường kiểm thử của nhà cung cấp).
 */
public record LicensePayload(String keyId, String licenseId, String customerName, List<String> taxCodes,
                             LocalDate validTo, int graceDays, Instant issuedAt) {

    public static final String ANY_COMPANY = "*";

    public LocalDate graceUntil() {
        return validTo.plusDays(graceDays);
    }

    public boolean covers(String taxCode) {
        return taxCodes.contains(ANY_COMPANY) || (taxCode != null && taxCodes.contains(taxCode.trim()));
    }
}
