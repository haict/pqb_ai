package com.company.erp.inventory.application.dto;

import java.util.List;

/** Kết quả nhập tồn kho đầu kỳ từ Excel: số dòng đã nhập và các phiếu nhập kho nháp đã lập (mỗi kho một phiếu). */
public record OpeningStockImportResponse(int created, List<Long> documentIds, List<String> documentNos) {
}
