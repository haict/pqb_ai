package com.company.erp.reporting.infrastructure;

import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.Scope;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Số lượng xuất bán theo đơn bán đã / chưa có số hóa đơn điện tử (D-68), tính từ sổ nhật ký kho theo ngày hạch toán:
 * phiếu xuất kho của đơn bán (nguồn {@code DBH}) cộng, phiếu nhập trả lại (gắn đơn bán qua {@code sales_returns}) trừ;
 * dòng đảo / bỏ ghi sổ trừ vào chứng từ gốc. Trạng thái hóa đơn lấy theo đơn bán hiện tại (ADR-18: SQL chỉ đọc).
 */
@Repository
public class IssuedInvoiceStatusJdbc {

    /** Tổng theo hàng × kho; số lượng theo đơn vị cơ sở. */
    public record QuantityRow(Long productId, Long warehouseId, BigDecimal invoicedQuantity,
                              BigDecimal uninvoicedQuantity) {
    }

    /** Một đơn bán cấu thành số lượng. */
    public record OrderRow(Long orderId, String documentNo, LocalDate postingDate, String customerCode,
                           String customerName, String einvoiceSeries, String einvoiceNo, LocalDate einvoiceDate,
                           BigDecimal quantity) {
    }

    private static final String SOURCE = """
            FROM inventory_transactions t
            LEFT JOIN inventory_transactions o ON o.id = t.reversal_of_id
            JOIN inventory_documents d ON d.id = COALESCE(o.reference_id, t.reference_id)
                 AND d.document_type = COALESCE(o.reference_type, t.reference_type)
            LEFT JOIN sales_returns r ON r.inventory_document_id = d.id AND d.source_type <> 'DBH'
            JOIN sales_orders so ON so.id = IF(d.source_type = 'DBH', d.source_id, r.sales_order_id)
            """;

    private static final String ISSUED = "IF(t.direction = 'OUT', t.quantity, -t.quantity)";

    private final NamedParameterJdbcTemplate jdbc;

    public IssuedInvoiceStatusJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<QuantityRow> quantities(Scope scope, Collection<Long> productIds, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = params(scope, from, to);
        StringBuilder where = where(scope, params);
        if (productIds != null && !productIds.isEmpty()) {
            where.append(" AND t.product_id IN (:productIds)");
            params.addValue("productIds", productIds);
        }
        String sql = "SELECT t.product_id, t.warehouse_id, "
                + "SUM(IF(so.einvoice_no IS NOT NULL, " + ISSUED + ", 0)) AS invoiced, "
                + "SUM(IF(so.einvoice_no IS NULL, " + ISSUED + ", 0)) AS uninvoiced "
                + SOURCE + where + " GROUP BY t.product_id, t.warehouse_id";
        return jdbc.query(sql, params, (rs, i) -> new QuantityRow(rs.getLong("product_id"),
                rs.getLong("warehouse_id"), rs.getBigDecimal("invoiced"), rs.getBigDecimal("uninvoiced")));
    }

    public List<OrderRow> orders(Scope scope, Long productId, boolean invoiced, LocalDate from, LocalDate to,
                                 int limit) {
        MapSqlParameterSource params = params(scope, from, to).addValue("productId", productId)
                .addValue("limit", limit);
        StringBuilder where = where(scope, params).append(" AND t.product_id = :productId AND so.einvoice_no IS ")
                .append(invoiced ? "NOT NULL" : "NULL");
        String sql = "SELECT so.id, so.document_no, so.posting_date, c.code, c.name, so.einvoice_series, "
                + "so.einvoice_no, so.einvoice_date, SUM(" + ISSUED + ") AS quantity "
                + SOURCE + " JOIN partners c ON c.id = so.customer_id" + where
                + " GROUP BY so.id, so.document_no, so.posting_date, c.code, c.name, so.einvoice_series, "
                + "so.einvoice_no, so.einvoice_date HAVING quantity <> 0"
                + " ORDER BY so.posting_date, so.document_no LIMIT :limit";
        return jdbc.query(sql, params, (rs, i) -> new OrderRow(rs.getLong("id"), rs.getString("document_no"),
                date(rs.getDate("posting_date")), rs.getString("code"), rs.getString("name"),
                rs.getString("einvoice_series"), rs.getString("einvoice_no"), date(rs.getDate("einvoice_date")),
                rs.getBigDecimal("quantity")));
    }

    private static MapSqlParameterSource params(Scope scope, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("companyId", scope.companyId())
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
    }

    private static StringBuilder where(Scope scope, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(
                " WHERE t.company_id = :companyId AND t.posting_date BETWEEN :from AND :to");
        if (scope.warehouseIds() != null) {
            where.append(" AND t.warehouse_id IN (:warehouseIds)");
            params.addValue("warehouseIds", scope.warehouseIds());
        }
        return where;
    }

    private static LocalDate date(Date value) {
        return value == null ? null : value.toLocalDate();
    }
}
