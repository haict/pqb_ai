package com.company.erp.finance;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Giai đoạn 4B - sổ công nợ trên MySQL thật: công nợ đầu kỳ theo hóa đơn (BR-MIG-01), hạn thanh toán (BR-AR-02),
 * tuổi nợ (BR-AR-05), sổ chi tiết công nợ, đảo (AT-09), khóa kỳ công nợ (AT-11), phân quyền và phạm vi (AT-10), chống
 * xử lý trùng (AT-13). Sau mỗi kịch bản: số còn lại của khoản công nợ = số dư sổ chi tiết.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class DebtIntegrationTest {

    private static final String OPENINGS = "/api/v1/debt-openings";
    private static final String POSTING_DATE = "2026-10-01";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final BusinessCalendar calendar;

    private Session admin;
    private Session clerk;
    private Session chief;

    @Autowired
    DebtIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, BusinessCalendar calendar) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.calendar = calendar;
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = login(mockMvc, "admin");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
    }

    @Test
    @DisplayName("BR-MIG-01, BR-AR-02, BR-AR-05: công nợ đầu kỳ qua duyệt, ghi sổ thành khoản công nợ theo hóa đơn; hạn mặc định theo số ngày nợ; tuổi nợ, sổ chi tiết")
    void openingReceivables() throws Exception {
        long customer = partner("CUSTOMER", 30);
        LocalDate today = calendar.today();
        String overdue = unique("HD");
        String current = unique("HD");
        long document = create(clerk, "RECEIVABLE", List.of(
                line(customer, overdue, today.minusDays(100), today.minusDays(45), "1000000"),
                line(customer, current, today.minusDays(10), null, "2500000")));
        mockMvc.perform(as(clerk, get(OPENINGS + "/" + document))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(3500000))
                .andExpect(jsonPath("$.data.lines[1].dueDate").value(today.plusDays(20).toString()));

        action(clerk, document, "submit", null).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        action(clerk, document, "approve", "{}").andExpect(status().isForbidden());
        action(chief, document, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        String postKey = newKey();
        actionWithKey(clerk, document, "post", null, postKey).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        actionWithKey(clerk, document, "post", null, postKey).andExpect(status().isOk());
        action(clerk, document, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        assertThat(count("SELECT COUNT(*) FROM debt_items WHERE partner_id = " + customer)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM debt_ledger_entries WHERE partner_id = " + customer
                + " AND entry_type = 'OPENING' AND debit_amount > 0")).isEqualTo(2);

        MvcResult items = mockMvc.perform(as(clerk, get("/api/v1/debt-items")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(customer))
                        .param("overdueOnly", "true"))
                .andExpect(status().isOk()).andReturn();
        List<String> overdueInvoices = json(items, "$.data.items[*].invoiceNo");
        assertThat(overdueInvoices).containsExactly(overdue);
        assertThat(((Number) JsonPath.read(items.getResponse().getContentAsString(), "$.data.items[0].overdueDays"))
                .intValue()).isEqualTo(45);

        MvcResult aging = mockMvc.perform(as(clerk, get("/api/v1/debt-items/aging")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(customer)))
                .andExpect(status().isOk()).andReturn();
        assertThat(amount(aging, "$.data.rows.items[0].notDue")).isEqualByComparingTo("2500000");
        assertThat(amount(aging, "$.data.rows.items[0].bucket2")).isEqualByComparingTo("1000000");
        assertThat(amount(aging, "$.data.rows.items[0].total")).isEqualByComparingTo("3500000");
        List<Integer> buckets = json(aging, "$.data.bucketDays");
        assertThat(buckets).containsExactly(30, 60, 90);

        MvcResult ledger = mockMvc.perform(as(clerk, get("/api/v1/debt-ledger")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(customer))
                        .param("dateFrom", "2026-01-01").param("dateTo", "2026-12-31"))
                .andExpect(status().isOk()).andReturn();
        assertThat(amount(ledger, "$.data.openingBalance")).isEqualByComparingTo("0");
        assertThat(amount(ledger, "$.data.closingBalance")).isEqualByComparingTo("3500000");
        assertConsistent(customer);

        // Hóa đơn đã có công nợ; đối tượng không phải khách hàng.
        send(clerk, post(OPENINGS), body("RECEIVABLE", List.of(line(customer, overdue, today.minusDays(5), null,
                "1"))), newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].invoiceNo"));
        long supplier = partner("SUPPLIER", 15);
        send(clerk, post(OPENINGS), body("RECEIVABLE", List.of(line(supplier, unique("HD"), today, null, "1"))),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].partnerId"));
    }

    @Test
    @DisplayName("AT-09, AT-13: đảo công nợ đầu kỳ chỉ người có quyền duyệt, bắt buộc lý do; khoản công nợ và sổ chi tiết về 0")
    void reverseOpening() throws Exception {
        long supplier = partner("SUPPLIER", 15);
        long document = create(clerk, "PAYABLE", List.of(line(supplier, unique("HDM"), LocalDate.parse("2026-09-01"),
                null, "800000")));
        submitApproveAndPost(document);
        assertThat(jdbc.queryForObject("SELECT due_date FROM debt_items WHERE partner_id = ?", LocalDate.class,
                supplier)).isEqualTo(LocalDate.parse("2026-09-16"));
        assertThat(jdbc.queryForObject("SELECT credit_amount FROM debt_ledger_entries WHERE partner_id = ?",
                BigDecimal.class, supplier)).isEqualByComparingTo("800000");

        action(clerk, document, "reverse", "{\"reason\":\"Nhập sai\"}").andExpect(status().isForbidden());
        action(chief, document, "reverse", "{\"reason\":\" \"}").andExpect(status().isBadRequest());
        action(chief, document, "reverse", "{\"reason\":\"Nhập sai số tiền\",\"postingDate\":\"2026-10-05\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVERSED"));
        action(chief, document, "reverse", "{\"reason\":\"Lần hai\"}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM debt_items WHERE partner_id = ?", String.class, supplier))
                .isEqualTo("REVERSED");
        assertThat(jdbc.queryForObject("SELECT SUM(credit_amount) - SUM(debit_amount) FROM debt_ledger_entries "
                + "WHERE partner_id = ?", BigDecimal.class, supplier)).isEqualByComparingTo("0");
        assertConsistent(supplier);
    }

    @Test
    @DisplayName("AT-11: kỳ công nợ đã khóa thì không ghi sổ, không để lại khoản công nợ")
    void lockedDebtPeriod() throws Exception {
        send(chief, post("/api/v1/fiscal-periods/generate"), "{\"companyId\":1,\"year\":2066}", newKey())
                .andExpect(status().isOk());
        long january = count("SELECT id FROM fiscal_periods WHERE company_id = 1 AND period_year = 2066 "
                + "AND period_month = 1");
        send(chief, post("/api/v1/fiscal-periods/" + january + "/lock"), "{\"module\":\"DEBT\",\"branchIds\":[1]}",
                newKey()).andExpect(status().isOk());
        long customer = partner("CUSTOMER", 0);
        String body = body("RECEIVABLE", List.of(line(customer, unique("HD"), LocalDate.parse("2065-12-20"), null,
                "100"))).replace("\"documentDate\":\"" + POSTING_DATE + "\"", "\"documentDate\":\"2066-01-05\"");
        long document = jsonId(send(clerk, post(OPENINGS), body, newKey()).andExpect(status().isCreated()).andReturn());
        action(clerk, document, "submit", null).andExpect(status().isOk());
        action(chief, document, "approve", "{}").andExpect(status().isOk());
        action(clerk, document, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_LOCKED"));
        assertThat(jdbc.queryForObject("SELECT status FROM debt_opening_documents WHERE id = ?", String.class,
                document)).isEqualTo("APPROVED");
        assertThat(count("SELECT COUNT(*) FROM debt_items WHERE partner_id = " + customer)).isZero();
    }

    @Test
    @DisplayName("AT-10: không có quyền công nợ 403; chứng từ chi nhánh khác 404; sửa đồng thời 409")
    void accessAndConcurrency() throws Exception {
        long customer = partner("CUSTOMER", 0);
        long document = create(clerk, "RECEIVABLE", List.of(line(customer, unique("HD"), LocalDate.parse("2026-09-20"),
                null, "500000")));
        mockMvc.perform(as(login(mockMvc, "kho.hcm"), get(OPENINGS + "/" + document)))
                .andExpect(status().isForbidden());
        String otherBranch = unique("kthcm").toLowerCase();
        createUser(jdbc, otherBranch, "AR_ACCOUNTANT", "BRANCH", 2, 2, false);
        mockMvc.perform(as(login(mockMvc, otherBranch), get(OPENINGS + "/" + document)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(login(mockMvc, otherBranch), get("/api/v1/debt-items")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));

        String update = body("RECEIVABLE", List.of(line(customer, unique("HD"), LocalDate.parse("2026-09-21"), null,
                "600000"))).replace("}]", "}],\"version\":0");
        mockMvc.perform(as(clerk, put(OPENINGS + "/" + document)).contentType(MediaType.APPLICATION_JSON)
                .content(update.replace("\"version\":0", "\"version\":" + version(document))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(600000));
        mockMvc.perform(as(clerk, put(OPENINGS + "/" + document)).contentType(MediaType.APPLICATION_JSON)
                .content(update.replace("\"version\":0", "\"version\":" + (version(document) - 1))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // ---- hỗ trợ ----

    private long partner(String role, int termDays) throws Exception {
        String code = unique("DT");
        String termField = "SUPPLIER".equals(role) ? "supplierPaymentTermDays" : "paymentTermDays";
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Đối tác " + code
                + "\",\"partnerKind\":\"ORGANIZATION\",\"roles\":[\"" + role + "\"],\"" + termField + "\":" + termDays
                + ",\"contacts\":[{\"name\":\"Liên hệ\",\"primaryContact\":true}]}";
        return jsonId(send(admin, post("/api/v1/partners"), body, newKey()).andExpect(status().isCreated()).andReturn());
    }

    private static String line(long partner, String invoiceNo, LocalDate invoiceDate, LocalDate dueDate,
                               String amount) {
        return "{\"partnerId\":" + partner + ",\"invoiceNo\":\"" + invoiceNo + "\",\"invoiceDate\":\"" + invoiceDate
                + "\"" + (dueDate == null ? "" : ",\"dueDate\":\"" + dueDate + "\"") + ",\"amount\":" + amount + "}";
    }

    private static String body(String debtType, List<String> lines) {
        return "{\"branchId\":1,\"debtType\":\"" + debtType + "\",\"documentDate\":\"" + POSTING_DATE
                + "\",\"description\":\"Số dư chuyển từ MISA\",\"lines\":[" + String.join(",", lines) + "]}";
    }

    private long create(Session session, String debtType, List<String> lines) throws Exception {
        return jsonId(send(session, post(OPENINGS), body(debtType, lines), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn());
    }

    private void submitApproveAndPost(long document) throws Exception {
        action(clerk, document, "submit", null).andExpect(status().isOk());
        action(chief, document, "approve", "{}").andExpect(status().isOk());
        action(clerk, document, "post", null).andExpect(status().isOk());
    }

    /** Số còn lại của khoản công nợ chưa đảo = số dư sổ chi tiết của đối tượng (theo chiều tự nhiên). */
    private void assertConsistent(long partnerId) {
        BigDecimal remaining = jdbc.queryForObject("SELECT COALESCE(SUM(remaining_amount), 0) FROM debt_items "
                + "WHERE partner_id = ? AND status <> 'REVERSED'", BigDecimal.class, partnerId);
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN debt_type = 'RECEIVABLE' "
                + "THEN debit_amount - credit_amount ELSE credit_amount - debit_amount END), 0) "
                + "FROM debt_ledger_entries WHERE partner_id = ?", BigDecimal.class, partnerId);
        assertThat(remaining).isEqualByComparingTo(ledger);
    }

    private int version(long document) {
        return jdbc.queryForObject("SELECT version FROM debt_opening_documents WHERE id = ?", Integer.class, document);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private ResultActions action(Session session, long id, String action, String body) throws Exception {
        return actionWithKey(session, id, action, body, newKey());
    }

    private ResultActions actionWithKey(Session session, long id, String action, String body, String key)
            throws Exception {
        MockHttpServletRequestBuilder request = as(session, post(OPENINGS + "/" + id + "/" + action))
                .header(IdempotentCommand.HEADER, key);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions send(Session session, MockHttpServletRequestBuilder request, String body, String key)
            throws Exception {
        return mockMvc.perform(as(session, request).header(IdempotentCommand.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    private static long jsonId(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static BigDecimal amount(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return new BigDecimal(value.toString());
    }
}
