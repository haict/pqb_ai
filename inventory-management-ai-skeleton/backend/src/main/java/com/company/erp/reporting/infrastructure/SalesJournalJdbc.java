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
 * Sổ nhật ký bán hàng (BR-RPT-26) từ chứng từ bán đã ghi sổ theo ngày hạch toán (chủ dự án chọn nguồn chứng từ thay vì
 * bút toán, 2026-10-14): đơn bán tách doanh thu hàng hóa / dịch vụ theo loại dòng; trả lại / giảm giá vào cột giảm trừ.
 * Chứng từ đã đảo: dòng gốc theo ngày hạch toán và dòng đảo theo ngày đảo (ADR-18: SQL chỉ đọc).
 */
@Repository
public class SalesJournalJdbc {

    /** Một chứng từ; số tiền mang dấu của chứng từ gốc, ứng dụng đổi dấu dòng đảo. */
    public record Row(String documentType, boolean reversal, Long documentId, String documentNo,
                      LocalDate documentDate, LocalDate entryDate, LocalDate invoiceDate, String invoiceNo,
                      String customerCode, String customerName, String description, BigDecimal goodsAmount,
                      BigDecimal serviceAmount, BigDecimal deductionAmount, BigDecimal taxAmount,
                      BigDecimal totalAmount) {
    }

    private static final String ORDERS = """
            SELECT 'DBH' AS document_type, %s AS reversal, h.id, h.document_no, h.document_date, %s AS entry_date,
                   h.einvoice_date, h.einvoice_no, c.code AS customer_code, c.name AS customer_name, h.description,
                   SUM(IF(l.service_line, 0, l.amount)) AS goods, SUM(IF(l.service_line, l.amount, 0)) AS service,
                   0 AS deduction, h.tax_amount, h.total_amount
            FROM sales_orders h JOIN sales_order_lines l ON l.order_id = h.id JOIN partners c ON c.id = h.customer_id
            %s
            GROUP BY h.id, h.document_no, h.document_date, h.einvoice_date, h.einvoice_no, c.code, c.name,
                     h.description, h.tax_amount, h.total_amount, h.posting_date, h.reverse_date
            """;

    private static final String RETURNS = """
            SELECT h.document_type, %s, h.id, h.document_no, h.document_date, %s, NULL, NULL, c.code, c.name,
                   h.description, 0, 0, h.amount, -h.tax_amount, -h.total_amount
            FROM sales_returns h JOIN partners c ON c.id = h.customer_id
            %s
            """;

    private static final String POSTED =
            "WHERE h.company_id = :companyId AND h.status IN ('POSTED', 'REVERSED') AND h.posting_date BETWEEN :from AND :to";
    private static final String REVERSED =
            "WHERE h.company_id = :companyId AND h.status = 'REVERSED' AND h.reverse_date BETWEEN :from AND :to";

    private final NamedParameterJdbcTemplate jdbc;

    public SalesJournalJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> journal(Long companyId, Collection<Long> branchIds, Long ownerId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId)
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to)).addValue("ownerId", ownerId);
        StringBuilder extra = new StringBuilder();
        if (branchIds != null) {
            extra.append(" AND h.branch_id IN (:branchIds)");
            params.addValue("branchIds", branchIds);
        }
        if (ownerId != null) {
            extra.append(" AND (h.sales_user_id = :ownerId OR h.created_by = :ownerId)");
        }
        String sql = String.join(" UNION ALL ",
                ORDERS.formatted("FALSE", "h.posting_date", POSTED + extra),
                ORDERS.formatted("TRUE", "h.reverse_date", REVERSED + extra),
                RETURNS.formatted("FALSE", "h.posting_date", POSTED + extra),
                RETURNS.formatted("TRUE", "h.reverse_date", REVERSED + extra))
                + " ORDER BY entry_date, document_no, reversal";
        return jdbc.query(sql, params, SalesJournalJdbc::toRow);
    }

    private static Row toRow(ResultSet rs, int rowNum) throws SQLException {
        Date invoiceDate = rs.getDate("einvoice_date");
        return new Row(rs.getString("document_type"), rs.getBoolean("reversal"), rs.getLong("id"),
                rs.getString("document_no"), rs.getDate("document_date").toLocalDate(),
                rs.getDate("entry_date").toLocalDate(), invoiceDate == null ? null : invoiceDate.toLocalDate(),
                rs.getString("einvoice_no"), rs.getString("customer_code"), rs.getString("customer_name"),
                rs.getString("description"), rs.getBigDecimal("goods"), rs.getBigDecimal("service"),
                rs.getBigDecimal("deduction"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("total_amount"));
    }
}
