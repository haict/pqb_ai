package com.company.erp.ai.application.dto;

import java.util.List;

/**
 * Kết quả đối chiếu một giá trị OCR với danh mục (BR-AI-06).
 *
 * @param status         MATCHED (đã gán {@code id}), AMBIGUOUS (nhiều ứng viên, không gán), NOT_FOUND, UNKNOWN (chứng
 *                       từ không có giá trị này)
 * @param matchedBy      khóa đã khớp: CODE, TAX_CODE, NAME, UNIT, RATE
 * @param reviewRequired gán theo tên hoặc giá trị OCR độ tin cậy thấp: người dùng phải xác nhận
 * @param rawText        văn bản đọc từ chứng từ, hiển thị để đối chiếu
 * @param candidates     ứng viên khi AMBIGUOUS
 */
public record CatalogMatch(String status, String matchedBy, boolean reviewRequired, Long id, String code, String name,
                           String rawText, List<Candidate> candidates) {

    public CatalogMatch {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public record Candidate(Long id, String code, String name) {
    }

    public static CatalogMatch matched(String matchedBy, boolean reviewRequired, Long id, String code, String name,
                                       String rawText) {
        return new CatalogMatch("MATCHED", matchedBy, reviewRequired, id, code, name, rawText, List.of());
    }

    public static CatalogMatch ambiguous(String rawText, List<Candidate> candidates) {
        return new CatalogMatch("AMBIGUOUS", null, true, null, null, null, rawText, candidates);
    }

    public static CatalogMatch notFound(String rawText) {
        return new CatalogMatch("NOT_FOUND", null, true, null, null, null, rawText, List.of());
    }

    public static CatalogMatch unknown(String rawText) {
        return new CatalogMatch("UNKNOWN", null, false, null, null, null, rawText, List.of());
    }

    public boolean matched() {
        return "MATCHED".equals(status);
    }
}
