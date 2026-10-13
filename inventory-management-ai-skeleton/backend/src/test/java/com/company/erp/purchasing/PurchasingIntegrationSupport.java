package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.support.ApiTestSupport.Session;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Dữ liệu, lệnh và khẳng định dùng chung cho test tích hợp mua hàng (D-54: đơn mua là chứng từ ghi sổ duy nhất). */
abstract class PurchasingIntegrationSupport {

    static final String DATE = "2026-10-05";
    static final String ORDERS = "/api/v1/purchase-orders";
    static final String EXPIRY = "2028-01-31";

    protected final MockMvc mockMvc;
    protected final JdbcTemplate jdbc;

    protected Session buyer;
    protected Session director;
    protected Session keeper;
    protected Session clerk;
    protected Session chief;
    protected Session hcmKeeper;

    PurchasingIntegrationSupport(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void loginUsers() throws Exception {
        buyer = login(mockMvc, "muahang.hn");
        director = login(mockMvc, "giamdoc");
        keeper = login(mockMvc, "thukho.hn");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
        hcmKeeper = login(mockMvc, "kho.hcm");
    }

    long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    long product(String code) {
        return id("SELECT id FROM products WHERE company_id = 1 AND code = '" + code + "'");
    }

    long unit(String code) {
        return id("SELECT id FROM units WHERE company_id = 1 AND code = '" + code + "'");
    }

    long warehouse(String code) {
        return id("SELECT id FROM warehouses WHERE company_id = 1 AND code = '" + code + "'");
    }

    long partner(String code) {
        return id("SELECT id FROM partners WHERE company_id = 1 AND code = '" + code + "'");
    }

    long taxRate(String code) {
        return id("SELECT id FROM tax_rates WHERE company_id = 1 AND code = '" + code + "'");
    }

    long orderLineId(long order, int lineNo) {
        return id("SELECT id FROM purchase_order_lines WHERE order_id = " + order + " AND line_no = " + lineNo);
    }

    BigDecimal incoming(long productId, long warehouseId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(incoming_quantity), 0) FROM inventory_stock_summaries "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, productId, warehouseId);
    }

    BigDecimal onHand(long productId, long warehouseId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_stock_summaries "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, productId, warehouseId);
    }

    BigDecimal lotOnHand(String lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(b.on_hand_quantity), 0) FROM inventory_balances b "
                + "JOIN inventory_lots l ON l.id = b.lot_id WHERE l.lot_no = ?", BigDecimal.class, lot);
    }

    /** Còn phải trả của khoản công nợ sinh từ đơn mua. */
    BigDecimal debtRemaining(long order) {
        return jdbc.queryForObject("SELECT original_amount - settled_amount FROM debt_items WHERE source_type = 'DMH' "
                + "AND source_id = ?", BigDecimal.class, order);
    }

    /** Tồn chi tiết = tổng tồn = sổ nhật ký kho (BR mục 5.4). */
    void assertLedgerConsistent(long productId, long warehouseId) {
        BigDecimal balances = jdbc.queryForObject("SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_balances "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, productId, warehouseId);
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(IF(direction = 'IN', quantity, -quantity)), 0) "
                + "FROM inventory_transactions WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class,
                productId, warehouseId);
        assertThat(balances).isEqualByComparingTo(onHand(productId, warehouseId));
        assertThat(ledger).isEqualByComparingTo(balances);
    }

    /** Dòng bút toán gốc "N|C:tài khoản:số tiền"; khẳng định cân Nợ = Có (kể cả bút toán đảo). */
    List<String> journal(String type, long source) {
        List<String> lines = jdbc.queryForList("""
                SELECT CONCAT(IF(l.debit_amount > 0, 'N', 'C'), ':', a.code, ':',
                              CAST(l.debit_amount + l.credit_amount AS DECIMAL(19,0)))
                FROM journal_entry_lines l
                JOIN journal_entries e ON e.id = l.journal_entry_id
                JOIN chart_of_accounts a ON a.id = l.account_id
                WHERE e.source_type = ? AND e.source_id = ? AND e.reversal_of_id IS NULL
                ORDER BY l.line_no
                """, String.class, type, source);
        BigDecimal net = jdbc.queryForObject("SELECT COALESCE(SUM(l.debit_amount) - SUM(l.credit_amount), 0) FROM "
                + "journal_entry_lines l JOIN journal_entries e ON e.id = l.journal_entry_id WHERE "
                + "e.source_type = ? AND e.source_id = ?", BigDecimal.class, type, source);
        assertThat(net).isEqualByComparingTo("0");
        return lines;
    }

    static String orderLine(long product, long unit, String quantity, String price, String discount, Long taxRate) {
        StringBuilder line = new StringBuilder("{\"productId\":" + product + ",\"unitId\":" + unit + ",\"quantity\":"
                + quantity + ",\"unitPrice\":" + price);
        if (discount != null) {
            line.append(",\"discountPercent\":").append(discount);
        }
        if (taxRate != null) {
            line.append(",\"taxRateId\":").append(taxRate);
        }
        return line.append('}').toString();
    }

    /** Dòng hàng quản lý lô, hạn dùng. */
    static String lotLine(long product, long unit, String quantity, String price, Long taxRate, String lot) {
        return orderLine(product, unit, quantity, price, null, taxRate).replaceFirst("}$",
                ",\"lotNo\":\"" + lot + "\",\"expiryDate\":\"" + EXPIRY + "\"}");
    }

    static String order(long supplier, long warehouse, List<String> lines) {
        return "{\"branchId\":1,\"documentDate\":\"" + DATE + "\",\"supplierId\":" + supplier + ",\"warehouseId\":"
                + warehouse + ",\"expectedDate\":\"2026-10-15\",\"lines\":[" + String.join(",", lines) + "]}";
    }

    /** Đơn mua kèm hóa đơn của nhà cung cấp (ký hiệu, số, ngày). */
    static String orderWithInvoice(long supplier, long warehouse, String series, String number, List<String> lines) {
        return order(supplier, warehouse, lines).replace("\"lines\"", "\"invoiceSeries\":\"" + series
                + "\",\"invoiceNo\":\"" + number + "\",\"invoiceDate\":\"" + DATE + "\",\"lines\"");
    }

    /** Lập, gửi duyệt (dưới ngưỡng: tự duyệt) và ghi sổ đơn mua; trả id. */
    long postedOrder(String body) throws Exception {
        long order = jsonId(send(buyer, post(ORDERS), body, newKey()).andExpect(status().isCreated()).andReturn());
        act(buyer, ORDERS, order, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(keeper, ORDERS, order, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        return order;
    }

    /** Đơn mua đã ghi sổ: {@code quantity} hộp ống nghiệm lô {@code lot}, 500.000 đ/hộp, VAT 10%. */
    long postedTubeOrder(String quantity, String lot) throws Exception {
        return postedOrder(orderWithInvoice(partner("NCC_01"), warehouse("KHO_HN"), unique("C26T"), "1",
                List.of(lotLine(product("VT_ONG_01"), unit("HOP"), quantity, "500000", taxRate("VAT10"), lot))));
    }

    ResultActions act(Session session, String base, long id, String action, String body) throws Exception {
        MockHttpServletRequestBuilder request = as(session, post(base + "/" + id + "/" + action))
                .header(IdempotentCommand.HEADER, newKey());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    ResultActions send(Session session, MockHttpServletRequestBuilder request, String body, String key)
            throws Exception {
        return mockMvc.perform(as(session, request).header(IdempotentCommand.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    static long jsonId(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    static <T> T json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
