package com.company.erp.reporting.infrastructure;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Thẻ kho và nhập - xuất - tồn (BR-RPT-21, 22) đọc sổ nhật ký kho {@code inventory_transactions} (ADR-18: SQL chỉ đọc).
 * Số lượng theo đơn vị cơ sở của hàng.
 */
@Repository
public class StockLedgerReportJdbc {

    /** Phạm vi chung; {@code warehouseIds} null: mọi kho của công ty. */
    public record Scope(Long companyId, Collection<Long> warehouseIds) {
    }

    public record ProductHeader(Long id, String code, String name, String unitName) {
    }

    /** Một dòng thẻ kho; {@code direction} IN / OUT; {@code reversal}: dòng đảo của chứng từ. */
    public record CardRow(Long id, LocalDate postingDate, String transactionType, String direction,
                          BigDecimal quantity, String referenceType, Long referenceId, String referenceNo,
                          boolean reversal, String warehouseCode, String lotNo, LocalDate expiryDate,
                          String serialNo, String partnerName, String qualityStatus) {
    }

    /** Một dòng nhập - xuất - tồn theo hàng × kho. */
    public record MovementRow(Long productId, String productCode, String productName, String unitName,
                              Long warehouseId, String warehouseCode, String warehouseName, BigDecimal opening,
                              BigDecimal inQuantity, BigDecimal outQuantity) {
    }

    /** Chuyển trạng thái chất lượng trong cùng kho: không tính vào nhập / xuất của kỳ (tổng bằng 0). */
    private static final String MOVEMENT_TYPE = """
            CASE WHEN t.transaction_type IN ('STATUS_IN', 'STATUS_OUT')
                   OR (t.transaction_type = 'REVERSAL' AND o.transaction_type IN ('STATUS_IN', 'STATUS_OUT'))
                 THEN 'STATUS' ELSE 'MOVE' END
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public StockLedgerReportJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductHeader> product(Long companyId, Long productId) {
        return jdbc.query("""
                SELECT p.id, p.code, p.name, u.name AS unit_name FROM products p JOIN units u ON u.id = p.base_unit_id
                WHERE p.company_id = :companyId AND p.id = :productId
                """, new MapSqlParameterSource("companyId", companyId).addValue("productId", productId),
                (rs, i) -> new ProductHeader(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("unit_name"))).stream().findFirst();
    }

    /** Hàng của serial (lịch sử serial dùng thẻ kho lọc theo serial). */
    public Optional<Long> productOfSerial(Long companyId, Long serialId) {
        return jdbc.queryForList("SELECT product_id FROM inventory_serials WHERE company_id = :companyId AND id = :id",
                new MapSqlParameterSource("companyId", companyId).addValue("id", serialId), Long.class)
                .stream().findFirst();
    }

    /** Tồn trước ngày {@code from} theo điều kiện thẻ kho. */
    public BigDecimal opening(Scope scope, Long productId, Long lotId, Long serialId, LocalDate from) {
        MapSqlParameterSource params = cardParams(scope, productId, lotId, serialId).addValue("from", Date.valueOf(from));
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(IF(t.direction = 'IN', t.quantity, -t.quantity)), 0)
                FROM inventory_transactions t WHERE %s AND t.posting_date < :from
                """.formatted(cardWhere(scope, lotId, serialId)), params, BigDecimal.class);
    }

    /** Dòng phát sinh trong kỳ theo thứ tự ghi sổ; lấy tối đa {@code limit} dòng. */
    public List<CardRow> cardLines(Scope scope, Long productId, Long lotId, Long serialId, LocalDate from,
                                   LocalDate to, int limit) {
        MapSqlParameterSource params = cardParams(scope, productId, lotId, serialId)
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to)).addValue("limit", limit);
        return jdbc.query("""
                SELECT t.id, t.posting_date, t.transaction_type, t.direction, t.quantity, t.reference_type,
                       t.reference_id, t.reference_no, t.reversal_of_id IS NOT NULL AS reversal, w.code AS warehouse_code,
                       l.lot_no, l.expiry_date, s.serial_no, p.name AS partner_name, t.quality_status
                FROM inventory_transactions t
                JOIN warehouses w ON w.id = t.warehouse_id
                LEFT JOIN inventory_lots l ON l.id = t.lot_id
                LEFT JOIN inventory_serials s ON s.id = t.serial_id
                LEFT JOIN partners p ON p.id = t.partner_id
                WHERE %s AND t.posting_date BETWEEN :from AND :to
                ORDER BY t.posting_date, t.posted_at, t.id
                LIMIT :limit
                """.formatted(cardWhere(scope, lotId, serialId)), params, (rs, i) -> {
                    Date expiry = rs.getDate("expiry_date");
                    return new CardRow(rs.getLong("id"), rs.getDate("posting_date").toLocalDate(),
                            rs.getString("transaction_type"), rs.getString("direction"), rs.getBigDecimal("quantity"),
                            rs.getString("reference_type"), rs.getLong("reference_id"), rs.getString("reference_no"),
                            rs.getBoolean("reversal"), rs.getString("warehouse_code"), rs.getString("lot_no"),
                            expiry == null ? null : expiry.toLocalDate(), rs.getString("serial_no"),
                            rs.getString("partner_name"), rs.getString("quality_status"));
                });
    }

    /**
     * Nhập - xuất - tồn theo hàng × kho: tồn đầu (trước {@code from}), nhập, xuất trong kỳ. Dòng đảo trừ vào chiều của
     * chứng từ gốc (đảo phiếu xuất giảm số xuất, không tăng số nhập); chuyển trạng thái chất lượng không tính.
     */
    public List<MovementRow> movement(Scope scope, Collection<Long> productIds, Collection<Long> categoryIds,
                                      LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", scope.companyId())
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        StringBuilder where = new StringBuilder("t.company_id = :companyId AND t.posting_date <= :to");
        if (scope.warehouseIds() != null) {
            where.append(" AND t.warehouse_id IN (:warehouseIds)");
            params.addValue("warehouseIds", scope.warehouseIds());
        }
        if (productIds != null && !productIds.isEmpty()) {
            where.append(" AND t.product_id IN (:productIds)");
            params.addValue("productIds", productIds);
        }
        if (categoryIds != null) {
            where.append(" AND pr.category_id IN (:categoryIds)");
            params.addValue("categoryIds", categoryIds);
        }
        String sql = """
                SELECT m.product_id, pr.code AS product_code, pr.name AS product_name, u.name AS unit_name,
                       m.warehouse_id, w.code AS warehouse_code, w.name AS warehouse_name, m.opening, m.in_qty, m.out_qty
                FROM (
                  SELECT t.product_id, t.warehouse_id,
                         SUM(IF(t.posting_date < :from, IF(t.direction = 'IN', t.quantity, -t.quantity), 0)) AS opening,
                         SUM(IF(t.posting_date >= :from AND kind = 'MOVE',
                                CASE WHEN t.transaction_type <> 'REVERSAL' AND t.direction = 'IN' THEN t.quantity
                                     WHEN t.transaction_type = 'REVERSAL' AND t.direction = 'OUT' THEN -t.quantity
                                     ELSE 0 END, 0)) AS in_qty,
                         SUM(IF(t.posting_date >= :from AND kind = 'MOVE',
                                CASE WHEN t.transaction_type <> 'REVERSAL' AND t.direction = 'OUT' THEN t.quantity
                                     WHEN t.transaction_type = 'REVERSAL' AND t.direction = 'IN' THEN -t.quantity
                                     ELSE 0 END, 0)) AS out_qty
                  FROM (SELECT t.*, %s AS kind FROM inventory_transactions t
                        LEFT JOIN inventory_transactions o ON o.id = t.reversal_of_id) t
                  JOIN products pr ON pr.id = t.product_id
                  WHERE %s
                  GROUP BY t.product_id, t.warehouse_id
                ) m
                JOIN products pr ON pr.id = m.product_id
                JOIN units u ON u.id = pr.base_unit_id
                JOIN warehouses w ON w.id = m.warehouse_id
                WHERE m.opening <> 0 OR m.in_qty <> 0 OR m.out_qty <> 0
                ORDER BY pr.code, w.code
                """.formatted(MOVEMENT_TYPE, where);
        return jdbc.query(sql, params, (rs, i) -> new MovementRow(rs.getLong("product_id"),
                rs.getString("product_code"), rs.getString("product_name"), rs.getString("unit_name"),
                rs.getLong("warehouse_id"), rs.getString("warehouse_code"), rs.getString("warehouse_name"),
                rs.getBigDecimal("opening"), rs.getBigDecimal("in_qty"), rs.getBigDecimal("out_qty")));
    }

    private static MapSqlParameterSource cardParams(Scope scope, Long productId, Long lotId, Long serialId) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", scope.companyId())
                .addValue("productId", productId).addValue("lotId", lotId).addValue("serialId", serialId);
        if (scope.warehouseIds() != null) {
            params.addValue("warehouseIds", scope.warehouseIds());
        }
        return params;
    }

    private static String cardWhere(Scope scope, Long lotId, Long serialId) {
        StringBuilder where = new StringBuilder("t.company_id = :companyId AND t.product_id = :productId");
        if (scope.warehouseIds() != null) {
            where.append(" AND t.warehouse_id IN (:warehouseIds)");
        }
        if (lotId != null) {
            where.append(" AND t.lot_id = :lotId");
        }
        if (serialId != null) {
            where.append(" AND t.serial_id = :serialId");
        }
        return where.toString();
    }
}
