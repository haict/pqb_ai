package com.company.erp.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcrTextKeyTest {

    @Test
    @DisplayName("Danh mục và văn bản OCR (rơi chữ có dấu model không đọc được) cho cùng khóa")
    void catalogAndOcrTextGiveSameKey() {
        assertThat(OcrTextKey.of("Hộp")).isEqualTo(OcrTextKey.of("Hp")).isEqualTo("hp");
        assertThat(OcrTextKey.of("Nhà phân phối thiết bị mẫu")).isEqualTo(OcrTextKey.of("Nhà phân phi thit b mu"));
        assertThat(OcrTextKey.of("Đơn vị tính")).isEqualTo("don v tinh");
        assertThat(OcrTextKey.of("LDL Cholesterol (6x40ml)(Glenbio/Anh)")).isEqualTo("ldl cholesterol 6x40ml glenbio anh");
        assertThat(OcrTextKey.of(null)).isEmpty();
    }

    @Test
    void similarity() {
        assertThat(OcrTextKey.similarity("abc", "abc")).isEqualTo(1.0);
        assertThat(OcrTextKey.similarity("abcd", "abce")).isEqualTo(0.75);
        assertThat(OcrTextKey.similarity("", "")).isEqualTo(1.0);
    }
}
