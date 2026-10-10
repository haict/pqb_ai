package com.company.erp.system.application.dto;

import com.company.erp.system.domain.ParameterValueType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Tham số của một công ty.
 *
 * @param customized {@code false}: đang dùng giá trị mặc định
 * @param locked     đã khóa vì đã phát sinh số liệu ghi sổ (BR-SYS-13)
 * @param version    gửi lại khi sửa để phát hiện sửa đồng thời; {@code 0} khi chưa có giá trị riêng,
 *                   từ {@code 1} khi đã có (phiên bản dòng + 1, để phân biệt hai trường hợp)
 */
public record ParameterResponse(
        String key,
        String module,
        String name,
        String description,
        ParameterValueType valueType,
        String value,
        String defaultValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        List<String> allowedValues,
        boolean lockAfterPosting,
        boolean locked,
        boolean customized,
        int version) {
}
