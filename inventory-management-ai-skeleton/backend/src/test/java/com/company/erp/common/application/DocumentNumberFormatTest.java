package com.company.erp.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.common.domain.DocumentNumberPattern;
import com.company.erp.common.domain.NumberResetCycle;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentNumberFormatTest {

    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

    @Test
    @DisplayName("D-03: mặc định <LOẠI>-<CHI NHÁNH>-<YYYY>-<6 số>")
    void formatsDefaultDocumentNumber() {
        assertThat(DocumentNumberPattern.defaultFor("PXK").format("HN", OCT_15, 42)).isEqualTo("PXK-HN-2026-000042");
    }

    @Test
    @DisplayName("BR-SYS-10: tiền tố, độ dài, đặt lại theo tháng in YYYYMM")
    void formatsConfiguredDocumentNumber() {
        DocumentNumberPattern pattern = new DocumentNumberPattern("XK", 4, NumberResetCycle.MONTHLY);

        assertThat(pattern.format("HCM", OCT_15, 7)).isEqualTo("XK-HCM-202610-0007");
        assertThat(pattern.periodKey(OCT_15)).isEqualTo("202610");
    }

    @Test
    @DisplayName("Báo lỗi khi bộ đếm vượt số chữ số thay vì sinh số sai định dạng")
    void rejectsOverflow() {
        assertThatThrownBy(() -> DocumentNumberPattern.defaultFor("PXK").format("HN", OCT_15, 1_000_000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Mã chi nhánh, tiền tố chỉ gồm A-Z, 0-9; độ dài số thứ tự 3-10")
    void rejectsInvalidCodes() {
        assertThatThrownBy(() -> new DocumentNumberRequest(1L, 2L, "hn", "PXK", OCT_15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentNumberRequest(1L, 2L, "HN", "PX-K", OCT_15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentNumberPattern("P-X", 6, NumberResetCycle.YEARLY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentNumberPattern("PX", 2, NumberResetCycle.YEARLY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Thay đổi có hiệu lực từ chu kỳ sau; đổi chu kỳ đặt lại thì từ đầu năm sau")
    void nextCycleStart() {
        assertThat(DocumentNumberPattern.nextCycleStart(NumberResetCycle.MONTHLY, NumberResetCycle.MONTHLY, OCT_15))
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(DocumentNumberPattern.nextCycleStart(NumberResetCycle.YEARLY, NumberResetCycle.YEARLY, OCT_15))
                .isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(DocumentNumberPattern.nextCycleStart(NumberResetCycle.YEARLY, NumberResetCycle.MONTHLY, OCT_15))
                .isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(DocumentNumberPattern.nextCycleStart(NumberResetCycle.MONTHLY, NumberResetCycle.YEARLY, OCT_15))
                .isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(DocumentNumberPattern.nextCycleStart(NumberResetCycle.MONTHLY, NumberResetCycle.MONTHLY,
                LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
