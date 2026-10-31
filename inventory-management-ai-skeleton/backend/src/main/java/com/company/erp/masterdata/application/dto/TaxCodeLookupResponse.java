package com.company.erp.masterdata.application.dto;

/**
 * Thông tin doanh nghiệp tra theo mã số thuế (D-66); trường nguồn không có thì {@code null}.
 *
 * @param status tình trạng người nộp thuế theo nguồn tra cứu (ví dụ "NNT đang hoạt động")
 * @param source nguồn dữ liệu, để người dùng biết căn cứ gợi ý
 */
public record TaxCodeLookupResponse(String taxCode, String name, String internationalName, String shortName,
                                    String address, String phone, String status, String source) {
}
