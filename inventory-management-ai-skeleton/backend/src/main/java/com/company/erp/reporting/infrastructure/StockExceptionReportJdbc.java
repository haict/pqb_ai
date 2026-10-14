package com.company.erp.reporting.infrastructure;

import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.Scope;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hàng tồn lâu, hàng ngoại lệ (hết hạn, không đạt, cách ly, thu hồi, lô bị khóa), hàng thiếu hồ sơ (BR-RPT-23) từ số dư
 * và sổ nhật ký kho, hồ sơ pháp lý của danh mục (ADR-18: SQL chỉ đọc).
 */
@Repository
public class StockExceptionReportJdbc {

    /** Tồn theo hàng × kho kèm ngày xuất / nhập gần nhất (không tính chuyển trạng thái chất lượng, dòng đảo). */
    public record SlowRow(Long productId, String productCode, String productName, String unitName, Long warehouseId,
                          String warehouseCode, BigDecimal onHand, LocalDate lastInDate, LocalDate lastOutDate) {
    }

    /** Dòng số dư có ngoại lệ; {@code blocked}: lô / serial bị khóa. */
    public record ExceptionRow(Long productId, String productCode, String productName, String unitName,
                               String warehouseCode, String lotNo, LocalDate expiryDate, String serialNo,
                               String qualityStatus, boolean blocked, BigDecimal onHand) {
    }

    /** Hàng còn tồn thiếu hồ sơ bắt buộc; {@code expiredDocumentNo} khác null: có hồ sơ nhưng đã hết hạn. */
    public record MissingDocumentRow(Long productId, String productCode, String productName, String productType,
                                     String documentTypeCode, String documentTypeName, String expiredDocumentNo,
                                     LocalDate expiredOn, BigDecimal onHand) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public StockExceptionReportJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Hàng còn tồn mà lần xuất gần nhất (chưa xuất: lần nhập gần nhất) trước {@code cutoff}. */
    public List<SlowRow> slowMoving(Scope scope, LocalDate cutoff) {
        MapSqlParameterSource params = params(scope).addValue("cutoff", Date.valueOf(cutoff));
        return jdbc.query("""
                SELECT b.product_id, p.code AS product_code, p.name AS product_name, u.name AS unit_name,
                       b.warehouse_id, w.code AS warehouse_code, b.on_hand, m.last_in, m.last_out
                FROM (SELECT product_id, warehouse_id, SUM(on_hand_quantity) AS on_hand FROM inventory_balances
                      WHERE company_id = :companyId %s GROUP BY product_id, warehouse_id
                      HAVING SUM(on_hand_quantity) > 0) b
                LEFT JOIN (SELECT product_id, warehouse_id,
                                  MAX(IF(direction = 'IN', posting_date, NULL)) AS last_in,
                                  MAX(IF(direction = 'OUT', posting_date, NULL)) AS last_out
                           FROM inventory_transactions
                           WHERE company_id = :companyId AND reversal_of_id IS NULL
                             AND transaction_type NOT IN ('STATUS_IN', 'STATUS_OUT', 'REVERSAL') %s
                           GROUP BY product_id, warehouse_id) m
                       ON m.product_id = b.product_id AND m.warehouse_id = b.warehouse_id
                JOIN products p ON p.id = b.product_id
                JOIN units u ON u.id = p.base_unit_id
                JOIN warehouses w ON w.id = b.warehouse_id
                WHERE COALESCE(m.last_out, m.last_in) IS NULL OR COALESCE(m.last_out, m.last_in) < :cutoff
                ORDER BY COALESCE(m.last_out, m.last_in), p.code, w.code
                """.formatted(warehouseFilter(scope, "warehouse_id"), warehouseFilter(scope, "warehouse_id")),
                params, (rs, i) -> new SlowRow(rs.getLong("product_id"), rs.getString("product_code"),
                        rs.getString("product_name"), rs.getString("unit_name"), rs.getLong("warehouse_id"),
                        rs.getString("warehouse_code"), rs.getBigDecimal("on_hand"), date(rs.getDate("last_in")),
                        date(rs.getDate("last_out"))));
    }

    /** Số dư còn tồn ở trạng thái khác ĐẠT, lô hết hạn trước {@code today}, lô / serial bị khóa. */
    public List<ExceptionRow> exceptions(Scope scope, LocalDate today) {
        MapSqlParameterSource params = params(scope).addValue("today", Date.valueOf(today));
        return jdbc.query("""
                SELECT b.product_id, p.code AS product_code, p.name AS product_name, u.name AS unit_name,
                       w.code AS warehouse_code, l.lot_no, l.expiry_date, s.serial_no, b.quality_status,
                       (COALESCE(l.blocked, FALSE) OR COALESCE(s.blocked, FALSE)) AS blocked, b.on_hand_quantity
                FROM inventory_balances b
                JOIN products p ON p.id = b.product_id
                JOIN units u ON u.id = p.base_unit_id
                JOIN warehouses w ON w.id = b.warehouse_id
                LEFT JOIN inventory_lots l ON l.id = b.lot_id
                LEFT JOIN inventory_serials s ON s.id = b.serial_id
                WHERE b.company_id = :companyId AND b.on_hand_quantity > 0 %s
                  AND (b.quality_status <> 'PASSED' OR l.expiry_date < :today OR l.blocked OR s.blocked)
                ORDER BY p.code, w.code, l.expiry_date, l.lot_no, s.serial_no
                """.formatted(warehouseFilter(scope, "b.warehouse_id")), params,
                (rs, i) -> new ExceptionRow(rs.getLong("product_id"), rs.getString("product_code"),
                        rs.getString("product_name"), rs.getString("unit_name"), rs.getString("warehouse_code"),
                        rs.getString("lot_no"), date(rs.getDate("expiry_date")), rs.getString("serial_no"),
                        rs.getString("quality_status"), rs.getBoolean("blocked"),
                        rs.getBigDecimal("on_hand_quantity")));
    }

    /**
     * Hàng còn tồn có loại hàng nằm trong "loại hàng bắt buộc" của loại hồ sơ áp dụng cho hàng hóa (BR-MD-44) nhưng
     * không có hồ sơ đang hiệu lực (còn hạn hoặc không thời hạn) tại {@code today}.
     */
    public List<MissingDocumentRow> missingDocuments(Scope scope, LocalDate today) {
        MapSqlParameterSource params = params(scope).addValue("today", Date.valueOf(today));
        return jdbc.query("""
                SELECT p.id AS product_id, p.code AS product_code, p.name AS product_name, p.product_type,
                       dt.code AS type_code, dt.name AS type_name, x.document_no AS expired_no,
                       x.expiry_date AS expired_on, b.on_hand
                FROM (SELECT product_id, SUM(on_hand_quantity) AS on_hand FROM inventory_balances
                      WHERE company_id = :companyId %s GROUP BY product_id HAVING SUM(on_hand_quantity) > 0) b
                JOIN products p ON p.id = b.product_id
                JOIN compliance_document_types dt ON dt.company_id = p.company_id AND dt.status = 'ACTIVE'
                     AND dt.applies_to = 'PRODUCT' AND FIND_IN_SET(p.product_type, dt.required_product_types) > 0
                LEFT JOIN (SELECT d.document_type_id, d.owner_id, MAX(d.expiry_date) AS expiry_date,
                                  MAX(d.document_no) AS document_no
                           FROM compliance_documents d
                           WHERE d.owner_type = 'PRODUCT' AND d.status = 'ACTIVE'
                           GROUP BY d.document_type_id, d.owner_id) x
                       ON x.document_type_id = dt.id AND x.owner_id = p.id
                WHERE NOT EXISTS (SELECT 1 FROM compliance_documents d
                                  WHERE d.document_type_id = dt.id AND d.owner_type = 'PRODUCT' AND d.owner_id = p.id
                                    AND d.status = 'ACTIVE' AND (d.expiry_date IS NULL OR d.expiry_date >= :today))
                ORDER BY p.code, dt.code
                """.formatted(warehouseFilter(scope, "warehouse_id")), params,
                (rs, i) -> new MissingDocumentRow(rs.getLong("product_id"), rs.getString("product_code"),
                        rs.getString("product_name"), rs.getString("product_type"), rs.getString("type_code"),
                        rs.getString("type_name"), rs.getString("expired_no"), date(rs.getDate("expired_on")),
                        rs.getBigDecimal("on_hand")));
    }

    private static MapSqlParameterSource params(Scope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", scope.companyId());
        if (scope.warehouseIds() != null) {
            params.addValue("warehouseIds", scope.warehouseIds());
        }
        return params;
    }

    private static String warehouseFilter(Scope scope, String column) {
        return scope.warehouseIds() == null ? "" : " AND " + column + " IN (:warehouseIds)";
    }

    private static LocalDate date(Date value) {
        return value == null ? null : value.toLocalDate();
    }
}
