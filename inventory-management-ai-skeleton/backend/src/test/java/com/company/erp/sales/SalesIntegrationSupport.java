package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
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
    static final String QUOTES = "/api/v1/sales-quotes";
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

    static String line(long product, long unit, String quantity, String price, String discount) {
        return "{\"productId\":" + product + ",\"unitId\":" + unit + ",\"quantity\":" + quantity
                + (price == null ? "" : ",\"unitPrice\":" + price)
                + (discount == null ? "" : ",\"discountPercent\":" + discount) + "}";
    }

    static String quote(long customer, List<String> lines) {
        return "{\"branchId\":1,\"customerId\":" + customer + ",\"documentDate\":\"" + DATE
                + "\",\"validUntil\":\"2099-12-31\",\"lines\":[" + String.join(",", lines) + "]}";
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
