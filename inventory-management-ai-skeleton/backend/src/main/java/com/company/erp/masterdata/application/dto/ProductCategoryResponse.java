package com.company.erp.masterdata.application.dto;

import com.company.erp.common.domain.RecordStatus;
import java.time.Instant;

/** @param deleted {@code true} khi lệnh ngừng đã xóa hẳn nhóm chưa có hàng và nhóm con (D-67) */
public record ProductCategoryResponse(
        Long id,
        Long companyId,
        Long parentId,
        String code,
        String name,
        RecordStatus status,
        int version,
        Instant updatedAt,
        boolean deleted) {
}
