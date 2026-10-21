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
 * Dòng tiền dự kiến (BR-CSH-06, BR-RPT-09, 30; ADR-18: SQL chỉ đọc). Dự thu: khoản phải thu còn lại theo hạn thanh toán,
 * đơn bán đã duyệt chưa ghi sổ (chủ dự án chọn 2026-10-14). Dự chi: khoản phải trả còn lại theo hạn, đơn mua đã duyệt
 * chưa ghi sổ. Từ D-61 không còn bước duyệt: chỉ đơn đã duyệt trước D-61 còn được tính, đơn nháp không tính (chủ dự án
 * chọn 2026-10-21). Tổng hợp và chi tiết dùng chung một tập khoản nên tổng chi tiết bằng số trên báo cáo (BR-RPT-11).
 */
@Repository
public class CashForecastJdbc {

    /** {@code branchIds} null: mọi chi nhánh; {@code ownerId}: chỉ dữ liệu mình phụ trách / lập. */
    public record Scope(Long companyId, Collection<Long> branchIds, Long ownerId) {
    }

    public record Balance(Long id, String code, String name, String accountType, BigDecimal balance) {
    }

    /** Tổng tiền dự kiến của một nguồn trong một ngày. */
    public record DailyAmount(String source, LocalDate expectedDate, BigDecimal amount) {
    }

    public record Item(String source, String documentType, Long documentId, String documentNo, String partnerCode,
                       String partnerName, LocalDate expectedDate, BigDecimal amount) {
    }

    /** Ngày dự kiến của đơn chưa ghi sổ: ngày dự kiến giao / nhận (trống: ngày đơn) cộng số ngày được nợ. */
    private static final String ORDER_DATE =
            "DATE_ADD(COALESCE(h.expected_date, h.document_date), INTERVAL COALESCE(h.payment_term_days, 0) DAY)";

    private final NamedParameterJdbcTemplate jdbc;

    public CashForecastJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Số dư từng quỹ / tài khoản ngân hàng từ sổ quỹ đến {@code asOf}; bỏ tài khoản ngừng dùng đã hết số dư. */
    public List<Balance> balances(Scope scope, LocalDate asOf) {
        MapSqlParameterSource params = params(scope).addValue("asOf", Date.valueOf(asOf));
        String sql = """
                SELECT a.id, a.code, a.name, a.account_type,
                       COALESCE(SUM(e.debit_amount - e.credit_amount), 0) AS balance
                FROM cash_accounts a
                LEFT JOIN cash_ledger_entries e ON e.cash_account_id = a.id AND e.posting_date <= :asOf
                WHERE a.company_id = :companyId%s
                GROUP BY a.id, a.code, a.name, a.account_type, a.status
                HAVING a.status = 'ACTIVE' OR balance <> 0
                ORDER BY a.account_type, a.code
                """.formatted(scope.branchIds() == null ? "" : " AND a.branch_id IN (:branchIds)");
        return jdbc.query(sql, params, (rs, rowNum) -> new Balance(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("account_type"), rs.getBigDecimal("balance")));
    }

    public List<DailyAmount> dailyAmounts(Scope scope) {
        String sql = """
                SELECT x.source, x.expected_date, SUM(x.amount) AS amount FROM (%s) x
                GROUP BY x.source, x.expected_date
                ORDER BY x.expected_date, x.source
                """.formatted(itemsSql(scope));
        return jdbc.query(sql, params(scope), (rs, rowNum) -> new DailyAmount(rs.getString("source"),
                rs.getDate("expected_date").toLocalDate(), rs.getBigDecimal("amount")));
    }

    /** Khoản dự kiến; lọc nguồn và khoảng ngày dự kiến (null: không giới hạn). */
    public List<Item> items(Scope scope, String source, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = params(scope);
        StringBuilder where = new StringBuilder("TRUE");
        if (source != null) {
            where.append(" AND x.source = :source");
            params.addValue("source", source);
        }
        if (from != null) {
            where.append(" AND x.expected_date >= :from");
            params.addValue("from", Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND x.expected_date <= :to");
            params.addValue("to", Date.valueOf(to));
        }
        String sql = """
                SELECT x.source, x.document_type, x.document_id, x.document_no, p.code, p.name, x.expected_date,
                       x.amount
                FROM (%s) x JOIN partners p ON p.id = x.partner_id
                WHERE %s
                ORDER BY x.expected_date, x.source, x.document_no
                """.formatted(itemsSql(scope), where);
        return jdbc.query(sql, params, (rs, rowNum) -> new Item(rs.getString("source"),
                rs.getString("document_type"), rs.getLong("document_id"), rs.getString("document_no"),
                rs.getString("code"), rs.getString("name"), rs.getDate("expected_date").toLocalDate(),
                rs.getBigDecimal("amount")));
    }

    private static String itemsSql(Scope scope) {
        boolean owned = scope.ownerId() != null;
        String debtOwner = owned ? " AND (i.sales_user_id = :ownerId OR i.created_by = :ownerId)" : "";
        String salesOwner = owned ? " AND (h.sales_user_id = :ownerId OR h.created_by = :ownerId)" : "";
        String purchaseOwner = owned ? " AND h.created_by = :ownerId" : "";
        return """
                SELECT IF(i.debt_type = 'RECEIVABLE', 'RECEIVABLE', 'PAYABLE') AS source,
                       i.source_type AS document_type, i.source_id AS document_id, i.source_no AS document_no,
                       i.partner_id, i.due_date AS expected_date, i.remaining_amount AS amount
                FROM debt_items i
                WHERE i.company_id = :companyId AND i.status IN ('OPEN', 'PARTIAL') AND i.remaining_amount > 0%s%s
                UNION ALL
                SELECT 'SALES_ORDER', 'DBH', h.id, h.document_no, h.customer_id, %s, h.total_amount
                FROM sales_orders h
                WHERE h.company_id = :companyId AND h.status = 'APPROVED' AND h.total_amount > 0%s%s
                UNION ALL
                SELECT 'PURCHASE_ORDER', 'DMH', h.id, h.document_no, h.supplier_id, COALESCE(h.due_date, %s),
                       h.total_amount * h.exchange_rate
                FROM purchase_orders h
                WHERE h.company_id = :companyId AND h.status = 'APPROVED' AND h.total_amount > 0%s%s
                """.formatted(branches(scope, "i"), debtOwner, ORDER_DATE, branches(scope, "h"), salesOwner,
                ORDER_DATE, branches(scope, "h"), purchaseOwner);
    }

    private static String branches(Scope scope, String alias) {
        return scope.branchIds() == null ? "" : " AND " + alias + ".branch_id IN (:branchIds)";
    }

    private static MapSqlParameterSource params(Scope scope) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", scope.companyId())
                .addValue("ownerId", scope.ownerId());
        if (scope.branchIds() != null) {
            params.addValue("branchIds", scope.branchIds());
        }
        return params;
    }
}
