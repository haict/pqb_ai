package com.company.erp.reporting.infrastructure;

import com.company.erp.reporting.infrastructure.StockLedgerReportJdbc.Scope;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy xuất lô / serial (BR-RPT-24): lô → nơi nhập, khách nhận; khách → lô, serial đã nhận. Đọc sổ nhật ký kho
 * (ADR-18: SQL chỉ đọc); đối tượng là {@code partner_id} ghi trên dòng sổ kho của chứng từ mua, bán, trả hàng (dòng đảo
 * không có thì lấy của dòng gốc).
 */
@Repository
public class TraceabilityReportJdbc {

    public record LotHeader(Long lotId, String lotNo, LocalDate expiryDate, Long productId, String productCode,
                            String productName, String unitName) {
    }

    /**
     * Một dòng sổ kho của lô / serial hoặc của khách; {@code quantity} mang dấu (nhập +, xuất −); {@code baseType}: loại
     * biến động, với dòng đảo là loại của dòng gốc.
     */
    public record TraceRow(Long id, LocalDate postingDate, String transactionType, String baseType,
                           String referenceType,
                           Long referenceId, String referenceNo, boolean reversal, String warehouseCode,
                           Long partnerId, String partnerCode, String partnerName, Long productId,
                           String productCode, String productName, String unitName, String lotNo,
                           LocalDate expiryDate, String serialNo, BigDecimal quantity) {
    }

    private static final String SELECT = """
            SELECT t.id, t.posting_date, t.transaction_type,
                   IF(t.transaction_type = 'REVERSAL', o.transaction_type, t.transaction_type) AS base_type,
                   t.reference_type, t.reference_id, t.reference_no,
                   t.reversal_of_id IS NOT NULL AS reversal, w.code AS warehouse_code,
                   COALESCE(t.partner_id, o.partner_id) AS partner_id,
                   pa.code AS partner_code, pa.name AS partner_name, t.product_id, p.code AS product_code,
                   p.name AS product_name, u.name AS unit_name, l.lot_no, l.expiry_date, s.serial_no,
                   IF(t.direction = 'IN', t.quantity, -t.quantity) AS quantity
            FROM inventory_transactions t
            LEFT JOIN inventory_transactions o ON o.id = t.reversal_of_id
            JOIN warehouses w ON w.id = t.warehouse_id
            JOIN products p ON p.id = t.product_id
            JOIN units u ON u.id = p.base_unit_id
            LEFT JOIN partners pa ON pa.id = COALESCE(t.partner_id, o.partner_id)
            LEFT JOIN inventory_lots l ON l.id = t.lot_id
            LEFT JOIN inventory_serials s ON s.id = t.serial_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TraceabilityReportJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LotHeader> lot(Long companyId, Long lotId) {
        return jdbc.query("""
                SELECT l.id, l.lot_no, l.expiry_date, p.id AS product_id, p.code, p.name, u.name AS unit_name
                FROM inventory_lots l JOIN products p ON p.id = l.product_id JOIN units u ON u.id = p.base_unit_id
                WHERE l.company_id = :companyId AND l.id = :lotId
                """, new MapSqlParameterSource("companyId", companyId).addValue("lotId", lotId),
                (rs, i) -> new LotHeader(rs.getLong("id"), rs.getString("lot_no"), date(rs.getDate("expiry_date")),
                        rs.getLong("product_id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("unit_name"))).stream().findFirst();
    }

    /**
     * Mọi dòng sổ kho của lô (hoặc của serial) trong phạm vi kho, trừ chuyển trạng thái chất lượng (không đổi nơi
     * hàng nằm); chuyển kho giữ lại để thấy đường đi của hàng.
     */
    public List<TraceRow> lotMovements(Scope scope, Long lotId, Long serialId) {
        MapSqlParameterSource params = params(scope).addValue("lotId", lotId).addValue("serialId", serialId);
        String filter = lotId != null ? "t.lot_id = :lotId" : "t.serial_id = :serialId";
        return jdbc.query(SELECT + """
                WHERE t.company_id = :companyId AND %s %s
                  AND t.transaction_type NOT IN ('STATUS_IN', 'STATUS_OUT')
                  AND (o.id IS NULL OR o.transaction_type NOT IN ('STATUS_IN', 'STATUS_OUT'))
                ORDER BY t.posting_date, t.posted_at, t.id
                """.formatted(filter, warehouseFilter(scope)), params, TraceabilityReportJdbc::toRow);
    }

    /** Hàng khách đã nhận (xuất bán) và trả lại, kể cả dòng đảo, trong khoảng ngày. */
    public List<TraceRow> customerMovements(Scope scope, Long customerId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = params(scope).addValue("customerId", customerId)
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        return jdbc.query(SELECT + """
                WHERE t.company_id = :companyId AND COALESCE(t.partner_id, o.partner_id) = :customerId %s
                  AND t.posting_date BETWEEN :from AND :to
                  AND IF(t.transaction_type = 'REVERSAL', o.transaction_type, t.transaction_type)
                      IN ('SALES_ISSUE', 'SALES_RETURN')
                ORDER BY t.posting_date, t.posted_at, t.id
                """.formatted(warehouseFilter(scope)), params, TraceabilityReportJdbc::toRow);
    }

    private static TraceRow toRow(ResultSet rs, int rowNum) throws SQLException {
        return new TraceRow(rs.getLong("id"), rs.getDate("posting_date").toLocalDate(),
                rs.getString("transaction_type"), rs.getString("base_type"), rs.getString("reference_type"), rs.getLong("reference_id"),
                rs.getString("reference_no"), rs.getBoolean("reversal"), rs.getString("warehouse_code"),
                rs.getObject("partner_id", Long.class), rs.getString("partner_code"), rs.getString("partner_name"),
                rs.getLong("product_id"), rs.getString("product_code"), rs.getString("product_name"),
                rs.getString("unit_name"), rs.getString("lot_no"), date(rs.getDate("expiry_date")),
                rs.getString("serial_no"), rs.getBigDecimal("quantity"));
    }

    private static MapSqlParameterSource params(Scope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", scope.companyId());
        if (scope.warehouseIds() != null) {
            params.addValue("warehouseIds", scope.warehouseIds());
        }
        return params;
    }

    private static String warehouseFilter(Scope scope) {
        return scope.warehouseIds() == null ? "" : " AND t.warehouse_id IN (:warehouseIds)";
    }

    private static LocalDate date(Date value) {
        return value == null ? null : value.toLocalDate();
    }
}
