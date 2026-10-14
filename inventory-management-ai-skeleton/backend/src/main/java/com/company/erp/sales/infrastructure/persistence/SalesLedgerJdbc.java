package com.company.erp.sales.infrastructure.persistence;

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
 * Dòng phát sinh của sổ chi tiết bán hàng (S16-DNN) từ chứng từ đã ghi sổ theo ngày hạch toán: dòng đơn bán (doanh
 * thu), dòng trả lại / giảm giá (giảm trừ). Chứng từ đã đảo: dòng gốc theo ngày hạch toán và dòng đảo theo ngày đảo
 * (ứng dụng đổi dấu), nên số liệu kỳ khớp sổ cái.
 */
@Repository
public class SalesLedgerJdbc {

    /** Điều kiện lọc; {@code ownerId}: "chỉ dữ liệu của mình" (BR-SYS-04), chứng từ mình phụ trách hoặc mình lập. */
    public record Filter(Long companyId, Long branchId, LocalDate from, LocalDate to, Long salesUserId, Long ownerId,
                         Collection<Long> productIds, Collection<Long> customerIds) {
    }

    /** {@code reversal}: dòng đảo của chứng từ (ngày = ngày đảo); số lượng, số tiền vẫn mang dấu của chứng từ gốc. */
    public record Row(String documentType, boolean reversal, Long documentId, Long lineId, String documentNo,
                      LocalDate documentDate, LocalDate entryDate, LocalDate invoiceDate, String invoiceNo,
                      String description, Long customerId, Long productId, Long unitId, BigDecimal quantity,
                      BigDecimal amount) {
    }

    private static final String ORDER_LINES = """
            SELECT 'DBH' AS document_type, %s AS reversal, h.id AS document_id, l.id AS line_id, h.document_no,
                   h.document_date, %s AS entry_date, h.einvoice_date, h.einvoice_no, h.description, h.customer_id,
                   l.product_id, l.unit_id, l.quantity, l.amount, l.line_no
            FROM sales_orders h JOIN sales_order_lines l ON l.order_id = h.id
            """;

    private static final String RETURN_LINES = """
            SELECT h.document_type, %s, h.id, l.id, h.document_no, h.document_date, %s, NULL, NULL, h.description,
                   h.customer_id, l.product_id, l.unit_id, l.quantity, l.amount, l.line_no
            FROM sales_returns h JOIN sales_return_lines l ON l.return_id = h.id
            """;

    private static final String POSTED = """
            WHERE h.company_id = ? AND h.status IN ('POSTED', 'REVERSED') AND h.posting_date BETWEEN ? AND ?
            """;

    private static final String REVERSED = """
            WHERE h.company_id = ? AND h.status = 'REVERSED' AND h.reverse_date BETWEEN ? AND ?
            """;

    private final JdbcTemplate jdbc;

    public SalesLedgerJdbc(JdbcTemplate jdbc) {
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
        return jdbc.query(sql, SalesLedgerJdbc::toRow, params.toArray());
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
        if (filter.salesUserId() != null) {
            sql.append(" AND h.sales_user_id = ?");
            params.add(filter.salesUserId());
        }
        if (filter.ownerId() != null) {
            sql.append(" AND (h.sales_user_id = ? OR h.created_by = ?)");
            params.add(filter.ownerId());
            params.add(filter.ownerId());
        }
        appendIn(sql, params, "l.product_id", filter.productIds());
        appendIn(sql, params, "h.customer_id", filter.customerIds());
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
        Date invoiceDate = rs.getDate("einvoice_date");
        return new Row(rs.getString("document_type"), rs.getBoolean("reversal"), rs.getLong("document_id"),
                rs.getLong("line_id"), rs.getString("document_no"), rs.getDate("document_date").toLocalDate(),
                rs.getDate("entry_date").toLocalDate(), invoiceDate == null ? null : invoiceDate.toLocalDate(),
                rs.getString("einvoice_no"), rs.getString("description"), rs.getLong("customer_id"),
                rs.getLong("product_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("amount"));
    }
}
