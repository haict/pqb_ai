package com.company.erp.system.application.dto;

import com.company.erp.system.domain.LicenseState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Trạng thái bản quyền phần mềm (D-59). {@code writable}: được ghi dữ liệu; sai thì hệ thống chỉ đọc.
 * {@code daysRemaining}: số ngày đến hết thời gian ân hạn (âm khi đã quá). {@code unlicensedCompanies}: công ty đang
 * hoạt động có mã số thuế không thuộc license.
 */
public record LicenseStatusResponse(
        LicenseState state,
        boolean writable,
        String message,
        String licenseId,
        String customerName,
        List<String> taxCodes,
        LocalDate validTo,
        LocalDate graceUntil,
        Long daysRemaining,
        Instant issuedAt,
        Instant installedAt,
        List<String> unlicensedCompanies) {
}
