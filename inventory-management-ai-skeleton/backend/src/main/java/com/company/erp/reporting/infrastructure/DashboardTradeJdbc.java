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
 * Doanh thu và giá trị mua cho dashboard (BR-RPT-01, 10) từ chứng từ đã ghi sổ (chủ dự án chọn nguồn chứng từ,
 * 2026-10-14), cùng cách tính với sổ nhật ký bán hàng: dòng hàng chưa thuế sau chiết khấu; trả lại / giảm giá trừ; chứng
 * từ đã đảo có dòng gốc theo ngày hạch toán và dòng ngược dấu theo ngày đảo. Tổng hợp, top và chi tiết dùng chung một
 * tập dòng nên tổng chi tiết bằng số trên dashboard (BR-RPT-11; ADR-18: SQL chỉ đọc).
 */
@Repository
public class DashboardTradeJdbc {

    public enum Kind { SALES, PURCHASES }

    /** {@code branchIds} null: mọi chi nhánh; {@code ownerId}: chỉ chứng từ mình phụ trách / lập. */
    public record Filter(Kind kind, Long companyId, Collection<Long> branchIds, Long ownerId, LocalDate from,
                         LocalDate to) {
    }

    public record Totals(BigDecimal grossAmount, BigDecimal deductionAmount, long documentCount) {
    }

    public record Ranked(Long id, String code, String name, BigDecimal amount) {
    }

    /** Một chứng từ (dòng gốc hoặc dòng đảo); số tiền có dấu. */
    public record DocumentRow(LocalDate entryDate, String documentType, Long documentId, String documentNo,
                              boolean reversal, String partnerCode, String partnerName, BigDecimal amount) {
    }

    private static final String SALES_ORDER = """
            SELECT 'DBH' AS document_type, h.id AS document_id, h.document_no, h.customer_id AS partner_id,
                   l.product_id, l.amount AS base_amount, %s
            FROM sales_orders h JOIN sales_order_lines l ON l.order_id = h.id""";
    private static final String SALES_RETURN = """
            SELECT h.document_type, h.id, h.document_no, h.customer_id, l.product_id, -l.amount, %s
            FROM sales_returns h JOIN sales_return_lines l ON l.return_id = h.id""";
    /** Đơn mua có thể bằng ngoại tệ: quy đổi theo tỷ giá của đơn. */
    private static final String PURCHASE_ORDER = """
            SELECT 'DMH' AS document_type, h.id AS document_id, h.document_no, h.supplier_id AS partner_id,
                   l.product_id, (l.amount - l.discount_amount) * h.exchange_rate AS base_amount, %s
            FROM purchase_orders h JOIN purchase_order_lines l ON l.order_id = h.id""";
    private static final String PURCHASE_RETURN = """
            SELECT h.document_type, h.id, h.document_no, h.supplier_id, l.product_id, -l.amount * o.exchange_rate, %s
            FROM purchase_returns h JOIN purchase_return_lines l ON l.return_id = h.id
            JOIN purchase_orders o ON o.id = h.purchase_order_id""";

    private static final String POSTED = "FALSE AS reversal, h.posting_date AS entry_date, 1 AS sign";
    private static final String REVERSED = "TRUE, h.reverse_date, -1";
    private static final String POSTED_WHERE = " WHERE h.company_id = :companyId AND h.status IN ('POSTED', 'REVERSED')"
            + " AND h.posting_date BETWEEN :from AND :to";
    private static final String REVERSED_WHERE = " WHERE h.company_id = :companyId AND h.status = 'REVERSED'"
            + " AND h.reverse_date BETWEEN :from AND :to";

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardTradeJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals(Filter filter) {
        String sql = """
                SELECT COALESCE(SUM(IF(document_type IN ('DBH', 'DMH'), base_amount * sign, 0)), 0) AS gross,
                       COALESCE(SUM(IF(document_type IN ('DBH', 'DMH'), 0, -base_amount * sign)), 0) AS deduction,
                       COUNT(DISTINCT IF(document_type IN ('DBH', 'DMH') AND NOT reversal, document_id, NULL)) AS docs
                FROM (%s) x
                """.formatted(lines(filter));
        return jdbc.queryForObject(sql, params(filter), (rs, rowNum) -> new Totals(rs.getBigDecimal("gross"),
                rs.getBigDecimal("deduction"), rs.getLong("docs")));
    }

    /** Hàng có giá trị thuần dương lớn nhất. */
    public List<Ranked> topProducts(Filter filter, int limit) {
        return ranked(filter, "products", "product_id", limit);
    }

    /** Khách (bán) hoặc NCC (mua) có giá trị thuần dương lớn nhất. */
    public List<Ranked> topPartners(Filter filter, int limit) {
        return ranked(filter, "partners", "partner_id", limit);
    }

    /** Chứng từ cấu thành giá trị; lọc thêm theo đối tượng hoặc hàng (xem chi tiết một dòng top). */
    public List<DocumentRow> documents(Filter filter, Long partnerId, Long productId) {
        StringBuilder where = new StringBuilder("TRUE");
        MapSqlParameterSource params = params(filter);
        if (partnerId != null) {
            where.append(" AND x.partner_id = :partnerId");
            params.addValue("partnerId", partnerId);
        }
        if (productId != null) {
            where.append(" AND x.product_id = :productId");
            params.addValue("productId", productId);
        }
        String sql = """
                SELECT x.entry_date, x.document_type, x.document_id, x.document_no, x.reversal, c.code, c.name,
                       SUM(x.base_amount * x.sign) AS amount
                FROM (%s) x JOIN partners c ON c.id = x.partner_id
                WHERE %s
                GROUP BY x.entry_date, x.document_type, x.document_id, x.document_no, x.reversal, c.code, c.name
                ORDER BY x.entry_date, x.document_no, x.reversal
                """.formatted(lines(filter), where);
        return jdbc.query(sql, params, (rs, rowNum) -> new DocumentRow(rs.getDate("entry_date").toLocalDate(),
                rs.getString("document_type"), rs.getLong("document_id"), rs.getString("document_no"),
                rs.getBoolean("reversal"), rs.getString("code"), rs.getString("name"), rs.getBigDecimal("amount")));
    }

    private List<Ranked> ranked(Filter filter, String table, String column, int limit) {
        String sql = """
                SELECT t.id, t.code, t.name, SUM(x.base_amount * x.sign) AS amount
                FROM (%s) x JOIN %s t ON t.id = x.%s
                GROUP BY t.id, t.code, t.name
                HAVING amount > 0
                ORDER BY amount DESC, t.code
                LIMIT :limit
                """.formatted(lines(filter), table, column);
        return jdbc.query(sql, params(filter).addValue("limit", limit), (rs, rowNum) -> new Ranked(rs.getLong("id"),
                rs.getString("code"), rs.getString("name"), rs.getBigDecimal("amount")));
    }

    /** Dòng hàng của chứng từ gốc và chứng từ trả lại / giảm giá, dòng ghi sổ và dòng đảo. */
    private static String lines(Filter filter) {
        boolean sales = filter.kind() == Kind.SALES;
        StringBuilder extra = new StringBuilder();
        if (filter.branchIds() != null) {
            extra.append(" AND h.branch_id IN (:branchIds)");
        }
        if (filter.ownerId() != null) {
            extra.append(sales ? " AND (h.sales_user_id = :ownerId OR h.created_by = :ownerId)"
                    : " AND h.created_by = :ownerId");
        }
        String order = sales ? SALES_ORDER : PURCHASE_ORDER;
        String goodsReturn = sales ? SALES_RETURN : PURCHASE_RETURN;
        return String.join(" UNION ALL ",
                order.formatted(POSTED) + POSTED_WHERE + extra,
                order.formatted(REVERSED) + REVERSED_WHERE + extra,
                goodsReturn.formatted(POSTED) + POSTED_WHERE + extra,
                goodsReturn.formatted(REVERSED) + REVERSED_WHERE + extra);
    }

    private static MapSqlParameterSource params(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", filter.companyId())
                .addValue("from", Date.valueOf(filter.from())).addValue("to", Date.valueOf(filter.to()))
                .addValue("ownerId", filter.ownerId());
        if (filter.branchIds() != null) {
            params.addValue("branchIds", filter.branchIds());
        }
        return params;
    }
}
