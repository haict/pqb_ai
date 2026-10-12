package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * Hỗ trợ test tích hợp mua hàng: phiên người dùng mẫu (mua hàng HN, giám đốc, giám đốc chi nhánh HN, thủ kho HCM),
 * tra id danh mục mẫu, gửi lệnh có Idempotency-Key, đọc hàng đang về.
 */
abstract class PurchasingIntegrationSupport {

    static final String DATE = "2026-10-05";
    static final String REQUISITIONS = "/api/v1/purchase-requisitions";
    static final String ORDERS = "/api/v1/purchase-orders";

    protected final MockMvc mockMvc;
    protected final JdbcTemplate jdbc;

    protected Session buyer;
    protected Session director;
    protected Session branchDirector;
    protected Session hcmKeeper;

    PurchasingIntegrationSupport(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void loginUsers() throws Exception {
        buyer = login(mockMvc, "muahang.hn");
        director = login(mockMvc, "giamdoc");
        branchDirector = login(mockMvc, "giamdoc.hn");
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

    BigDecimal incoming(long productId, long warehouseId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(incoming_quantity), 0) FROM inventory_stock_summaries "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, productId, warehouseId);
    }

    static String requisitionLine(long product, long unit, String quantity) {
        return "{\"productId\":" + product + ",\"unitId\":" + unit + ",\"quantity\":" + quantity + "}";
    }

    static String requisition(List<String> lines) {
        return "{\"branchId\":1,\"documentDate\":\"" + DATE + "\",\"requiredDate\":\"2026-10-20\",\"lines\":["
                + String.join(",", lines) + "]}";
    }

    static String orderLine(long product, long unit, String quantity, String price, String discount, Long taxRate,
                            Long requisitionLine) {
        StringBuilder line = new StringBuilder("{\"productId\":" + product + ",\"unitId\":" + unit + ",\"quantity\":"
                + quantity + ",\"unitPrice\":" + price);
        if (discount != null) {
            line.append(",\"discountPercent\":").append(discount);
        }
        if (taxRate != null) {
            line.append(",\"taxRateId\":").append(taxRate);
        }
        if (requisitionLine != null) {
            line.append(",\"requisitionLineId\":").append(requisitionLine);
        }
        return line.append('}').toString();
    }

    static String order(long supplier, long warehouse, List<String> lines) {
        return "{\"branchId\":1,\"documentDate\":\"" + DATE + "\",\"supplierId\":" + supplier + ",\"warehouseId\":"
                + warehouse + ",\"expectedDate\":\"2026-10-15\",\"lines\":[" + String.join(",", lines) + "]}";
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
