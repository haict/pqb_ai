package com.company.erp.inventory.application.dto;

/** Công ty chọn để in tên / địa chỉ đầu phiếu kho (D-58, D-78). */
public record InventoryPrintCompanyResponse(Long id, String code, String name, String address) {
}
