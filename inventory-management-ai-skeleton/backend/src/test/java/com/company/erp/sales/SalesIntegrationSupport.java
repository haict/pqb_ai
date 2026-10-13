package com.company.erp.sales;

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

/**
 * Hỗ trợ test tích hợp bán hàng: phiên người dùng mẫu (nhân viên kinh doanh HN chỉ xem dữ liệu của mình, trưởng phòng
 * kinh doanh HN, thủ kho HN, kế toán công nợ, kế toán trưởng), tra id danh mục mẫu, nhập tồn nhanh bằng phiếu nhập kho.
 */
abstract class SalesIntegrationSupport {

    static final String DATE = "2026-10-05";
    static final String ORDERS = "/api/v1/sales-orders";

    protected final MockMvc mockMvc;
    protected final JdbcTemplate jdbc;

    protected Session seller;
    protected Session manager;
    protected Session keeper;
    protected Session clerk;
    protected Session chief;
    protected Session admin;
    protected Session courier;

    SalesIntegrationSupport(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void loginUsers() throws Exception {
        seller = login(mockMvc, "kinhdoanh.hn");
        manager = login(mockMvc, "truongkd.hn");
        keeper = login(mockMvc, "thukho.hn");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
        admin = login(mockMvc, "admin");
        courier = login(mockMvc, "giaohang.hn");
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

    BigDecimal reserved(long productId, long warehouseId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(reserved_quantity), 0) FROM inventory_stock_summaries "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, productId, warehouseId);
    }

    BigDecimal lotOnHand(String lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(b.on_hand_quantity), 0) FROM inventory_balances b "
                + "JOIN inventory_lots l ON l.id = b.lot_id WHERE l.lot_no = ?", BigDecimal.class, lot);
    }

    /** Nhập tồn VT_ONG_01 (đơn vị Hộp) vào kho HN bằng phiếu nhập kho: lập, gửi (tự duyệt), ghi sổ. */
    void stockIn(String quantity, String lot, String expiry) throws Exception {
        String body = "{\"documentType\":\"PNK\",\"documentDate\":\"" + DATE + "\",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"lines\":[{\"productId\":" + product("VT_ONG_01") + ",\"unitId\":"
                + unit("HOP") + ",\"quantity\":" + quantity + ",\"unitCost\":500000,\"lotNo\":\"" + lot
                + "\",\"expiryDate\":\"" + expiry + "\"}]}";
        long document = jsonId(send(keeper, post("/api/v1/inventory-documents"), body, newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, "/api/v1/inventory-documents", document, "submit", null);
        act(keeper, "/api/v1/inventory-documents", document, "post", null)
                .andExpect(jsonPath("$.data.status").value("POSTED"));
    }

    /** Hàng mới đơn vị cơ sở Cái (để FEFO không lẫn tồn của test khác). */
    long newProduct(boolean lot, boolean serial) throws Exception {
        String code = unique("P");
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Hàng " + code + "\",\"categoryId\":"
                + id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'VT'")
                + ",\"productType\":\"CONSUMABLE\",\"baseUnitId\":" + unit("CAI") + ",\"lotTracked\":" + lot
                + ",\"expiryTracked\":" + lot + ",\"serialTracked\":" + serial + "}";
        return jsonId(send(admin, post("/api/v1/products"), body, newKey()).andExpect(status().isCreated())
                .andReturn());
    }

    /** Nhập tồn hàng {@code product} (đơn vị Cái) vào kho HN. */
    void stockIn(long product, String quantity, String lot, String expiry, List<String> serials) throws Exception {
        String body = "{\"documentType\":\"PNK\",\"documentDate\":\"" + DATE + "\",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"lines\":[{\"productId\":" + product + ",\"unitId\":" + unit("CAI")
                + ",\"quantity\":" + quantity + ",\"unitCost\":50000"
                + (lot == null ? "" : ",\"lotNo\":\"" + lot + "\",\"expiryDate\":\"" + expiry + "\"")
                + (serials == null ? "" : ",\"serialNumbers\":[\"" + String.join("\",\"", serials) + "\"]") + "}]}";
        long document = jsonId(send(keeper, post("/api/v1/inventory-documents"), body, newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, "/api/v1/inventory-documents", document, "submit", null);
        act(keeper, "/api/v1/inventory-documents", document, "post", null)
                .andExpect(jsonPath("$.data.status").value("POSTED"));
    }

    /** Gửi duyệt đơn bán; khách mẫu dùng chung có thể có nợ quá hạn từ test khác nên duyệt khi đơn phải duyệt. */
    void submitAndApproveOrder(long order) throws Exception {
        String status = json(act(manager, ORDERS, order, "submit", null).andExpect(status().isOk()).andReturn(),
                "$.data.status");
        if ("SUBMITTED".equals(status)) {
            act(manager, ORDERS, order, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        }
    }

    /** Lập (người lập {@code creator}), gửi duyệt / duyệt và ghi sổ đơn bán (thủ kho); trả id. */
    long postedOrder(Session creator, String body) throws Exception {
        long order = jsonId(send(creator, post(ORDERS), body, newKey()).andExpect(status().isCreated()).andReturn());
        submitAndApproveOrder(order);
        act(keeper, ORDERS, order, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        return order;
    }

    /** Còn phải thu của khoản công nợ sinh từ đơn bán. */
    BigDecimal receivable(long order) {
        return jdbc.queryForObject("SELECT original_amount - settled_amount FROM debt_items WHERE source_type = 'DBH' "
                + "AND source_id = ?", BigDecimal.class, order);
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

    static String line(long product, long unit, String quantity, String price, String discount) {
        return "{\"productId\":" + product + ",\"unitId\":" + unit + ",\"quantity\":" + quantity
                + (price == null ? "" : ",\"unitPrice\":" + price)
                + (discount == null ? "" : ",\"discountPercent\":" + discount) + "}";
    }

    static String order(long customer, long warehouse, boolean reserve, List<String> lines) {
        return "{\"branchId\":1,\"customerId\":" + customer + ",\"warehouseId\":" + warehouse + ",\"documentDate\":\""
                + DATE + "\",\"reserveStock\":" + reserve + ",\"lines\":[" + String.join(",", lines) + "]}";
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
