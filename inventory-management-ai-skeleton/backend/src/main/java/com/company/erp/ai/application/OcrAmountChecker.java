package com.company.erp.ai.application;

import com.company.erp.ai.application.dto.AmountCheck;
import com.company.erp.ai.application.dto.OcrValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;

/**
 * Kiểm tra số học trên giá trị OCR bằng {@code BigDecimal} (BR-AI-07): thành tiền dòng, tổng tiền hàng, tổng thanh toán.
 * Lệch quá dung sai thì MISMATCH và giữ nguyên giá trị đọc được; không tự sửa.
 */
public class OcrAmountChecker {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal tolerance;

    public OcrAmountChecker(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }

    /** Thành tiền = SL × đơn giá × (1 − CK%), làm tròn đến đồng, so với thành tiền in trên chứng từ. */
    public AmountCheck line(OcrValue quantity, OcrValue unitPrice, OcrValue discountPercent, OcrValue printed) {
        BigDecimal qty = decimal(quantity);
        BigDecimal price = decimal(unitPrice);
        if (qty == null || price == null) {
            return AmountCheck.UNKNOWN;
        }
        BigDecimal amount = qty.multiply(price);
        BigDecimal discount = decimal(discountPercent);
        if (discount != null) {
            amount = amount.multiply(HUNDRED.subtract(discount)).divide(HUNDRED);
        }
        amount = amount.setScale(0, RoundingMode.HALF_UP);
        return compare(amount, decimal(printed), "Thành tiền trên chứng từ %s khác SL × đơn giá %s.");
    }

    /** Tổng tiền hàng in so với cộng thành tiền các dòng, và tổng thanh toán so với tiền hàng + thuế. */
    public AmountCheck totals(List<OcrValue> lineAmounts, OcrValue goods, OcrValue vat, OcrValue total) {
        List<String> warnings = new ArrayList<>();
        BigDecimal goodsValue = decimal(goods);
        boolean allLines = !lineAmounts.isEmpty() && lineAmounts.stream().allMatch(v -> decimal(v) != null);
        boolean checked = false;
        if (goodsValue != null && allLines) {
            BigDecimal sum = lineAmounts.stream().map(OcrAmountChecker::decimal).reduce(BigDecimal.ZERO, BigDecimal::add);
            checked = true;
            addIfMismatch(warnings, sum, goodsValue, "Tổng tiền hàng trên chứng từ %s khác cộng thành tiền các dòng %s.");
        }
        BigDecimal vatValue = decimal(vat);
        BigDecimal totalValue = decimal(total);
        if (goodsValue != null && vatValue != null && totalValue != null) {
            checked = true;
            addIfMismatch(warnings, goodsValue.add(vatValue), totalValue,
                    "Tổng thanh toán trên chứng từ %s khác tiền hàng + tiền thuế %s.");
        }
        if (!checked) {
            return AmountCheck.UNKNOWN;
        }
        return new AmountCheck(warnings.isEmpty() ? "OK" : "MISMATCH", null, null, warnings);
    }

    private AmountCheck compare(BigDecimal calculated, BigDecimal printed, String message) {
        if (printed == null) {
            return new AmountCheck("UNKNOWN", calculated.toPlainString(), null, List.of());
        }
        List<String> warnings = new ArrayList<>();
        addIfMismatch(warnings, calculated, printed, message);
        return new AmountCheck(warnings.isEmpty() ? "OK" : "MISMATCH", calculated.toPlainString(),
                printed.toPlainString(), warnings);
    }

    private void addIfMismatch(List<String> warnings, BigDecimal calculated, BigDecimal printed, String message) {
        if (calculated.subtract(printed).abs().compareTo(tolerance) > 0) {
            warnings.add(String.format(message, format(printed), format(calculated)));
        }
    }

    private static BigDecimal decimal(OcrValue value) {
        if (value == null || !value.present()) {
            return null;
        }
        try {
            return new BigDecimal(value.value());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String format(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.###", symbols).format(value);
    }
}
