package com.company.erp.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentNumberFormatTest {

    @Test
    @DisplayName("D-03: số chứng từ dạng <LOẠI>-<CHI NHÁNH>-<YYYY>-<6 số>")
    void formatsDocumentNumber() {
        DocumentNumberRequest request = new DocumentNumberRequest(1L, 2L, "HN", "PXK", 2026);

        assertThat(DocumentNumberService.format(request, 42)).isEqualTo("PXK-HN-2026-000042");
    }

    @Test
    @DisplayName("Báo lỗi khi bộ đếm vượt 6 chữ số thay vì sinh số sai định dạng")
    void rejectsOverflow() {
        DocumentNumberRequest request = new DocumentNumberRequest(1L, 2L, "HN", "PXK", 2026);

        assertThatThrownBy(() -> DocumentNumberService.format(request, 1_000_000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mã loại và mã chi nhánh chỉ gồm A-Z, 0-9")
    void rejectsInvalidCodes() {
        assertThatThrownBy(() -> new DocumentNumberRequest(1L, 2L, "hn", "PXK", 2026))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentNumberRequest(1L, 2L, "HN", "PX-K", 2026))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
