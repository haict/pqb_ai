package com.company.erp.system.application.dto;

/** Kết quả tạo kỳ: số kỳ mới tạo (tháng đã có không tính). */
public record GeneratePeriodsResponse(Long companyId, int year, int created) {
}
