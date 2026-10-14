package com.company.erp.reporting.infrastructure;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tình hình thực hiện đơn mua / đơn bán (BR-RPT-25, 26) sau D-54: trạng thái, hẹn giao / nhận, giao hàng (đơn bán), trả
 * lại / giảm giá đã ghi sổ, đã thanh toán / đã thu và còn nợ từ sổ công nợ (ADR-18: SQL chỉ đọc).
 */
@Repository
public class OrderProgressJdbc {

    /** Điều kiện; {@code branchIds} null: mọi chi nhánh; {@code ownerId}: chỉ đơn mình phụ trách / lập (đơn bán). */
    public record Filter(Long companyId, Collection<Long> branchIds, LocalDate from, LocalDate to, Long partnerId,
                         String status, Long ownerId) {
    }

    /**
     * {@code returnedAmount}: tổng tiền (gồm thuế) trả lại / giảm giá đã ghi sổ còn hiệu lực; {@code paidAmount}: phần
     * công nợ của đơn đã thanh toán / thu (không tính trả lại, giảm giá), sau đảo; {@code remainingAmount}: còn nợ.
     */
    public record Row(Long documentId, String documentNo, LocalDate documentDate, LocalDate postingDate,
                      Long partnerId, String partnerCode, String partnerName, String status, LocalDate expectedDate,
                      String deliveryStatus, BigDecimal totalAmount, BigDecimal returnedAmount,
                      BigDecimal paidAmount, BigDecimal remainingAmount) {
    }

    /** Còn nợ của các khoản công nợ (chưa đảo) sinh từ chứng từ nguồn. */
    private static final String REMAINING = """
            SELECT source_id, SUM(remaining_amount) AS remaining FROM debt_items
            WHERE company_id = :companyId AND source_type = '%s' AND status <> 'REVERSED'
            GROUP BY source_id
            """;

    /**
     * Phần đã thanh toán / thu của khoản công nợ: dòng thu, chi, bù trừ (không tính trả lại, giảm giá); dòng đảo lấy
     * loại của dòng gốc và mang chiều ngược nên tự trừ.
     */
    private static final String PAID = """
            SELECT i.source_id, SUM(IF(i.debt_type = 'PAYABLE', e.debit_amount - e.credit_amount,
                                       e.credit_amount - e.debit_amount)) AS paid
            FROM debt_items i
            JOIN debt_ledger_entries e ON e.debt_item_id = i.id
            LEFT JOIN debt_ledger_entries o ON o.id = e.reversal_of_id
            WHERE i.company_id = :companyId AND i.source_type = '%s' AND i.status <> 'REVERSED'
              AND IF(e.entry_type = 'REVERSAL', o.entry_type, e.entry_type) IN ('RECEIPT', 'PAYMENT', 'OFFSET')
            GROUP BY i.source_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OrderProgressJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> purchaseOrders(Filter filter) {
        String sql = """
                SELECT h.id, h.document_no, h.document_date, h.posting_date, h.supplier_id AS partner_id,
                       p.code AS partner_code, p.name AS partner_name, h.status, h.expected_date,
                       NULL AS delivery_status, h.total_amount, COALESCE(r.returned, 0) AS returned,
                       COALESCE(pay.paid, 0) AS paid, COALESCE(rem.remaining, 0) AS remaining
                FROM purchase_orders h
                JOIN partners p ON p.id = h.supplier_id
                LEFT JOIN (SELECT purchase_order_id, SUM(total_amount) AS returned FROM purchase_returns
                           WHERE company_id = :companyId AND status = 'POSTED' GROUP BY purchase_order_id) r
                       ON r.purchase_order_id = h.id
                LEFT JOIN (%s) rem ON rem.source_id = h.id
                LEFT JOIN (%s) pay ON pay.source_id = h.id
                WHERE %s
                ORDER BY h.document_date, h.document_no
                """.formatted(REMAINING.formatted("DMH"), PAID.formatted("DMH"), where(filter, "h.supplier_id", false));
        return jdbc.query(sql, params(filter), OrderProgressJdbc::toRow);
    }

    public List<Row> salesOrders(Filter filter) {
        String sql = """
                SELECT h.id, h.document_no, h.document_date, h.posting_date, h.customer_id AS partner_id,
                       p.code AS partner_code, p.name AS partner_name, h.status, h.expected_date,
                       h.delivery_status, h.total_amount, COALESCE(r.returned, 0) AS returned,
                       COALESCE(pay.paid, 0) AS paid, COALESCE(rem.remaining, 0) AS remaining
                FROM sales_orders h
                JOIN partners p ON p.id = h.customer_id
                LEFT JOIN (SELECT sales_order_id, SUM(total_amount) AS returned FROM sales_returns
                           WHERE company_id = :companyId AND status = 'POSTED' GROUP BY sales_order_id) r
                       ON r.sales_order_id = h.id
                LEFT JOIN (%s) rem ON rem.source_id = h.id
                LEFT JOIN (%s) pay ON pay.source_id = h.id
                WHERE %s
                ORDER BY h.document_date, h.document_no
                """.formatted(REMAINING.formatted("DBH"), PAID.formatted("DBH"), where(filter, "h.customer_id", true));
        return jdbc.query(sql, params(filter), OrderProgressJdbc::toRow);
    }

    /** Đơn theo ngày chứng từ; bỏ đơn đã hủy trừ khi lọc đúng trạng thái hủy. */
    private static String where(Filter filter, String partnerColumn, boolean owned) {
        StringBuilder where = new StringBuilder(
                "h.company_id = :companyId AND h.document_date BETWEEN :from AND :to");
        if (filter.branchIds() != null) {
            where.append(" AND h.branch_id IN (:branchIds)");
        }
        if (filter.partnerId() != null) {
            where.append(" AND ").append(partnerColumn).append(" = :partnerId");
        }
        where.append(filter.status() != null ? " AND h.status = :status" : " AND h.status <> 'CANCELLED'");
        if (owned && filter.ownerId() != null) {
            where.append(" AND (h.sales_user_id = :ownerId OR h.created_by = :ownerId)");
        }
        return where.toString();
    }

    private static MapSqlParameterSource params(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", filter.companyId())
                .addValue("from", Date.valueOf(filter.from())).addValue("to", Date.valueOf(filter.to()))
                .addValue("partnerId", filter.partnerId()).addValue("status", filter.status())
                .addValue("ownerId", filter.ownerId());
        if (filter.branchIds() != null) {
            params.addValue("branchIds", filter.branchIds());
        }
        return params;
    }

    private static Row toRow(ResultSet rs, int rowNum) throws SQLException {
        Date expected = rs.getDate("expected_date");
        return new Row(rs.getLong("id"), rs.getString("document_no"), rs.getDate("document_date").toLocalDate(),
                rs.getDate("posting_date").toLocalDate(), rs.getLong("partner_id"), rs.getString("partner_code"),
                rs.getString("partner_name"), rs.getString("status"),
                expected == null ? null : expected.toLocalDate(), rs.getString("delivery_status"),
                rs.getBigDecimal("total_amount"), rs.getBigDecimal("returned"), rs.getBigDecimal("paid"),
                rs.getBigDecimal("remaining"));
    }
}
