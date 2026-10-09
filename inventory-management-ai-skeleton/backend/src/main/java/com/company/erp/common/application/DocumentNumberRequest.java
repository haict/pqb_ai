package com.company.erp.common.application;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Yêu cầu cấp số chứng từ.
 *
 * @param companyId    công ty sở hữu chứng từ
 * @param branchId     chi nhánh sở hữu chứng từ
 * @param branchCode   mã chi nhánh in trên số chứng từ
 * @param documentType mã loại chứng từ, đồng thời là tiền tố (ví dụ {@code PXK})
 * @param year         năm của ngày chứng từ, xác định chu kỳ đặt lại số
 */
public record DocumentNumberRequest(Long companyId, Long branchId, String branchCode, String documentType, int year) {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{1,10}");

    public DocumentNumberRequest {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(branchId, "branchId");
        requireCode(branchCode, "branchCode");
        requireCode(documentType, "documentType");
        if (year < 2000 || year > 9999) {
            throw new IllegalArgumentException("year không hợp lệ: " + year);
        }
    }

    private static void requireCode(String value, String name) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " phải gồm 1-10 ký tự A-Z, 0-9: " + value);
        }
    }

    String periodKey() {
        return Integer.toString(year);
    }
}
