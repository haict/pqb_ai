package com.company.erp.common.infrastructure.persistence;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/** Điều kiện lọc dùng chung cho truy vấn danh sách phía server (api.md mục 4). */
public final class Specs {

    private Specs() {
    }

    /** Ghép các điều kiện bằng AND, bỏ qua điều kiện {@code null}. */
    @SafeVarargs
    public static <T> Specification<T> allOf(Specification<T>... specifications) {
        List<Specification<T>> present = Arrays.stream(specifications).filter(Objects::nonNull).toList();
        return (root, query, cb) -> cb.and(present.stream()
                .map(spec -> spec.toPredicate(root, query, cb))
                .filter(Objects::nonNull)
                .toArray(Predicate[]::new));
    }

    /** {@code field IN values}; tập rỗng thì không khớp bản ghi nào (phạm vi dữ liệu rỗng). */
    public static <T> Specification<T> in(String field, Collection<?> values) {
        return (root, query, cb) -> values.isEmpty() ? cb.disjunction() : root.get(field).in(values);
    }

    /** {@code field = value}; bỏ qua khi {@code value} rỗng. */
    public static <T> Specification<T> equalsIfPresent(String field, Object value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    /** Một trong các trường chứa từ khóa (không phân biệt hoa thường); bỏ qua khi từ khóa rỗng. */
    public static <T> Specification<T> keyword(String keyword, String... fields) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String field : fields) {
                Expression<String> value = cb.lower(root.get(field));
                predicates.add(cb.like(value, pattern, '\\'));
            }
            return cb.or(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
