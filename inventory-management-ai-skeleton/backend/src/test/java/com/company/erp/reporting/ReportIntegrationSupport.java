package com.company.erp.reporting;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.support.ApiTestSupport.Session;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Hỗ trợ test tích hợp báo cáo M10: phiên người dùng mẫu, lập nhanh hàng mới, nhập kho, đơn bán đã ghi sổ qua API thật
 * (dữ liệu báo cáo sinh từ chứng từ, không chèn thẳng sổ).
 */
abstract class ReportIntegrationSupport {

    static final String DATE = "2026-10-05";
    static final String REPORTS = "/api/v1/reports";
    static final String ORDERS = "/api/v1/sales-orders";
    static final String RETURNS = "/api/v1/sales-returns";
    static final String DOCUMENTS = "/api/v1/inventory-documents";
    static final String PURCHASES = "/api/v1/purchase-orders";

    protected final MockMvc mockMvc;
    protected final JdbcTemplate jdbc;
    protected Session admin;
    protected Session keeper;
    protected Session hcmKeeper;
    protected Session salesManager;
    protected Session seller;
    protected Session buyer;
    protected Session clerk;
    protected Session chief;
    protected Session cashier;
    protected Session director;

    ReportIntegrationSupport(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void loginUsers() throws Exception {
        admin = login(mockMvc, "admin");
        keeper = login(mockMvc, "thukho.hn");
        hcmKeeper = login(mockMvc, "kho.hcm");
        salesManager = login(mockMvc, "truongkd.hn");
        seller = login(mockMvc, "kinhdoanh.hn");
        buyer = login(mockMvc, "muahang.hn");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
        cashier = login(mockMvc, "thuquy.hn");
        director = login(mockMvc, "giamdoc");
    }

    MockHttpServletRequestBuilder report(Session session, String path) {
        return as(session, get(REPORTS + path)).param("companyId", "1");
    }

    /** Hàng mới (đơn vị Cái, quản lý lô, hạn dùng) để báo cáo không lẫn phát sinh của test khác. */
    long newProduct(String type) throws Exception {
        String code = unique("P");
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Hàng " + code + "\",\"categoryId\":"
                + id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'VT'") + ",\"productType\":\""
                + type + "\",\"baseUnitId\":" + unit() + ",\"lotTracked\":true,\"expiryTracked\":true,"
                + "\"serialTracked\":false}";
        return jsonId(send(admin, post("/api/v1/products"), body).andExpect(status().isCreated()).andReturn());
    }

    void stockIn(long product, String quantity, String lot) throws Exception {
        stockIn(product, quantity, lot, "2028-12-31");
    }

    void stockIn(long product, String quantity, String lot, String expiryDate) throws Exception {
        String body = "{\"documentType\":\"PNK\",\"documentDate\":\"" + DATE + "\",\"warehouseId\":" + warehouse()
                + ",\"lines\":[{\"productId\":" + product + ",\"unitId\":" + unit() + ",\"quantity\":" + quantity
                + ",\"unitCost\":50000,\"lotNo\":\"" + lot + "\",\"expiryDate\":\"" + expiryDate + "\"}]}";
        long document = jsonId(send(keeper, post(DOCUMENTS), body).andExpect(status().isCreated()).andReturn());
        act(keeper, DOCUMENTS, document, "submit");
        act(keeper, DOCUMENTS, document, "post").andExpect(jsonPath("$.data.status").value("POSTED"));
    }

    /** Đơn bán đã duyệt (trưởng phòng kinh doanh lập, duyệt nếu cần), chưa ghi sổ. */
    long approvedOrder(long customer, long product, String quantity, String extra) throws Exception {
        String body = "{\"branchId\":1,\"customerId\":" + customer + ",\"warehouseId\":" + warehouse()
                + ",\"documentDate\":\"" + DATE + "\"" + extra + ",\"lines\":[{\"productId\":" + product
                + ",\"unitId\":" + unit() + ",\"quantity\":" + quantity + ",\"unitPrice\":100000}]}";
        long order = jsonId(send(salesManager, post(ORDERS), body).andExpect(status().isCreated()).andReturn());
        String state = JsonPath.read(act(salesManager, ORDERS, order, "submit").andReturn().getResponse()
                .getContentAsString(), "$.data.status");
        if ("SUBMITTED".equals(state)) {
            mockMvc.perform(as(salesManager, post(ORDERS + "/" + order + "/approve"))
                    .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON).content("{}"));
        }
        return order;
    }

    /** Đơn bán đã ghi sổ (thủ kho ghi sổ). */
    long postedOrder(long customer, long product, String quantity) throws Exception {
        long order = approvedOrder(customer, product, quantity, "");
        act(keeper, ORDERS, order, "post").andExpect(jsonPath("$.data.status").value("POSTED"));
        return order;
    }

    /** Trả lại {@code quantity} của dòng đơn bán theo lô, đã ghi sổ. */
    long postedReturn(long customer, long order, String lot, String quantity) throws Exception {
        long orderLine = id("SELECT id FROM sales_order_lines WHERE order_id = " + order);
        long goodsReturn = jsonId(send(clerk, post(RETURNS), "{\"documentType\":\"TLB\",\"branchId\":1,\"customerId\":"
                + customer + ",\"salesOrderId\":" + order + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{"
                + "\"orderLineId\":" + orderLine + ",\"lotNo\":\"" + lot + "\",\"quantity\":" + quantity + "}]}")
                .andExpect(status().isCreated()).andReturn());
        act(clerk, RETURNS, goodsReturn, "submit").andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, RETURNS, goodsReturn, "post").andExpect(jsonPath("$.data.status").value("POSTED"));
        return goodsReturn;
    }

    /** Đơn mua đã ghi sổ (nhân viên mua lập, gửi, dưới ngưỡng tự duyệt; thủ kho ghi sổ). */
    long postedPurchase(long supplier, long product, String quantity, String price, String lot)
            throws Exception {
        long order = approvedPurchase(supplier, product, quantity, price, lot);
        act(keeper, PURCHASES, order, "post").andExpect(jsonPath("$.data.status").value("POSTED"));
        return order;
    }

    /** Đơn mua đã duyệt, chưa ghi sổ (dưới ngưỡng duyệt nên gửi là duyệt); dự kiến về 2026-10-15. */
    long approvedPurchase(long supplier, long product, String quantity, String price, String lot)
            throws Exception {
        String body = "{\"branchId\":1,\"documentDate\":\"" + DATE + "\",\"supplierId\":" + supplier
                + ",\"warehouseId\":" + warehouse() + ",\"expectedDate\":\"2026-10-15\",\"invoiceSeries\":\""
                + unique("C26T") + "\",\"invoiceNo\":\"1\",\"invoiceDate\":\"" + DATE + "\",\"lines\":[{\"productId\":"
                + product + ",\"unitId\":" + unit() + ",\"quantity\":" + quantity + ",\"unitPrice\":" + price
                + ",\"lotNo\":\"" + lot + "\",\"expiryDate\":\"2028-01-31\"}]}";
        long order = jsonId(send(buyer, post(PURCHASES), body).andExpect(status().isCreated()).andReturn());
        act(buyer, PURCHASES, order, "submit").andExpect(jsonPath("$.data.status").value("APPROVED"));
        return order;
    }

    ResultActions act(Session session, String base, long id, String action) throws Exception {
        return mockMvc.perform(as(session, post(base + "/" + id + "/" + action))
                .header(IdempotentCommand.HEADER, newKey()));
    }

    ResultActions send(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(as(session, request).header(IdempotentCommand.HEADER, newKey())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    long customer() {
        return id("SELECT id FROM partners WHERE company_id = 1 AND code = 'KH_PK01'");
    }

    long unit() {
        return id("SELECT id FROM units WHERE company_id = 1 AND code = 'CAI'");
    }

    long warehouse() {
        return id("SELECT id FROM warehouses WHERE company_id = 1 AND code = 'KHO_HN'");
    }

    long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    static long jsonId(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }
}
