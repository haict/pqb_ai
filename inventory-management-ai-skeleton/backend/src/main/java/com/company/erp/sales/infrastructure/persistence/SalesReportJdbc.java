package com.company.erp.sales.infrastructure.persistence;

import com.company.erp.sales.domain.SalesReportGrouping;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn tổng hợp doanh số (BR-SAL-51) từ chứng từ đã ghi sổ: đơn bán (doanh thu), trả hàng / giảm giá hàng bán
 * (giảm trừ), theo ngày hạch toán. Nhóm theo nhân viên, khách, hàng, chi nhánh. Cột nhóm lấy từ danh sách cố định.
 */
@Repository
public class SalesReportJdbc {

    /** Số liệu một nhóm: khóa, số đơn bán đã ghi sổ, doanh thu, giảm trừ, thuế (thuế giảm trừ đã trừ). */
    public record Row(Long key, long invoiceCount, BigDecimal revenue, BigDecimal deductions, BigDecimal tax) {
    }

    /** Chứng từ cấu thành của một nhóm (giảm trừ mang dấu âm). */
    public record Source(String type, Long id, String number, LocalDate postingDate, Long customerId,
                         BigDecimal amount) {
    }

    private final JdbcTemplate jdbc;

    public SalesReportJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> summary(Long companyId, Long branchId, LocalDate from, LocalDate to, SalesReportGrouping grouping) {
        String sql = """
                SELECT k, SUM(cnt) AS cnt, SUM(revenue) AS revenue, SUM(deductions) AS deductions, SUM(tax) AS tax FROM (
                  SELECT %s AS k, COUNT(DISTINCT i.id) AS cnt, SUM(l.amount) AS revenue, 0 AS deductions,
                         SUM(l.tax_amount) AS tax
                  FROM sales_orders i JOIN sales_order_lines l ON l.order_id = i.id
                  WHERE i.company_id = ? AND i.status IN ('POSTED', 'REVERSED') AND i.posting_date BETWEEN ? AND ?
                    AND (i.status = 'POSTED' OR i.reverse_date > ?) AND (? IS NULL OR i.branch_id = ?)
                  GROUP BY %s
                  UNION ALL
                  SELECT %s AS k, 0, 0, SUM(rl.amount), -SUM(rl.tax_amount)
                  FROM sales_returns r JOIN sales_return_lines rl ON rl.return_id = r.id
                  WHERE r.company_id = ? AND r.status IN ('POSTED', 'REVERSED') AND r.posting_date BETWEEN ? AND ?
                    AND (r.status = 'POSTED' OR r.reverse_date > ?) AND (? IS NULL OR r.branch_id = ?)
                  GROUP BY %s
                ) t GROUP BY k ORDER BY SUM(revenue) - SUM(deductions) DESC
                """.formatted(invoiceKey(grouping), invoiceKey(grouping), returnKey(grouping), returnKey(grouping));
        return jdbc.query(sql, (rs, i) -> new Row(rs.getObject("k") == null ? null : rs.getLong("k"),
                rs.getLong("cnt"), rs.getBigDecimal("revenue"), rs.getBigDecimal("deductions"),
                rs.getBigDecimal("tax")), concat(scope(companyId, branchId, from, to), scope(companyId, branchId, from, to))
                .toArray());
    }

    /** Đơn bán, trả hàng / giảm giá cấu thành nhóm {@code key} (BR mục 5, truy ngược số liệu). */
    public List<Source> sources(Long companyId, Long branchId, LocalDate from, LocalDate to, SalesReportGrouping grouping,
                                Long key) {
        String sql = """
                SELECT 'DBH' AS type, i.id, i.document_no, i.posting_date, i.customer_id, SUM(l.amount) AS amount
                FROM sales_orders i JOIN sales_order_lines l ON l.order_id = i.id
                WHERE i.company_id = ? AND i.status IN ('POSTED', 'REVERSED') AND i.posting_date BETWEEN ? AND ?
                  AND (i.status = 'POSTED' OR i.reverse_date > ?) AND (? IS NULL OR i.branch_id = ?) AND %s <=> ?
                GROUP BY i.id, i.document_no, i.posting_date, i.customer_id
                UNION ALL
                SELECT r.document_type, r.id, r.document_no, r.posting_date, r.customer_id, -SUM(rl.amount)
                FROM sales_returns r JOIN sales_return_lines rl ON rl.return_id = r.id
                WHERE r.company_id = ? AND r.status IN ('POSTED', 'REVERSED') AND r.posting_date BETWEEN ? AND ?
                  AND (r.status = 'POSTED' OR r.reverse_date > ?) AND (? IS NULL OR r.branch_id = ?)
                  AND %s <=> ?
                GROUP BY r.document_type, r.id, r.document_no, r.posting_date, r.customer_id
                ORDER BY 4, 3
                """.formatted(invoiceKey(grouping), returnKey(grouping));
        List<Object> params = new ArrayList<>(scope(companyId, branchId, from, to));
        params.add(key);
        params.addAll(scope(companyId, branchId, from, to));
        params.add(key);
        return jdbc.query(sql, (rs, i) -> new Source(rs.getString("type"), rs.getLong("id"),
                rs.getString("document_no"), rs.getDate("posting_date").toLocalDate(), rs.getLong("customer_id"),
                rs.getBigDecimal("amount")), params.toArray());
    }

    /** Công nợ phải thu còn lại theo nhóm (khách, nhân viên, chi nhánh); không áp dụng cho nhóm hàng. */
    public List<Object[]> receivables(Long companyId, Long branchId, SalesReportGrouping grouping) {
        String column = switch (grouping) {
            case CUSTOMER -> "partner_id";
            case SALES_USER -> "sales_user_id";
            case BRANCH -> "branch_id";
            case PRODUCT -> null;
        };
        if (column == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT %s AS k, SUM(original_amount - settled_amount) AS remaining FROM debt_items
                WHERE company_id = ? AND debt_type = 'RECEIVABLE' AND status <> 'REVERSED'
                  AND (? IS NULL OR branch_id = ?)
                GROUP BY %s
                """.formatted(column, column), (rs, i) -> new Object[] {rs.getObject("k") == null ? null
                : rs.getLong("k"), rs.getBigDecimal("remaining")}, companyId, branchId, branchId);
    }

    private static String invoiceKey(SalesReportGrouping grouping) {
        return switch (grouping) {
            case SALES_USER -> "i.sales_user_id";
            case CUSTOMER -> "i.customer_id";
            case PRODUCT -> "l.product_id";
            case BRANCH -> "i.branch_id";
        };
    }

    private static String returnKey(SalesReportGrouping grouping) {
        return switch (grouping) {
            case SALES_USER -> "r.sales_user_id";
            case CUSTOMER -> "r.customer_id";
            case PRODUCT -> "rl.product_id";
            case BRANCH -> "r.branch_id";
        };
    }

    /** Tham số một nhánh UNION: công ty, từ ngày, đến ngày, mốc đảo (đến ngày), chi nhánh (hai lần). */
    private static List<Object> scope(Long companyId, Long branchId, LocalDate from, LocalDate to) {
        return Arrays.asList(companyId, Date.valueOf(from), Date.valueOf(to), Date.valueOf(to), branchId, branchId);
    }

    private static List<Object> concat(List<Object> first, List<Object> second) {
        List<Object> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
