package com.company.erp.finance.infrastructure.persistence;

import com.company.erp.finance.domain.DebtType;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tuổi nợ theo đối tượng (BR-AR-05): cộng số còn lại của khoản công nợ chưa tất toán theo nhóm số ngày quá hạn tại
 * ngày xét. Gộp và phân trang ở database.
 */
@Repository
public class DebtAgingJdbc {

    public record AgingFilter(Long companyId, DebtType debtType, LocalDate asOf, Collection<Long> branchIds,
                              Long partnerId, int bucket1, int bucket2, int bucket3) {
    }

    /** Số còn lại theo nhóm: chưa đến hạn, quá hạn 1..m1, m1+1..m2, m2+1..m3, trên m3 ngày. */
    public record AgingRow(Long partnerId, BigDecimal notDue, BigDecimal bucket1, BigDecimal bucket2,
                           BigDecimal bucket3, BigDecimal bucket4, BigDecimal total) {
    }

    private static final String SUMS = """
            COALESCE(SUM(CASE WHEN DATEDIFF(?, due_date) <= 0 THEN remaining_amount END), 0),
            COALESCE(SUM(CASE WHEN DATEDIFF(?, due_date) BETWEEN 1 AND ? THEN remaining_amount END), 0),
            COALESCE(SUM(CASE WHEN DATEDIFF(?, due_date) BETWEEN ? + 1 AND ? THEN remaining_amount END), 0),
            COALESCE(SUM(CASE WHEN DATEDIFF(?, due_date) BETWEEN ? + 1 AND ? THEN remaining_amount END), 0),
            COALESCE(SUM(CASE WHEN DATEDIFF(?, due_date) > ? THEN remaining_amount END), 0),
            COALESCE(SUM(remaining_amount), 0)
            """;

    private final JdbcTemplate jdbc;

    public DebtAgingJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AgingRow> rows(AgingFilter filter, int limit, long offset) {
        List<Object> args = sumArgs(filter);
        String where = where(filter, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT partner_id, " + SUMS + " FROM debt_items " + where
                        + " GROUP BY partner_id ORDER BY SUM(remaining_amount) DESC, partner_id LIMIT ? OFFSET ?",
                (rs, row) -> new AgingRow(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7)),
                args.toArray());
    }

    public AgingRow totals(AgingFilter filter) {
        List<Object> args = sumArgs(filter);
        String where = where(filter, args);
        return jdbc.queryForObject("SELECT " + SUMS + " FROM debt_items " + where,
                (rs, row) -> new AgingRow(null, rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6)),
                args.toArray());
    }

    public long countPartners(AgingFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Long count = jdbc.queryForObject("SELECT COUNT(DISTINCT partner_id) FROM debt_items " + where, Long.class,
                args.toArray());
        return count == null ? 0 : count;
    }

    private static List<Object> sumArgs(AgingFilter filter) {
        Date asOf = Date.valueOf(filter.asOf());
        List<Object> args = new ArrayList<>();
        args.add(asOf);
        args.addAll(List.of(asOf, filter.bucket1()));
        args.addAll(List.of(asOf, filter.bucket1(), filter.bucket2()));
        args.addAll(List.of(asOf, filter.bucket2(), filter.bucket3()));
        args.addAll(List.of(asOf, filter.bucket3()));
        return args;
    }

    private static String where(AgingFilter filter, List<Object> args) {
        StringBuilder where = new StringBuilder(
                "WHERE company_id = ? AND debt_type = ? AND status IN ('OPEN', 'PARTIAL')");
        args.add(filter.companyId());
        args.add(filter.debtType().name());
        if (filter.branchIds() != null) {
            where.append(" AND branch_id IN (")
                    .append(filter.branchIds().stream().map(id -> "?").collect(Collectors.joining(",")))
                    .append(')');
            args.addAll(filter.branchIds());
        }
        if (filter.partnerId() != null) {
            where.append(" AND partner_id = ?");
            args.add(filter.partnerId());
        }
        return where.toString();
    }
}
