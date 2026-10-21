package com.company.erp.inventory.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Dòng nhập kho theo lô / serial làm chứng từ nguồn khi xuất đích danh (D-63): dòng sổ nhật ký kho chiều nhập, không
 * phải dòng đảo, chưa bị đảo. Tồn còn lại tính theo (hàng, kho, lô, serial) ở trạng thái ĐẠT; lô dùng chung cho mọi
 * phiếu nhập cùng lô.
 */
@Repository
public class ReceiptSourceJdbc {

    /** Dòng nhập còn tồn. Số lượng theo đơn vị cơ sở. */
    public record ReceiptRow(Long transactionId, String referenceType, Long referenceId, String referenceNo,
                             LocalDate postingDate, String lotNo, LocalDate expiryDate, String serialNo,
                             BigDecimal receivedQuantity, BigDecimal unitCost, BigDecimal availableQuantity) {
    }

    /** Thông tin dòng nhập để kiểm tra liên kết; {@code usable}: chiều nhập, không phải dòng đảo, chưa bị đảo. */
    public record ReceiptKey(Long transactionId, Long companyId, Long warehouseId, Long productId, String referenceNo,
                             String lotNo, String serialNo, boolean usable) {
    }

    private static final String AVAILABLE = """
            (SELECT COALESCE(SUM(b.on_hand_quantity), 0) FROM inventory_balances b
             WHERE b.product_id = t.product_id AND b.warehouse_id = t.warehouse_id AND b.lot_id <=> t.lot_id
               AND b.serial_id <=> t.serial_id AND b.quality_status = 'PASSED')
            """;

    private final JdbcTemplate jdbc;

    public ReceiptSourceJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Dòng nhập còn tồn bán được (lô / serial không khóa, lô chưa hết hạn tại {@code today}), FEFO rồi ngày nhập. */
    public List<ReceiptRow> available(Long companyId, Long warehouseId, Long productId, LocalDate today, int limit) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT t.id, t.reference_type, t.reference_id, t.reference_no, t.posting_date, l.lot_no,
                           l.expiry_date, s.serial_no, t.quantity, t.unit_cost,
                    """ + AVAILABLE + """
                           AS available_quantity
                    FROM inventory_transactions t
                    LEFT JOIN inventory_lots l ON l.id = t.lot_id
                    LEFT JOIN inventory_serials s ON s.id = t.serial_id
                    WHERE t.company_id = ? AND t.warehouse_id = ? AND t.product_id = ? AND t.direction = 'IN'
                      AND t.reversal_of_id IS NULL AND (t.lot_id IS NOT NULL OR t.serial_id IS NOT NULL)
                      AND NOT EXISTS (SELECT 1 FROM inventory_transactions r WHERE r.reversal_of_id = t.id)
                      AND COALESCE(l.blocked, FALSE) = FALSE AND COALESCE(s.blocked, FALSE) = FALSE
                      AND (l.expiry_date IS NULL OR l.expiry_date >= ?)
                ) rows_left
                WHERE available_quantity > 0
                ORDER BY expiry_date IS NULL, expiry_date, posting_date, id
                LIMIT ?
                """, ReceiptSourceJdbc::row, companyId, warehouseId, productId, Date.valueOf(today), limit);
    }

    public Optional<ReceiptKey> find(Long transactionId) {
        return jdbc.query("""
                SELECT t.id, t.company_id, t.warehouse_id, t.product_id, t.reference_no, l.lot_no, s.serial_no,
                       t.direction = 'IN' AND t.reversal_of_id IS NULL
                           AND NOT EXISTS (SELECT 1 FROM inventory_transactions r WHERE r.reversal_of_id = t.id)
                           AS usable
                FROM inventory_transactions t
                LEFT JOIN inventory_lots l ON l.id = t.lot_id
                LEFT JOIN inventory_serials s ON s.id = t.serial_id
                WHERE t.id = ?
                """, (rs, i) -> new ReceiptKey(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8)), transactionId)
                .stream().findFirst();
    }

    private static ReceiptRow row(ResultSet rs, int index) throws SQLException {
        Date expiry = rs.getDate("expiry_date");
        return new ReceiptRow(rs.getLong("id"), rs.getString("reference_type"), rs.getLong("reference_id"),
                rs.getString("reference_no"), rs.getDate("posting_date").toLocalDate(), rs.getString("lot_no"),
                expiry == null ? null : expiry.toLocalDate(), rs.getString("serial_no"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_cost"), rs.getBigDecimal("available_quantity"));
    }
}
