package com.company.erp.reporting.infrastructure;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lịch sử giá mua và hàng lỗi theo nhà cung cấp (BR-RPT-25) từ đơn mua đã ghi sổ, phiếu hàng lỗi khách báo và sổ kho
 * (ADR-18: SQL chỉ đọc).
 */
@Repository
public class PurchaseAnalysisJdbc {

    /** Một lần mua: giá theo đơn vị giao dịch và giá sau chiết khấu quy về đơn vị cơ sở. */
    public record PriceRow(Long productId, String productCode, String productName, String baseUnitName,
                           LocalDate postingDate, Long documentId, String documentNo, Long supplierId,
                           String supplierCode, String supplierName, String unitName, BigDecimal quantity,
                           BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal netAmount,
                           BigDecimal baseQuantity) {
    }

    /** Phiếu hàng lỗi kèm NCC của lô (null: không xác định). */
    public record DefectRow(Long complaintId, String complaintNo, LocalDate receivedDate, String customerCode,
                            String customerName, Long productId, String productCode, String productName,
                            String lotNo, String serialNo, BigDecimal quantity, String status, String decision,
                            Long supplierId, String supplierCode, String supplierName) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseAnalysisJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Dòng hàng hóa của đơn mua đang ghi sổ (không tính đơn đã đảo) trong khoảng ngày hạch toán. */
    public List<PriceRow> prices(Long companyId, Collection<Long> branchIds, Collection<Long> productIds,
                                 Long supplierId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId)
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to))
                .addValue("supplierId", supplierId);
        StringBuilder where = new StringBuilder("""
                h.company_id = :companyId AND h.status = 'POSTED' AND h.posting_date BETWEEN :from AND :to
                AND l.service_line = FALSE""");
        if (branchIds != null) {
            where.append(" AND h.branch_id IN (:branchIds)");
            params.addValue("branchIds", branchIds);
        }
        if (productIds != null && !productIds.isEmpty()) {
            where.append(" AND l.product_id IN (:productIds)");
            params.addValue("productIds", productIds);
        }
        if (supplierId != null) {
            where.append(" AND h.supplier_id = :supplierId");
        }
        return jdbc.query("""
                SELECT l.product_id, pr.code AS product_code, pr.name AS product_name, bu.name AS base_unit,
                       h.posting_date, h.id AS document_id, h.document_no, h.supplier_id, s.code AS supplier_code,
                       s.name AS supplier_name, u.name AS unit_name, l.quantity, l.unit_price, l.discount_percent,
                       l.amount - l.discount_amount AS net_amount, l.base_quantity
                FROM purchase_orders h
                JOIN purchase_order_lines l ON l.order_id = h.id
                JOIN products pr ON pr.id = l.product_id
                JOIN units bu ON bu.id = pr.base_unit_id
                JOIN units u ON u.id = l.unit_id
                JOIN partners s ON s.id = h.supplier_id
                WHERE %s
                ORDER BY pr.code, h.posting_date, h.document_no, l.line_no
                """.formatted(where), params, (rs, i) -> new PriceRow(rs.getLong("product_id"),
                rs.getString("product_code"), rs.getString("product_name"), rs.getString("base_unit"),
                rs.getDate("posting_date").toLocalDate(), rs.getLong("document_id"), rs.getString("document_no"),
                rs.getLong("supplier_id"), rs.getString("supplier_code"), rs.getString("supplier_name"),
                rs.getString("unit_name"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("discount_percent"), rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("base_quantity")));
    }

    /**
     * Phiếu hàng lỗi (trừ đã hủy) theo ngày nhận; NCC là đối tượng của lần nhập mua đầu tiên của lô (phiếu nhập kho
     * nguồn đơn mua DMH, không tính dòng đảo).
     */
    public List<DefectRow> defects(Long companyId, Collection<Long> branchIds, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId)
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        String branchFilter = "";
        if (branchIds != null) {
            branchFilter = " AND c.branch_id IN (:branchIds)";
            params.addValue("branchIds", branchIds);
        }
        return jdbc.query("""
                SELECT c.id, c.complaint_no, c.received_date, cu.code AS customer_code, cu.name AS customer_name,
                       c.product_id, pr.code AS product_code, pr.name AS product_name, c.lot_no, c.serial_no,
                       c.quantity, c.status, c.decision, s.id AS supplier_id, s.code AS supplier_code,
                       s.name AS supplier_name
                FROM sales_complaints c
                JOIN partners cu ON cu.id = c.customer_id
                JOIN products pr ON pr.id = c.product_id
                LEFT JOIN inventory_lots l ON l.company_id = c.company_id AND l.product_id = c.product_id
                     AND l.lot_no = c.lot_no
                LEFT JOIN (SELECT t.lot_id, MIN(t.id) AS first_id
                           FROM inventory_transactions t
                           JOIN inventory_documents d ON d.id = t.reference_id AND t.reference_type = 'PNK'
                           WHERE t.company_id = :companyId AND t.reversal_of_id IS NULL AND d.source_type = 'DMH'
                           GROUP BY t.lot_id) fr ON fr.lot_id = l.id
                LEFT JOIN inventory_transactions ft ON ft.id = fr.first_id
                LEFT JOIN partners s ON s.id = ft.partner_id
                WHERE c.company_id = :companyId AND c.status <> 'CANCELLED'
                  AND c.received_date BETWEEN :from AND :to %s
                ORDER BY s.code IS NULL, s.code, c.received_date, c.complaint_no
                """.formatted(branchFilter), params, (rs, i) -> new DefectRow(rs.getLong("id"),
                rs.getString("complaint_no"), rs.getDate("received_date").toLocalDate(),
                rs.getString("customer_code"), rs.getString("customer_name"), rs.getLong("product_id"),
                rs.getString("product_code"), rs.getString("product_name"), rs.getString("lot_no"),
                rs.getString("serial_no"), rs.getBigDecimal("quantity"), rs.getString("status"),
                rs.getString("decision"), rs.getObject("supplier_id", Long.class), rs.getString("supplier_code"),
                rs.getString("supplier_name")));
    }
}
