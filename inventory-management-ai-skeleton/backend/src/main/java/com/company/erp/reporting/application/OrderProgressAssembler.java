package com.company.erp.reporting.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.reporting.application.dto.OrderReportResponse.OrderProgress;
import com.company.erp.reporting.application.dto.OrderReportResponse.OrderRow;
import com.company.erp.reporting.application.dto.OrderReportResponse.OrderTotals;
import com.company.erp.reporting.infrastructure.OrderProgressJdbc.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Dựng báo cáo tình hình thực hiện đơn (mua / bán): số ngày trễ hẹn và dòng tổng. */
@Component
class OrderProgressAssembler {

    /** Đơn không còn trạng thái CANCELLED: đơn chưa từng ghi sổ được xóa hẳn (D-65). */
    private static final Set<String> STATUSES = Set.of("DRAFT", "SUBMITTED", "REJECTED", "APPROVED", "POSTED",
            "REVERSED");
    /** Đơn chưa ghi sổ còn hiệu lực: nháp, và đơn đã duyệt trước D-61 (D-61 không qua duyệt). */
    private static final Set<String> OPEN = Set.of("DRAFT", "APPROVED");

    /** Trạng thái lọc hợp lệ hoặc {@code null}; sai: lỗi trường {@code status}. */
    static String status(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if (!STATUSES.contains(status)) {
            throw BusinessException.invalidField("status", "Trạng thái không hợp lệ.");
        }
        return status;
    }

    OrderProgress progress(String documentType, LocalDate from, LocalDate to, List<Row> rows, LocalDate today,
                           Instant generatedAt) {
        List<OrderRow> result = rows.stream().map(row -> new OrderRow(row.documentId(), row.documentNo(),
                row.documentDate(), row.postingDate(), row.partnerId(), row.partnerCode(), row.partnerName(),
                row.status(), row.expectedDate(), row.deliveryStatus(), overdueDays(row, today), row.totalAmount(),
                row.returnedAmount(), row.paidAmount(), row.remainingAmount())).toList();
        OrderTotals total = new OrderTotals(result.size(), result.stream().filter(row -> row.overdueDays() != null)
                .count(), sum(result, OrderRow::totalAmount), sum(result, OrderRow::returnedAmount),
                sum(result, OrderRow::paidAmount), sum(result, OrderRow::remainingAmount));
        return new OrderProgress(documentType, from, to, result, total, generatedAt);
    }

    /**
     * Trễ hẹn: đơn nháp / đã duyệt trước D-61 chưa ghi sổ (chưa nhập / xuất kho) hoặc đơn bán đã ghi sổ mà khách chưa nhận, quá
     * ngày dự kiến.
     */
    private static Long overdueDays(Row row, LocalDate today) {
        boolean pending = OPEN.contains(row.status()) || ("POSTED".equals(row.status())
                && row.deliveryStatus() != null && !"DELIVERED".equals(row.deliveryStatus()));
        if (!pending || row.expectedDate() == null || !row.expectedDate().isBefore(today)) {
            return null;
        }
        return ChronoUnit.DAYS.between(row.expectedDate(), today);
    }

    private static BigDecimal sum(List<OrderRow> rows, Function<OrderRow, BigDecimal> value) {
        return rows.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
