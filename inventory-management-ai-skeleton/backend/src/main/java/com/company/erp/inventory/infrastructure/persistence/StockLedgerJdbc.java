package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.StockKey;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Câu lệnh khóa và cập nhật số dư kho, viết tường minh để kiểm soát thứ tự khóa và tránh đọc dữ liệu cũ của
 * persistence context. Chỉ {@code InventoryPostingService} dùng (business-integrity.md mục 4, 5). Chạy trong cùng
 * transaction JPA (READ COMMITTED khi ghi sổ).
 */
@Repository
public class StockLedgerJdbc {

    /** Một dòng tồn chi tiết đã khóa, kèm thông tin lô / serial để kiểm tra bán được và FEFO. */
    public record LockedBalance(Long id, StockKey key, BigDecimal onHand, LocalDate expiryDate, boolean lotBlocked,
                                boolean serialBlocked) {
    }

    /** Tổng (hàng, kho) đã khóa. */
    public record LockedSummary(Long productId, Long warehouseId, BigDecimal onHand, BigDecimal reserved) {
    }

    private static final String BALANCE_COLUMNS = """
            b.id, b.product_id, b.warehouse_id, b.location_id, b.lot_id, b.serial_id, b.quality_status,
            b.on_hand_quantity, l.expiry_date, COALESCE(l.blocked, FALSE) AS lot_blocked,
            COALESCE(s.blocked, FALSE) AS serial_blocked
            """;

    private final JdbcTemplate jdbc;

    public StockLedgerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Tạo dòng trạng thái kho nếu chưa có (một dòng, không gây khóa chéo). */
    public void ensureWarehouseState(Long warehouseId, Long companyId, Instant now) {
        jdbc.update("""
                INSERT IGNORE INTO inventory_warehouse_states (warehouse_id, company_id, version, updated_at)
                VALUES (?, ?, 0, ?)
                """, warehouseId, companyId, Timestamp.from(now));
    }

    /** Phiếu kiểm kê đang chặn kho, đọc khóa chia sẻ để thao tác bắt đầu kiểm kê phải chờ (D-11). */
    public Optional<Long> countingStockCountForShare(Long warehouseId) {
        List<Long> ids = jdbc.query("""
                SELECT counting_stock_count_id FROM inventory_warehouse_states WHERE warehouse_id = ? FOR SHARE
                """, (rs, row) -> (Long) rs.getObject(1, Long.class), warehouseId);
        return ids.isEmpty() ? Optional.empty() : Optional.ofNullable(ids.get(0));
    }

    public Optional<Long> countingStockCountForUpdate(Long warehouseId) {
        List<Long> ids = jdbc.query("""
                SELECT counting_stock_count_id FROM inventory_warehouse_states WHERE warehouse_id = ? FOR UPDATE
                """, (rs, row) -> (Long) rs.getObject(1, Long.class), warehouseId);
        return ids.isEmpty() ? Optional.empty() : Optional.ofNullable(ids.get(0));
    }

    public void setCountingStockCount(Long warehouseId, Long stockCountId, Instant now) {
        jdbc.update("""
                UPDATE inventory_warehouse_states
                SET counting_stock_count_id = ?, version = version + 1, updated_at = ?
                WHERE warehouse_id = ?
                """, stockCountId, Timestamp.from(now), warehouseId);
    }

    /**
     * Khóa ghi dòng tổng (hàng, kho), tạo nếu chưa có; gọi theo thứ tự (hàng, kho) tăng dần. Không dùng INSERT IGNORE
     * trên dòng đã có: câu lệnh đó lấy khóa chia sẻ rồi phải nâng lên khóa ghi, hai giao dịch cùng nâng sẽ deadlock.
     */
    public LockedSummary lockSummary(Long companyId, Long productId, Long warehouseId, Instant now) {
        Optional<LockedSummary> existing = selectSummaryForUpdate(productId, warehouseId);
        if (existing.isPresent()) {
            return existing.get();
        }
        jdbc.update("""
                INSERT INTO inventory_stock_summaries
                    (company_id, product_id, warehouse_id, on_hand_quantity, reserved_quantity, incoming_quantity,
                     version, updated_at)
                VALUES (?, ?, ?, 0, 0, 0, 0, ?)
                ON DUPLICATE KEY UPDATE version = version
                """, companyId, productId, warehouseId, Timestamp.from(now));
        return selectSummaryForUpdate(productId, warehouseId)
                .orElseThrow(() -> new IllegalStateException("Không tạo được tổng tồn (" + productId + ", "
                        + warehouseId + ")"));
    }

    private Optional<LockedSummary> selectSummaryForUpdate(Long productId, Long warehouseId) {
        return jdbc.query("""
                SELECT product_id, warehouse_id, on_hand_quantity, reserved_quantity
                FROM inventory_stock_summaries WHERE product_id = ? AND warehouse_id = ? FOR UPDATE
                """, (rs, row) -> new LockedSummary(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4)), productId, warehouseId).stream().findFirst();
    }

    public void addSummary(Long productId, Long warehouseId, BigDecimal onHandDelta, BigDecimal reservedDelta,
                           Instant now) {
        int updated = jdbc.update("""
                UPDATE inventory_stock_summaries
                SET on_hand_quantity = on_hand_quantity + ?, reserved_quantity = reserved_quantity + ?,
                    version = version + 1, updated_at = ?
                WHERE product_id = ? AND warehouse_id = ?
                  AND on_hand_quantity + ? >= 0 AND reserved_quantity + ? >= 0
                """, onHandDelta, reservedDelta, Timestamp.from(now), productId, warehouseId, onHandDelta,
                reservedDelta);
        if (updated != 1) {
            throw new IllegalStateException("Tổng tồn (hàng " + productId + ", kho " + warehouseId + ") sẽ âm");
        }
    }

    /**
     * Khóa ghi các dòng tồn khớp điều kiện, theo thứ tự FEFO: hạn dùng sớm nhất trước, lô / vị trí / id tăng dần.
     * {@code null} ở tham số lọc nghĩa là không lọc theo cột đó.
     */
    public List<LockedBalance> lockCandidates(Long productId, Long warehouseId, Long locationId, Long lotId,
                                              Long serialId, Collection<QualityStatus> statuses) {
        return candidates(productId, warehouseId, locationId, lotId, serialId, statuses, true);
    }

    /** Như {@link #lockCandidates} nhưng không khóa; dùng cho đề xuất FEFO khi lập chứng từ. */
    public List<LockedBalance> readCandidates(Long productId, Long warehouseId, Collection<QualityStatus> statuses) {
        return candidates(productId, warehouseId, null, null, null, statuses, false);
    }

    private List<LockedBalance> candidates(Long productId, Long warehouseId, Long locationId, Long lotId,
                                           Long serialId, Collection<QualityStatus> statuses, boolean lock) {
        List<Object> args = new ArrayList<>(List.of(productId, warehouseId));
        StringBuilder sql = new StringBuilder("SELECT ").append(BALANCE_COLUMNS).append("""
                FROM inventory_balances b
                LEFT JOIN inventory_lots l ON l.id = b.lot_id
                LEFT JOIN inventory_serials s ON s.id = b.serial_id
                WHERE b.product_id = ? AND b.warehouse_id = ? AND b.on_hand_quantity > 0
                """);
        if (locationId != null) {
            sql.append(" AND b.location_id = ?");
            args.add(locationId);
        }
        if (lotId != null) {
            sql.append(" AND b.lot_id = ?");
            args.add(lotId);
        }
        if (serialId != null) {
            sql.append(" AND b.serial_id = ?");
            args.add(serialId);
        }
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND b.quality_status IN (")
                    .append(statuses.stream().map(status -> "?").collect(Collectors.joining(",")))
                    .append(")");
            statuses.forEach(status -> args.add(status.name()));
        }
        sql.append(" ORDER BY l.expiry_date IS NULL, l.expiry_date, b.lot_key, b.location_id, b.serial_key, b.id");
        if (lock) {
            sql.append(" FOR UPDATE OF b");
        }
        return jdbc.query(sql.toString(), this::mapBalance, args.toArray());
    }

    /** Tồn bán được của (hàng, kho): trạng thái ĐẠT, lô chưa hết hạn tại ngày ghi sổ, lô / serial không bị khóa. */
    public BigDecimal sellableQuantity(Long productId, Long warehouseId, LocalDate onDate) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(b.on_hand_quantity), 0)
                FROM inventory_balances b
                LEFT JOIN inventory_lots l ON l.id = b.lot_id
                LEFT JOIN inventory_serials s ON s.id = b.serial_id
                WHERE b.product_id = ? AND b.warehouse_id = ? AND b.quality_status = 'PASSED'
                  AND (l.id IS NULL OR ((l.expiry_date IS NULL OR l.expiry_date >= ?) AND l.blocked = FALSE))
                  AND (s.id IS NULL OR s.blocked = FALSE)
                """, BigDecimal.class, productId, warehouseId, Date.valueOf(onDate));
    }

    /** Giảm tồn có điều kiện không âm; trả {@code false} nếu không đủ. */
    public boolean decrement(Long balanceId, BigDecimal quantity, Instant now) {
        return jdbc.update("""
                UPDATE inventory_balances
                SET on_hand_quantity = on_hand_quantity - ?, version = version + 1, updated_at = ?
                WHERE id = ? AND on_hand_quantity >= ?
                """, quantity, Timestamp.from(now), balanceId, quantity) == 1;
    }

    /** Tăng tồn; dòng chưa có thì tạo (upsert theo khóa duy nhất). */
    public void increment(Long companyId, StockKey key, BigDecimal quantity, Instant now) {
        jdbc.update("""
                INSERT INTO inventory_balances
                    (company_id, product_id, warehouse_id, location_id, lot_id, serial_id, quality_status,
                     on_hand_quantity, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?) AS incoming
                ON DUPLICATE KEY UPDATE
                    on_hand_quantity = inventory_balances.on_hand_quantity + incoming.on_hand_quantity,
                    version = inventory_balances.version + 1,
                    updated_at = incoming.updated_at
                """, companyId, key.productId(), key.warehouseId(), key.locationId(), key.lotId(), key.serialId(),
                key.qualityStatus().name(), quantity, Timestamp.from(now));
    }

    /** Tổng tồn của một serial trên toàn bộ kho (BR-INV-02: một serial không tồn ở hai nơi). */
    public BigDecimal serialOnHand(Long serialId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_balances WHERE serial_id = ?",
                BigDecimal.class, serialId);
    }

    /** Khóa ghi các serial theo id tăng dần trước khi đổi tồn của serial. */
    public void lockSerials(Set<Long> serialIds) {
        if (serialIds.isEmpty()) {
            return;
        }
        String placeholders = serialIds.stream().map(id -> "?").collect(Collectors.joining(","));
        jdbc.query("SELECT id FROM inventory_serials WHERE id IN (" + placeholders + ") ORDER BY id FOR UPDATE",
                (rs, row) -> rs.getLong(1), serialIds.stream().sorted().toArray());
    }

    /** Tổng tồn theo khóa chi tiết, cho kiểm tra tính nhất quán balance = sổ nhật ký. */
    public BigDecimal onHand(StockKey key) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_balances
                WHERE product_id = ? AND warehouse_id = ? AND location_id = ? AND lot_key = ? AND serial_key = ?
                  AND quality_status = ?
                """, BigDecimal.class, key.productId(), key.warehouseId(), key.locationId(),
                key.lotId() == null ? 0L : key.lotId(), key.serialId() == null ? 0L : key.serialId(),
                key.qualityStatus().name());
    }

    private LockedBalance mapBalance(ResultSet rs, int row) throws SQLException {
        StockKey key = new StockKey(rs.getLong("product_id"), rs.getLong("warehouse_id"), rs.getLong("location_id"),
                rs.getObject("lot_id", Long.class), rs.getObject("serial_id", Long.class),
                QualityStatus.valueOf(rs.getString("quality_status")));
        Date expiry = rs.getDate("expiry_date");
        return new LockedBalance(rs.getLong("id"), key, rs.getBigDecimal("on_hand_quantity"),
                expiry == null ? null : expiry.toLocalDate(), rs.getBoolean("lot_blocked"),
                rs.getBoolean("serial_blocked"));
    }
}
