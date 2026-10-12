package com.company.erp.inventory;

import static com.company.erp.support.ApiTestSupport.createUser;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Dữ liệu và thao tác dùng chung cho test tích hợp kho: tạo kho, hàng, lập / duyệt / ghi sổ chứng từ qua API thật,
 * kiểm tra số dư = tổng sổ nhật ký.
 */
abstract class InventoryIntegrationSupport {

    static final String DOCS = "/api/v1/inventory-documents";

    protected final MockMvc mockMvc;
    protected final JdbcTemplate jdbc;

    protected Session admin;
    protected Session manager;
    protected Session director;
    protected long unitId;
    protected long categoryId;

    protected InventoryIntegrationSupport(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUpSessions() throws Exception {
        admin = login(mockMvc, "admin");
        String managerName = unique("invm").toLowerCase();
        String directorName = unique("invd").toLowerCase();
        createUser(jdbc, managerName, "WAREHOUSE_MANAGER", "COMPANY", 1, 1, false);
        createUser(jdbc, directorName, "DIRECTOR", "COMPANY", 1, 1, false);
        manager = login(mockMvc, managerName);
        director = login(mockMvc, directorName);
        unitId = id("SELECT id FROM units WHERE company_id = 1 AND code = 'CAI'");
        categoryId = id("SELECT id FROM product_categories WHERE company_id = 1 AND code = 'VT'");
    }

    // ---- dữ liệu ----

    protected long warehouse(long branchId) throws Exception {
        String code = unique("W");
        MvcResult result = send(admin, post("/api/v1/warehouses"), "{\"branchId\":" + branchId + ",\"code\":\"" + code
                + "\",\"name\":\"Kho test " + code + "\",\"warehouseType\":\"NORMAL\"}", newKey())
                .andExpect(status().isCreated()).andReturn();
        return jsonValue(result, "$.data.id", Integer.class);
    }

    protected long product(boolean lot, boolean expiry, boolean serial) throws Exception {
        return product("CONSUMABLE", lot, expiry, serial);
    }

    protected long product(String productType, boolean lot, boolean expiry, boolean serial) throws Exception {
        String code = unique("P");
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Hàng " + code + "\",\"categoryId\":"
                + categoryId + ",\"productType\":\"" + productType + "\",\"baseUnitId\":" + unitId
                + ",\"lotTracked\":" + lot + ",\"expiryTracked\":" + expiry + ",\"serialTracked\":" + serial + "}";
        return jsonValue(send(admin, post("/api/v1/products"), body, newKey()).andExpect(status().isCreated()).andReturn(),
                "$.data.id", Integer.class);
    }

    protected String receiptLine(long product, int quantity, String lotNo, String expiry, List<String> serials) {
        StringBuilder line = new StringBuilder("{\"productId\":" + product + ",\"unitId\":" + unitId
                + ",\"quantity\":" + quantity + ",\"unitCost\":1000");
        if (lotNo != null) {
            line.append(",\"lotNo\":\"").append(lotNo).append("\",\"expiryDate\":\"").append(expiry).append('"');
        }
        if (serials != null) {
            line.append(",\"serialNumbers\":[\"").append(String.join("\",\"", serials)).append("\"]");
        }
        return line.append('}').toString();
    }

    protected String issueLine(long product, int quantity, Long lotId, String overrideReason) {
        return "{\"productId\":" + product + ",\"unitId\":" + unitId + ",\"quantity\":" + quantity
                + (lotId == null ? "" : ",\"lotId\":" + lotId)
                + (overrideReason == null ? "" : ",\"lotOverrideReason\":\"" + overrideReason + "\"") + "}";
    }

    protected static String document(String type, long warehouse, List<String> lines, String date) {
        return "{\"documentType\":\"" + type + "\",\"documentDate\":\"" + date + "\",\"warehouseId\":" + warehouse
                + ",\"reasonText\":\"Kiểm thử\",\"lines\":[" + String.join(",", lines) + "]}";
    }

    /** Chèn thêm trường đầu phiếu (ví dụ {@code "partnerId":5}) trước danh sách dòng. */
    protected static String withHeader(String document, String fields) {
        return document.replace("\"lines\"", fields + ",\"lines\"");
    }

    // ---- quy trình ----

    protected long create(Session session, String type, long warehouse, List<String> lines) throws Exception {
        return create(session, type, warehouse, lines, "2026-10-05");
    }

    protected long create(Session session, String type, long warehouse, List<String> lines, String date)
            throws Exception {
        return createDocument(session, document(type, warehouse, lines, date));
    }

    protected long createDocument(Session session, String body) throws Exception {
        MvcResult result = send(session, post(DOCS), body, newKey()).andExpect(status().isCreated()).andReturn();
        return jsonValue(result, "$.data.id", Integer.class);
    }

    protected long createAndPost(Session session, String type, long warehouse, List<String> lines) throws Exception {
        long id = create(session, type, warehouse, lines);
        submitApproveAndPost(session, id);
        return id;
    }

    protected void submitApproveAndPost(Session session, long id) throws Exception {
        submitAndApprove(id);
        action(session, id, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
    }

    protected void submit(long id) throws Exception {
        action(manager, id, "submit", null).andExpect(status().isOk());
    }

    protected void submitAndApprove(long id) throws Exception {
        submit(id);
        if ("SUBMITTED".equals(docStatus(id))) {
            approve(director, id);
        }
        assertThat(docStatus(id)).isEqualTo("APPROVED");
    }

    protected void approve(Session session, long id) throws Exception {
        action(session, id, "approve", "{}").andExpect(status().isOk());
    }

    protected long reverse(long id) throws Exception {
        MvcResult result = action(manager, id, "reverse", "{\"reason\":\"Ghi sai\",\"postingDate\":\"2026-10-09\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentType").value("PDK"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        return jsonValue(result, "$.data.id", Integer.class);
    }

    protected ResultActions action(Session session, long id, String action, String body) throws Exception {
        return actionWithKey(session, id, action, body, newKey());
    }

    protected ResultActions actionWithKey(Session session, long id, String action, String body, String key)
            throws Exception {
        MockHttpServletRequestBuilder request = as(session, post(DOCS + "/" + id + "/" + action))
                .header(IdempotentCommand.HEADER, key);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    // ---- kiểm tra ----

    protected String docStatus(long documentId) {
        return jdbc.queryForObject("SELECT status FROM inventory_documents WHERE id = ?", String.class, documentId);
    }

    protected BigDecimal onHand(long product, long warehouse) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_balances "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, product, warehouse);
    }

    protected BigDecimal onHand(long product, long warehouse, String qualityStatus) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_balances "
                + "WHERE product_id = ? AND warehouse_id = ? AND quality_status = ?", BigDecimal.class, product,
                warehouse, qualityStatus);
    }

    /** Số dư chi tiết = tổng sổ nhật ký theo từng khóa tồn; tổng (hàng, kho) = tổng số dư chi tiết. */
    protected void assertLedgerConsistent(long product) {
        List<Map<String, Object>> mismatches = jdbc.queryForList("""
                SELECT l.warehouse_id, l.location_id, l.lot_id, l.serial_id, l.quality_status, l.qty,
                       COALESCE(b.on_hand_quantity, 0) AS balance
                FROM (SELECT warehouse_id, location_id, lot_id, serial_id, quality_status,
                             SUM(CASE WHEN direction = 'IN' THEN quantity ELSE -quantity END) AS qty
                      FROM inventory_transactions WHERE product_id = ?
                      GROUP BY warehouse_id, location_id, lot_id, serial_id, quality_status) l
                LEFT JOIN inventory_balances b ON b.product_id = ? AND b.warehouse_id = l.warehouse_id
                     AND b.location_id = l.location_id AND b.lot_key = COALESCE(l.lot_id, 0)
                     AND b.serial_key = COALESCE(l.serial_id, 0) AND b.quality_status = l.quality_status
                WHERE l.qty <> COALESCE(b.on_hand_quantity, 0)
                """, product, product);
        assertThat(mismatches).isEmpty();
        List<Map<String, Object>> summaryMismatches = jdbc.queryForList("""
                SELECT s.warehouse_id FROM inventory_stock_summaries s
                WHERE s.product_id = ? AND s.on_hand_quantity <> (SELECT COALESCE(SUM(b.on_hand_quantity), 0)
                      FROM inventory_balances b WHERE b.product_id = s.product_id AND b.warehouse_id = s.warehouse_id)
                """, product);
        assertThat(summaryMismatches).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM inventory_balances WHERE on_hand_quantity < 0")).isZero();
    }

    protected long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    protected long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    protected ResultActions send(Session session, MockHttpServletRequestBuilder request, String body, String key)
            throws Exception {
        return mockMvc.perform(as(session, request).header(IdempotentCommand.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    protected static <T> T jsonValue(MvcResult result, String path, Class<T> type) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        if (type == Integer.class && value instanceof Number number) {
            return type.cast(number.intValue());
        }
        return type.cast(value);
    }
}
