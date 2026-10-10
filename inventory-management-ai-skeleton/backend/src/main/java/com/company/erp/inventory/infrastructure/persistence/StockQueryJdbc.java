package com.company.erp.inventory.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy vấn đọc tổng hợp tồn (không khóa) cho màn hình tồn kho và cảnh báo; tránh truy vấn từng dòng (N+1). */
@Repository
public class StockQueryJdbc {

    /** Lô còn tồn có hạn dùng đến ngày giới hạn. */
    public record ExpiringStock(Long productId, Long warehouseId, Long lotId, String lotNo, LocalDate expiryDate,
                                BigDecimal onHand) {
    }

    private final JdbcTemplate jdbc;

    public StockQueryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Tồn bán được (ĐẠT, chưa hết hạn tại ngày, lô / serial không khóa) theo (hàng, kho). */
    public Map<String, BigDecimal> sellable(Collection<Long> productIds, Collection<Long> warehouseIds,
                                            LocalDate onDate) {
        if (productIds.isEmpty() || warehouseIds.isEmpty()) {
            return Map.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(onDate));
        args.addAll(productIds);
        args.addAll(warehouseIds);
        String sql = """
                SELECT b.product_id, b.warehouse_id, COALESCE(SUM(b.on_hand_quantity), 0) AS quantity
                FROM inventory_balances b
                LEFT JOIN inventory_lots l ON l.id = b.lot_id
                LEFT JOIN inventory_serials s ON s.id = b.serial_id
                WHERE b.quality_status = 'PASSED' AND b.on_hand_quantity > 0
                  AND (l.id IS NULL OR ((l.expiry_date IS NULL OR l.expiry_date >= ?) AND l.blocked = FALSE))
                  AND (s.id IS NULL OR s.blocked = FALSE)
                  AND b.product_id IN (%s) AND b.warehouse_id IN (%s)
                GROUP BY b.product_id, b.warehouse_id
                """.formatted(placeholders(productIds), placeholders(warehouseIds));
        Map<String, BigDecimal> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(pairKey(rs.getLong(1), rs.getLong(2)), rs.getBigDecimal(3));
        }, args.toArray());
        return result;
    }

    /** Tổng tồn theo lô (mọi kho trong danh sách; {@code null} là mọi kho). */
    public Map<Long, BigDecimal> onHandByLot(Collection<Long> lotIds, Collection<Long> warehouseIds) {
        if (lotIds.isEmpty()) {
            return Map.of();
        }
        List<Object> args = new ArrayList<>(lotIds);
        String warehouseFilter = "";
        if (warehouseIds != null) {
            warehouseFilter = " AND warehouse_id IN (" + placeholders(warehouseIds) + ")";
            args.addAll(warehouseIds);
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        jdbc.query("SELECT lot_id, SUM(on_hand_quantity) FROM inventory_balances WHERE lot_id IN ("
                + placeholders(lotIds) + ")" + warehouseFilter + " GROUP BY lot_id",
                rs -> {
                    result.put(rs.getLong(1), rs.getBigDecimal(2));
                }, args.toArray());
        return result;
    }

    /** Lô còn tồn có hạn dùng không sau {@code until}, sắp theo hạn dùng (BR-INV-40). */
    public List<ExpiringStock> expiring(Long companyId, Collection<Long> warehouseIds, LocalDate until) {
        List<Object> args = new ArrayList<>(List.of(companyId, Date.valueOf(until)));
        String warehouseFilter = "";
        if (warehouseIds != null) {
            warehouseFilter = " AND b.warehouse_id IN (" + placeholders(warehouseIds) + ")";
            args.addAll(warehouseIds);
        }
        return jdbc.query("""
                SELECT b.product_id, b.warehouse_id, l.id, l.lot_no, l.expiry_date, SUM(b.on_hand_quantity)
                FROM inventory_balances b
                JOIN inventory_lots l ON l.id = b.lot_id
                WHERE b.company_id = ? AND b.on_hand_quantity > 0 AND l.expiry_date IS NOT NULL
                  AND l.expiry_date <= ?""" + warehouseFilter + """
                 GROUP BY b.product_id, b.warehouse_id, l.id, l.lot_no, l.expiry_date
                 ORDER BY l.expiry_date, b.product_id, b.warehouse_id
                """, (rs, row) -> new ExpiringStock(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getBigDecimal(6)), args.toArray());
    }

    public static String pairKey(Long productId, Long warehouseId) {
        return productId + ":" + warehouseId;
    }

    private static String placeholders(Collection<?> values) {
        return values.stream().map(value -> "?").collect(Collectors.joining(","));
    }
}
