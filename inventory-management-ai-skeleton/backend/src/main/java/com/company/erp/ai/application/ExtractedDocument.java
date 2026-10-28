package com.company.erp.ai.application;

import java.util.List;
import java.util.Map;

/**
 * Kết quả {@code POST /internal/v1/ocr/extract} của AI Service (docs/14 mục 6.3). Giá trị là văn bản đọc từ chứng từ,
 * chưa tra danh mục; số tiền, số lượng là chuỗi thập phân.
 */
public record ExtractedDocument(int pageCount, Map<String, Field> header, List<Map<String, Field>> lines,
                                Map<String, Field> totals, List<String> warnings) {

    public ExtractedDocument {
        header = header == null ? Map.of() : Map.copyOf(header);
        lines = lines == null ? List.of() : List.copyOf(lines);
        totals = totals == null ? Map.of() : Map.copyOf(totals);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Hợp đồng một trường (docs/14 mục 4.4); {@code status}: OK, LOW_CONFIDENCE, UNKNOWN. */
    public record Field(String value, String rawText, String matchKey, String source, Double confidence,
                        String status, Integer page, List<Integer> box, List<String> warnings) {

        public static final Field UNKNOWN = new Field(null, null, null, "UNKNOWN", null, "UNKNOWN", null, null,
                List.of());

        public Field {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean present() {
            return value != null && !value.isBlank();
        }
    }

    public Field header(String name) {
        return header.getOrDefault(name, Field.UNKNOWN);
    }

    public Field total(String name) {
        return totals.getOrDefault(name, Field.UNKNOWN);
    }

    public static Field field(Map<String, Field> line, String name) {
        Field field = line.get(name);
        return field == null ? Field.UNKNOWN : field;
    }
}
