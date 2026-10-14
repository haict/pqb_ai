package com.company.erp.purchasing.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Dòng phát sinh của sổ chi tiết mua hàng từ chứng từ đã ghi sổ theo ngày hạch toán: dòng đơn mua (giá trị mua, chiết
 * khấu), dòng trả hàng (THN) / giảm giá (GGM). Chứng từ đã đảo: dòng gốc theo ngày hạch toán và dòng đảo theo ngày đảo
 * (ứng dụng đổi dấu), nên số liệu kỳ khớp sổ cái.
 */
@Repository
public class PurchaseLedgerJdbc {

    /** Điều kiện lọc; {@code creatorId}: "NV mua hàng" là người lập chứng từ. */
    public record Filter(Long companyId, Long branchId, LocalDate from, LocalDate to, Long creatorId,
                         Collection<Long> productIds, Collection<Long> supplierIds) {
    }

    /**
     * {@code reversal}: dòng đảo (ngày = ngày đảo); số lượng, số tiền vẫn mang dấu của chứng từ gốc. Đơn mua:
     * {@code amount} = số lượng × đơn giá, {@code discount} = chiết khấu dòng; trả hàng / giảm giá: {@code amount} =
     * giá trị trả / giảm, {@code discount} = 0.
     */
    public record Row(String documentType, boolean reversal, Long documentId, Long lineId, String documentNo,
                      LocalDate documentDate, LocalDate entryDate, LocalDate invoiceDate, String invoiceNo,
                      Long supplierId, Long productId, Long unitId, BigDecimal quantity, BigDecimal unitPrice,
                      BigDecimal amount, BigDecimal discount) {
    }

    private static final String ORDER_LINES = """
            SELECT 'DMH' AS document_type, %s AS reversal, h.id AS document_id, l.id AS line_id, h.document_no,
                   h.document_date, %s AS entry_date, h.invoice_date, h.invoice_no, h.supplier_id, l.product_id,
                   l.unit_id, l.quantity, l.unit_price, l.amount, l.discount_amount, l.line_no
            FROM purchase_orders h JOIN purchase_order_lines l ON l.order_id = h.id
            """;

    private static final String RETURN_LINES = """
            SELECT h.document_type, %s, h.id, l.id, h.document_no, h.document_date, %s, NULL, NULL, h.supplier_id,
                   l.product_id, l.unit_id, l.quantity, l.unit_price, l.amount, 0, l.line_no
            FROM purchase_returns h JOIN purchase_return_lines l ON l.return_id = h.id
            """;

    private static final String POSTED = """
            WHERE h.company_id = ? AND h.status IN ('POSTED', 'REVERSED') AND h.posting_date BETWEEN ? AND ?
            """;

    private static final String REVERSED = """
            WHERE h.company_id = ? AND h.status = 'REVERSED' AND h.reverse_date BETWEEN ? AND ?
            """;

    private final JdbcTemplate jdbc;

    public PurchaseLedgerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> lines(Filter filter) {
        List<Object> params = new ArrayList<>();
        String sql = String.join(" UNION ALL ",
                branch(ORDER_LINES.formatted("FALSE", "h.posting_date") + POSTED, filter, params),
                branch(ORDER_LINES.formatted("TRUE", "h.reverse_date") + REVERSED, filter, params),
                branch(RETURN_LINES.formatted("FALSE", "h.posting_date") + POSTED, filter, params),
                branch(RETURN_LINES.formatted("TRUE", "h.reverse_date") + REVERSED, filter, params))
                + " ORDER BY entry_date, document_no, reversal, line_no";
        return jdbc.query(sql, PurchaseLedgerJdbc::toRow, params.toArray());
    }

    private static String branch(String select, Filter filter, List<Object> params) {
        StringBuilder sql = new StringBuilder(select);
        params.add(filter.companyId());
        params.add(Date.valueOf(filter.from()));
        params.add(Date.valueOf(filter.to()));
        if (filter.branchId() != null) {
            sql.append(" AND h.branch_id = ?");
            params.add(filter.branchId());
        }
        if (filter.creatorId() != null) {
            sql.append(" AND h.created_by = ?");
            params.add(filter.creatorId());
        }
        appendIn(sql, params, "l.product_id", filter.productIds());
        appendIn(sql, params, "h.supplier_id", filter.supplierIds());
        return sql.toString();
    }

    private static void appendIn(StringBuilder sql, List<Object> params, String column, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (")
                .append(ids.stream().map(id -> "?").collect(Collectors.joining(", "))).append(')');
        params.addAll(ids);
    }

    private static Row toRow(ResultSet rs, int rowNum) throws SQLException {
        Date invoiceDate = rs.getDate("invoice_date");
        return new Row(rs.getString("document_type"), rs.getBoolean("reversal"), rs.getLong("document_id"),
                rs.getLong("line_id"), rs.getString("document_no"), rs.getDate("document_date").toLocalDate(),
                rs.getDate("entry_date").toLocalDate(), invoiceDate == null ? null : invoiceDate.toLocalDate(),
                rs.getString("invoice_no"), rs.getLong("supplier_id"), rs.getLong("product_id"),
                rs.getLong("unit_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("discount_amount"));
    }
}
