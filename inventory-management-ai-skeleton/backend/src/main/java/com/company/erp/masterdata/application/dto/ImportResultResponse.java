package com.company.erp.masterdata.application.dto;

/** Kết quả nhập Excel: số dòng đã thêm (nhập là tất cả hoặc không, lỗi thì không ghi dòng nào). */
public record ImportResultResponse(int created) {
}
