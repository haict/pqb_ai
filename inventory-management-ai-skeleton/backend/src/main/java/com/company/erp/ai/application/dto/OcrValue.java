package com.company.erp.ai.application.dto;

import com.company.erp.ai.application.ExtractedDocument.Field;
import java.util.List;

/**
 * Giá trị đọc từ chứng từ, chưa tra danh mục (docs/14 mục 4.4). {@code source}: OCR, UNKNOWN; {@code status}: OK,
 * LOW_CONFIDENCE, UNKNOWN. Số tiền, số lượng là chuỗi thập phân.
 */
public record OcrValue(String value, String rawText, String source, Double confidence, String status,
                       List<String> warnings) {

    public static final OcrValue UNKNOWN = new OcrValue(null, null, "UNKNOWN", null, "UNKNOWN", List.of());

    public OcrValue {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static OcrValue of(Field field) {
        if (!field.present()) {
            return UNKNOWN;
        }
        return new OcrValue(field.value(), field.rawText(), field.source(), field.confidence(), field.status(),
                field.warnings());
    }

    /** Giá trị dựng từ trường OCR (ví dụ diễn giải "Theo đơn đặt hàng số ..."), giữ độ tin cậy của trường gốc. */
    public OcrValue withValue(String newValue) {
        return new OcrValue(newValue, rawText, source, confidence, status, warnings);
    }

    public boolean present() {
        return value != null;
    }
}
