package com.company.erp.masterdata.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Thay toàn bộ định mức tồn của hàng (các kho không có trong danh sách bị xóa định mức). */
public record StockLevelsRequest(@NotNull @Size(max = 200) List<@Valid @NotNull StockLevelDto> levels) {
}
