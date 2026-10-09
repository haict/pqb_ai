package com.company.erp.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Dữ liệu danh sách phân trang (docs/04 mục 1.2). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
