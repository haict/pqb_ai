package com.company.erp.ai.application.dto;

import java.util.List;

/**
 * Kiểm tra số học bằng {@code BigDecimal} (BR-AI-07). {@code status}: OK, MISMATCH (giữ giá trị OCR, không tự sửa),
 * UNKNOWN (thiếu số để kiểm tra).
 */
public record AmountCheck(String status, String calculated, String printed, List<String> warnings) {

    public static final AmountCheck UNKNOWN = new AmountCheck("UNKNOWN", null, null, List.of());

    public AmountCheck {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
