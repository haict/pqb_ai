package com.company.erp.purchasing.application.dto;

/** Kết quả xóa đơn chưa từng ghi sổ (D-65). */
public record DeletedOrderResponse(Long id, String documentNo) {
}
