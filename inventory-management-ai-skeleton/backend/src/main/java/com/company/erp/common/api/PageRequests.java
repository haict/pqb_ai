package com.company.erp.common.api;

import com.company.erp.common.domain.BusinessException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Kiểm tra trường sắp xếp theo danh sách cho phép (api.md mục 4, security.md mục 4). */
public final class PageRequests {

    private PageRequests() {
    }

    /**
     * Trả {@link Pageable} hợp lệ; trường sắp xếp ngoài danh sách cho phép trả VALIDATION_ERROR.
     * Không có sắp xếp thì dùng {@code defaultSort}.
     */
    public static Pageable checked(Pageable pageable, Set<String> allowedSortFields, Sort defaultSort) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw BusinessException.invalidField("sort",
                        "Không hỗ trợ sắp xếp theo '" + order.getProperty() + "'. Cho phép: " + allowedSortFields);
            }
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
