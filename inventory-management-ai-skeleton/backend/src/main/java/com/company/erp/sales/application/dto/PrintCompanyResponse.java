package com.company.erp.sales.application.dto;

/** Công ty chọn để in tên / địa chỉ đầu phiếu xuất kho (D-58). */
public record PrintCompanyResponse(Long id, String code, String name, String address) {
}
