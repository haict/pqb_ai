package com.company.erp.ai.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Khóa so khớp văn bản OCR với danh mục (docs/14 mục 4.4 {@code matchKey}).
 *
 * <p>Model nhận dạng đang dùng ({@code latin_PP-OCRv5_mobile_rec}, docs/14 mục 9.4) không có các chữ có dấu dưới đây
 * trong từ điển nên làm rơi chúng ("Hộp" đọc thành "Hp"). Bỏ cùng tập chữ này ở cả hai phía rồi bỏ dấu, để tên trong
 * danh mục và văn bản OCR cho cùng một khóa. Đổi model thì phải đo lại và sửa tập chữ này.
 */
public final class OcrTextKey {

    private static final String MODEL_MISSING_CHARS = "ảạằắẳẵặầấẩẫậẻẽẹềếểễệỉịỏọồốổỗộờớởỡợủụừứửữựỳỷỹỵ";
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private OcrTextKey() {
    }

    public static String of(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder(text.length());
        Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).codePoints()
                .filter(c -> MODEL_MISSING_CHARS.indexOf(c) < 0)
                .forEach(kept::appendCodePoint);
        String plain = MARKS.matcher(Normalizer.normalize(kept.toString().replace('đ', 'd'), Normalizer.Form.NFD))
                .replaceAll("");
        return NON_ALNUM.matcher(plain).replaceAll(" ").trim();
    }

    /** Độ giống 0..1 theo khoảng cách Levenshtein trên khóa đã chuẩn hóa. */
    public static double similarity(String firstKey, String secondKey) {
        int longest = Math.max(firstKey.length(), secondKey.length());
        return longest == 0 ? 1.0 : 1.0 - (double) levenshtein(firstKey, secondKey) / longest;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
