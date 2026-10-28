package com.company.erp.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.erp.ai.application.dto.AmountCheck;
import com.company.erp.ai.application.dto.OcrValue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcrAmountCheckerTest {

    private final OcrAmountChecker checker = new OcrAmountChecker(BigDecimal.ONE);

    @Test
    @DisplayName("BR-AI-07: thành tiền = SL × đơn giá × (1 − CK%), làm tròn đồng")
    void lineAmountWithDiscount() {
        AmountCheck check = checker.line(v("1"), v("2273000"), v("10"), v("2045700"));
        assertThat(check.status()).isEqualTo("OK");
        assertThat(check.calculated()).isEqualTo("2045700");
    }

    @Test
    @DisplayName("BR-AI-07: lệch quá dung sai thì MISMATCH, giữ giá trị in trên chứng từ")
    void lineMismatchKeepsPrintedValue() {
        AmountCheck check = checker.line(v("6"), v("6863000"), OcrValue.UNKNOWN, v("41278000"));
        assertThat(check.status()).isEqualTo("MISMATCH");
        assertThat(check.printed()).isEqualTo("41278000");
        assertThat(check.calculated()).isEqualTo("41178000");
        assertThat(check.warnings()).containsExactly(
                "Thành tiền trên chứng từ 41.278.000 khác SL × đơn giá 41.178.000.");
    }

    @Test
    @DisplayName("Chênh lệch làm tròn trong dung sai vẫn OK; thiếu số thì UNKNOWN")
    void toleranceAndMissingValues() {
        assertThat(checker.line(v("3"), v("333.333"), OcrValue.UNKNOWN, v("1001")).status()).isEqualTo("OK");
        assertThat(checker.line(v("3"), OcrValue.UNKNOWN, OcrValue.UNKNOWN, v("1000")).status()).isEqualTo("UNKNOWN");
        assertThat(checker.line(v("3"), v("1000"), OcrValue.UNKNOWN, OcrValue.UNKNOWN).calculated()).isEqualTo("3000");
    }

    @Test
    @DisplayName("Tổng tiền hàng so với cộng các dòng; tổng thanh toán so với tiền hàng + thuế")
    void totals() {
        List<OcrValue> lines = List.of(v("1000000"), v("500000"));
        assertThat(checker.totals(lines, v("1500000"), v("150000"), v("1650000")).status()).isEqualTo("OK");

        AmountCheck wrong = checker.totals(lines, v("1600000"), v("150000"), v("1650000"));
        assertThat(wrong.status()).isEqualTo("MISMATCH");
        assertThat(wrong.warnings()).hasSize(2);

        assertThat(checker.totals(List.of(), OcrValue.UNKNOWN, OcrValue.UNKNOWN, OcrValue.UNKNOWN).status())
                .isEqualTo("UNKNOWN");
    }

    private static OcrValue v(String value) {
        return new OcrValue(value, value, "OCR", 0.99, "OK", List.of());
    }
}
