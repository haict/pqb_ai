package com.company.erp.reporting.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tồn so với đặt hàng chưa giao (BR-RPT-26, H-06): theo hàng × kho, số lượng trên đơn bán nháp (và đã duyệt trước
 * D-61) chưa ghi sổ (chưa xuất kho, D-54) so với tồn thực tế, giữ chỗ, hàng đang về (ADR-18: SQL chỉ đọc). Đơn vị cơ sở.
 */
@Repository
public class BacklogReportJdbc {

    public record Row(Long productId, String productCode, String productName, String unitName, Long warehouseId,
                      String warehouseCode, BigDecimal ordered, long orderCount, BigDecimal onHand,
                      BigDecimal reserved, BigDecimal incoming) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public BacklogReportJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> backlog(Long companyId, Collection<Long> warehouseIds, Collection<Long> productIds,
                             Long ownerId) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId)
                .addValue("ownerId", ownerId);
        StringBuilder where = new StringBuilder("""
                h.company_id = :companyId AND h.status IN ('DRAFT', 'APPROVED') AND l.service_line = FALSE""");
        if (warehouseIds != null) {
            where.append(" AND h.warehouse_id IN (:warehouseIds)");
            params.addValue("warehouseIds", warehouseIds);
        }
        if (productIds != null && !productIds.isEmpty()) {
            where.append(" AND l.product_id IN (:productIds)");
            params.addValue("productIds", productIds);
        }
        if (ownerId != null) {
            where.append(" AND (h.sales_user_id = :ownerId OR h.created_by = :ownerId)");
        }
        return jdbc.query("""
                SELECT o.product_id, p.code AS product_code, p.name AS product_name, u.name AS unit_name,
                       o.warehouse_id, w.code AS warehouse_code, o.ordered, o.order_count,
                       COALESCE(s.on_hand_quantity, 0) AS on_hand, COALESCE(s.reserved_quantity, 0) AS reserved,
                       COALESCE(s.incoming_quantity, 0) AS incoming
                FROM (SELECT l.product_id, h.warehouse_id, SUM(l.base_quantity) AS ordered,
                             COUNT(DISTINCT h.id) AS order_count
                      FROM sales_orders h JOIN sales_order_lines l ON l.order_id = h.id
                      WHERE %s
                      GROUP BY l.product_id, h.warehouse_id) o
                JOIN products p ON p.id = o.product_id
                JOIN units u ON u.id = p.base_unit_id
                JOIN warehouses w ON w.id = o.warehouse_id
                LEFT JOIN inventory_stock_summaries s ON s.product_id = o.product_id AND s.warehouse_id = o.warehouse_id
                ORDER BY p.code, w.code
                """.formatted(where), params, (rs, i) -> new Row(rs.getLong("product_id"),
                rs.getString("product_code"), rs.getString("product_name"), rs.getString("unit_name"),
                rs.getLong("warehouse_id"), rs.getString("warehouse_code"), rs.getBigDecimal("ordered"),
                rs.getLong("order_count"), rs.getBigDecimal("on_hand"), rs.getBigDecimal("reserved"),
                rs.getBigDecimal("incoming")));
    }
}
