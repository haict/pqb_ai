package com.company.erp.masterdata.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tìm kho theo tên nhóm hàng gần đúng (D-76): so khóa tên đã bỏ dấu, chữ hoa, ký tự đặc biệt và chữ "kho", theo cả từ
 * (nhóm "Đông máu" khớp "Kho đông máu"). Ưu tiên: khóa trùng hẳn, rồi tên kho chứa tên nhóm, rồi tên nhóm chứa tên kho
 * (khóa kho dài nhất). Cùng mức ưu tiên có nhiều kho thì không chọn, để người dùng chọn.
 */
public final class WarehouseNameMatcher {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final String WAREHOUSE_WORD = "kho";

    private WarehouseNameMatcher() {
    }

    /** Khóa so sánh: chữ thường không dấu, các từ cách nhau một khoảng trắng, bỏ chữ "kho". */
    public static String key(String name) {
        if (name == null) {
            return "";
        }
        String plain = MARKS.matcher(Normalizer.normalize(name.toLowerCase(Locale.ROOT).replace('đ', 'd'),
                Normalizer.Form.NFD)).replaceAll("");
        return Arrays.stream(NON_WORD.split(plain))
                .filter(word -> !word.isEmpty() && !word.equals(WAREHOUSE_WORD))
                .collect(Collectors.joining(" "));
    }

    /** Kho khớp tên nhóm; {@code warehouseNames}: id kho → tên. Không khớp hoặc khớp nhiều kho cùng mức: rỗng. */
    public static Optional<Long> match(String categoryName, Map<Long, String> warehouseNames) {
        String category = key(categoryName);
        if (category.isEmpty()) {
            return Optional.empty();
        }
        List<Long> exact = new ArrayList<>();
        List<Long> warehouseContains = new ArrayList<>();
        List<Long> categoryContains = new ArrayList<>();
        int longest = 0;
        for (Map.Entry<Long, String> entry : warehouseNames.entrySet()) {
            String warehouse = key(entry.getValue());
            if (warehouse.isEmpty()) {
                continue;
            }
            if (warehouse.equals(category)) {
                exact.add(entry.getKey());
            } else if (containsWords(warehouse, category)) {
                warehouseContains.add(entry.getKey());
            } else if (containsWords(category, warehouse)) {
                if (warehouse.length() > longest) {
                    categoryContains.clear();
                    longest = warehouse.length();
                }
                if (warehouse.length() == longest) {
                    categoryContains.add(entry.getKey());
                }
            }
        }
        for (List<Long> tier : List.of(exact, warehouseContains, categoryContains)) {
            if (!tier.isEmpty()) {
                return tier.size() == 1 ? Optional.of(tier.get(0)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean containsWords(String text, String words) {
        return (" " + text + " ").contains(" " + words + " ");
    }
}
