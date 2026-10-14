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
 * Chỉ tiêu tình trạng hiện tại cho dashboard (ADR-18: SQL chỉ đọc): khoản công nợ còn lại (BR-RPT-02), lô còn tồn đã /
 * sắp hết hạn (BR-RPT-04), đơn mua / bán chưa giao đủ (BR-RPT-05, 06). Ứng dụng tính tổng từ chính các dòng này nên tổng
 * chi tiết bằng số trên dashboard (BR-RPT-11).
 */
@Repository
public class DashboardStatusJdbc {

    /** {@code branchIds} null: mọi chi nhánh; {@code ownerId}: chỉ dữ liệu mình phụ trách / lập. */
    public record Scope(Long companyId, Collection<Long> branchIds, Long ownerId) {
    }

    public record DebtItem(Long id, String sourceType, Long sourceId, String sourceNo, String invoiceNo,
                           String partnerCode, String partnerName, LocalDate documentDate, LocalDate dueDate,
                           BigDecimal originalAmount, BigDecimal settledAmount, BigDecimal remainingAmount) {
    }

    public record LotStock(Long productId, String productCode, String productName, String unitName,
                           String warehouseCode, Long lotId, String lotNo, LocalDate expiryDate, BigDecimal onHand) {
    }

    public record OpenOrder(Long id, String documentNo, LocalDate documentDate, String partnerCode,
                            String partnerName, String status, String deliveryStatus, LocalDate expectedDate,
                            BigDecimal totalAmount) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardStatusJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Khoản công nợ còn lại ({@code RECEIVABLE} / {@code PAYABLE}), theo hạn thanh toán. */
    public List<DebtItem> openDebts(Scope scope, String debtType) {
        String sql = """
                SELECT i.id, i.source_type, i.source_id, i.source_no, i.invoice_no, p.code, p.name, i.document_date,
                       i.due_date, i.original_amount, i.settled_amount, i.remaining_amount
                FROM debt_items i JOIN partners p ON p.id = i.partner_id
                WHERE i.company_id = :companyId AND i.debt_type = :debtType AND i.status IN ('OPEN', 'PARTIAL')
                  AND i.remaining_amount > 0%s%s
                ORDER BY i.due_date, i.source_no, i.id
                """.formatted(branches(scope, "i"),
                scope.ownerId() == null ? "" : " AND (i.sales_user_id = :ownerId OR i.created_by = :ownerId)");
        return jdbc.query(sql, params(scope).addValue("debtType", debtType), DashboardStatusJdbc::debtItem);
    }

    /**
     * Lô còn tồn hạn dùng đến {@code until} (gồm lô đã quá hạn) trong các kho được xem; {@code warehouseIds} null: mọi
     * kho, {@code branchId}: chỉ kho của chi nhánh đang chọn.
     */
    public List<LotStock> expiringLots(Long companyId, Collection<Long> warehouseIds, Long branchId,
                                       LocalDate until) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId)
                .addValue("until", Date.valueOf(until)).addValue("branchId", branchId);
        StringBuilder where = new StringBuilder();
        if (warehouseIds != null) {
            where.append(" AND b.warehouse_id IN (:warehouseIds)");
            params.addValue("warehouseIds", warehouseIds);
        }
        if (branchId != null) {
            where.append(" AND w.branch_id = :branchId");
        }
        String sql = """
                SELECT p.id AS product_id, p.code AS product_code, p.name AS product_name, u.name AS unit_name,
                       w.code AS warehouse_code, l.id AS lot_id, l.lot_no, l.expiry_date,
                       SUM(b.on_hand_quantity) AS on_hand
                FROM inventory_balances b
                JOIN inventory_lots l ON l.id = b.lot_id
                JOIN products p ON p.id = b.product_id
                JOIN units u ON u.id = p.base_unit_id
                JOIN warehouses w ON w.id = b.warehouse_id
                WHERE b.company_id = :companyId AND b.on_hand_quantity > 0 AND l.expiry_date IS NOT NULL
                  AND l.expiry_date <= :until%s
                GROUP BY p.id, p.code, p.name, u.name, w.code, l.id, l.lot_no, l.expiry_date
                ORDER BY l.expiry_date, p.code, w.code, l.lot_no
                """.formatted(where);
        return jdbc.query(sql, params, (rs, rowNum) -> new LotStock(rs.getLong("product_id"),
                rs.getString("product_code"), rs.getString("product_name"), rs.getString("unit_name"),
                rs.getString("warehouse_code"), rs.getLong("lot_id"), rs.getString("lot_no"),
                rs.getDate("expiry_date").toLocalDate(), rs.getBigDecimal("on_hand")));
    }

    /** Đơn mua đã gửi / duyệt chưa ghi sổ (sau D-54 ghi sổ = nhập kho đủ). */
    public List<OpenOrder> openPurchaseOrders(Scope scope) {
        String sql = """
                SELECT h.id, h.document_no, h.document_date, p.code, p.name, h.status, NULL AS delivery_status,
                       h.expected_date, h.total_amount * h.exchange_rate AS total_amount
                FROM purchase_orders h JOIN partners p ON p.id = h.supplier_id
                WHERE h.company_id = :companyId AND h.status IN ('SUBMITTED', 'APPROVED')%s%s
                ORDER BY h.document_date, h.document_no
                """.formatted(branches(scope, "h"), scope.ownerId() == null ? "" : " AND h.created_by = :ownerId");
        return jdbc.query(sql, params(scope), DashboardStatusJdbc::openOrder);
    }

    /** Đơn bán đã gửi / duyệt chưa ghi sổ (chưa xuất) và đơn đã ghi sổ khách chưa nhận. */
    public List<OpenOrder> openSalesOrders(Scope scope) {
        String sql = """
                SELECT h.id, h.document_no, h.document_date, p.code, p.name, h.status, h.delivery_status,
                       h.expected_date, h.total_amount
                FROM sales_orders h JOIN partners p ON p.id = h.customer_id
                WHERE h.company_id = :companyId
                  AND (h.status IN ('SUBMITTED', 'APPROVED') OR (h.status = 'POSTED' AND h.delivery_status <> 'DELIVERED'))
                  %s%s
                ORDER BY h.document_date, h.document_no
                """.formatted(branches(scope, "h"),
                scope.ownerId() == null ? "" : " AND (h.sales_user_id = :ownerId OR h.created_by = :ownerId)");
        return jdbc.query(sql, params(scope), DashboardStatusJdbc::openOrder);
    }

    private static String branches(Scope scope, String alias) {
        return scope.branchIds() == null ? "" : " AND " + alias + ".branch_id IN (:branchIds)";
    }

    private static MapSqlParameterSource params(Scope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", scope.companyId())
                .addValue("ownerId", scope.ownerId());
        if (scope.branchIds() != null) {
            params.addValue("branchIds", scope.branchIds());
        }
        return params;
    }

    private static DebtItem debtItem(ResultSet rs, int rowNum) throws SQLException {
        return new DebtItem(rs.getLong("id"), rs.getString("source_type"), rs.getLong("source_id"),
                rs.getString("source_no"), rs.getString("invoice_no"), rs.getString("code"), rs.getString("name"),
                rs.getDate("document_date").toLocalDate(), rs.getDate("due_date").toLocalDate(),
                rs.getBigDecimal("original_amount"), rs.getBigDecimal("settled_amount"),
                rs.getBigDecimal("remaining_amount"));
    }

    private static OpenOrder openOrder(ResultSet rs, int rowNum) throws SQLException {
        Date expected = rs.getDate("expected_date");
        return new OpenOrder(rs.getLong("id"), rs.getString("document_no"), rs.getDate("document_date").toLocalDate(),
                rs.getString("code"), rs.getString("name"), rs.getString("status"), rs.getString("delivery_status"),
                expected == null ? null : expected.toLocalDate(), rs.getBigDecimal("total_amount"));
    }
}
