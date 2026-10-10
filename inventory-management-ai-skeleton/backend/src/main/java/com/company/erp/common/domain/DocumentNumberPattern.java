package com.company.erp.common.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cách tạo số chứng từ: {@code <TIỀN TỐ>-<CHI NHÁNH>-<KỲ>-<SỐ THỨ TỰ>} (D-03, BR-SYS-10).
 * Chi nhánh luôn được in vì bộ đếm tách theo chi nhánh; kỳ ({@code YYYY} hoặc {@code YYYYMM}) luôn được in
 * vì bộ đếm đặt lại theo kỳ. Nhờ vậy số chứng từ không trùng giữa chi nhánh và giữa các chu kỳ.
 *
 * @param prefix         tiền tố in trên số, 1-10 ký tự A-Z, 0-9
 * @param sequenceLength số chữ số của số thứ tự (3-10)
 * @param resetCycle     chu kỳ đặt lại số thứ tự
 */
public record DocumentNumberPattern(String prefix, int sequenceLength, NumberResetCycle resetCycle) {

    /** D-03: 6 chữ số, đặt lại theo năm. */
    public static final int DEFAULT_SEQUENCE_LENGTH = 6;
    public static final NumberResetCycle DEFAULT_RESET_CYCLE = NumberResetCycle.YEARLY;
    public static final int MIN_SEQUENCE_LENGTH = 3;
    public static final int MAX_SEQUENCE_LENGTH = 10;
    public static final Pattern CODE = Pattern.compile("[A-Z0-9]{1,10}");

    public DocumentNumberPattern {
        if (prefix == null || !CODE.matcher(prefix).matches()) {
            throw new IllegalArgumentException("prefix phải gồm 1-10 ký tự A-Z, 0-9: " + prefix);
        }
        if (sequenceLength < MIN_SEQUENCE_LENGTH || sequenceLength > MAX_SEQUENCE_LENGTH) {
            throw new IllegalArgumentException("sequenceLength ngoài khoảng 3-10: " + sequenceLength);
        }
        Objects.requireNonNull(resetCycle, "resetCycle");
    }

    /** Định dạng D-03 cho loại chứng từ chưa được cấu hình: mã loại là tiền tố. */
    public static DocumentNumberPattern defaultFor(String documentType) {
        return new DocumentNumberPattern(documentType, DEFAULT_SEQUENCE_LENGTH, DEFAULT_RESET_CYCLE);
    }

    /** Khóa kỳ của bộ đếm, đồng thời là phần kỳ in trên số chứng từ. */
    public String periodKey(LocalDate documentDate) {
        return switch (resetCycle) {
            case YEARLY -> String.format("%04d", documentDate.getYear());
            case MONTHLY -> String.format("%04d%02d", documentDate.getYear(), documentDate.getMonthValue());
        };
    }

    public String format(String branchCode, LocalDate documentDate, long value) {
        String sequencePart = String.format("%0" + sequenceLength + "d", value);
        if (sequencePart.length() > sequenceLength) {
            throw new IllegalStateException("Số chứng từ vượt " + sequenceLength + " chữ số: "
                    + prefix + "/" + branchCode + "/" + periodKey(documentDate));
        }
        return prefix + "-" + branchCode + "-" + periodKey(documentDate) + "-" + sequencePart;
    }

    /**
     * Ngày bắt đầu chu kỳ kế tiếp, khi cấu hình mới có hiệu lực. Đổi chu kỳ đặt lại thì chỉ áp dụng từ đầu năm
     * sau, để khóa kỳ của bộ đếm cũ và mới không bao giờ cùng tồn tại trong một năm.
     */
    public static LocalDate nextCycleStart(NumberResetCycle currentCycle, NumberResetCycle newCycle, LocalDate today) {
        if (currentCycle == NumberResetCycle.MONTHLY && newCycle == NumberResetCycle.MONTHLY) {
            return today.withDayOfMonth(1).plusMonths(1);
        }
        return LocalDate.of(today.getYear() + 1, 1, 1);
    }
}
