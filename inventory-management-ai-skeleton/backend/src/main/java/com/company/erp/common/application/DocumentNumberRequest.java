package com.company.erp.common.application;

import com.company.erp.common.domain.DocumentNumberPattern;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Yêu cầu cấp số chứng từ.
 *
 * @param companyId    công ty sở hữu chứng từ
 * @param branchId     chi nhánh sở hữu chứng từ
 * @param branchCode   mã chi nhánh in trên số chứng từ
 * @param documentType mã loại chứng từ (ví dụ {@code PXK}); là tiền tố khi loại chưa được cấu hình
 * @param documentDate ngày chứng từ, xác định cấu hình có hiệu lực và chu kỳ đặt lại số
 */
public record DocumentNumberRequest(Long companyId, Long branchId, String branchCode, String documentType,
                                    LocalDate documentDate) {

    public static final Pattern DOCUMENT_TYPE = Pattern.compile("[A-Z0-9_]{1,30}");

    public DocumentNumberRequest {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(branchId, "branchId");
        Objects.requireNonNull(documentDate, "documentDate");
        if (branchCode == null || !DocumentNumberPattern.CODE.matcher(branchCode).matches()) {
            throw new IllegalArgumentException("branchCode phải gồm 1-10 ký tự A-Z, 0-9: " + branchCode);
        }
        if (documentType == null || !DOCUMENT_TYPE.matcher(documentType).matches()) {
            throw new IllegalArgumentException("documentType phải gồm 1-30 ký tự A-Z, 0-9, _: " + documentType);
        }
        if (documentDate.getYear() < 2000 || documentDate.getYear() > 9999) {
            throw new IllegalArgumentException("documentDate không hợp lệ: " + documentDate);
        }
    }
}
