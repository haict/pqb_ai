package com.company.erp.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Dòng tiền dự kiến đợt 10D (BR-RPT-09, 30, BR-CSH-06): số dư quỹ / ngân hàng hiện tại, dự thu, dự chi theo kỳ từ hôm
 * nay; {@code generatedAt}: thời điểm lấy số liệu (BR-RPT-13).
 */
public final class CashForecastResponse {

    private CashForecastResponse() {
    }

    /**
     * Mảng số tiền xếp theo {@code columns}: cột {@code OVERDUE} (ngày dự kiến trước hôm nay), các kỳ {@code P1..Pn},
     * cột {@code LATER} (sau kỳ cuối). {@code closing}: số dư lũy kế từ {@code openingBalance}. Người xem chỉ dữ liệu
     * của mình không thấy số dư quỹ ({@code cashBalanceIncluded = false}, số dư đầu 0).
     */
    public record Forecast(String granularity, LocalDate today, boolean cashBalanceIncluded,
                           BigDecimal openingBalance, List<CashBalance> cashAccounts, List<Column> columns,
                           List<SourceLine> lines, List<BigDecimal> inflow, List<BigDecimal> outflow,
                           List<BigDecimal> net, List<BigDecimal> closing, Instant generatedAt) {
    }

    /** Số dư quỹ / tài khoản ngân hàng tính từ sổ quỹ đến hôm nay. */
    public record CashBalance(Long id, String code, String name, String accountType, BigDecimal balance) {
    }

    /** {@code from} null: không giới hạn dưới (quá hạn); {@code to} null: không giới hạn trên (sau kỳ cuối). */
    public record Column(String key, LocalDate from, LocalDate to) {
    }

    /** Một nguồn: RECEIVABLE, SALES_ORDER (thu), PAYABLE, PURCHASE_ORDER (chi); số tiền dương. */
    public record SourceLine(String source, String direction, List<BigDecimal> amounts, BigDecimal total) {
    }

    /** Khoản cấu thành một ô (BR-RPT-11); {@code total} bằng số trong ô khi lọc đúng nguồn và khoảng ngày của cột. */
    public record Items(String source, LocalDate dateFrom, LocalDate dateTo, LocalDate today, List<ItemRow> rows,
                        BigDecimal total, Instant generatedAt) {
    }

    /** {@code overdueDays} 0 khi chưa đến ngày dự kiến. */
    public record ItemRow(String source, String documentType, Long documentId, String documentNo,
                          String partnerCode, String partnerName, LocalDate expectedDate, long overdueDays,
                          BigDecimal amount) {
    }
}
