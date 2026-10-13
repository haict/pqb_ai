package com.company.erp.finance;

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
 * Hỗ trợ test tích hợp công nợ, quỹ: phiên người dùng mẫu, tạo đối tượng / quỹ / công nợ đầu kỳ / chứng từ thu chi qua
 * API, khẳng định sổ phụ khớp số dư (số còn lại hóa đơn = sổ chi tiết gắn hóa đơn, trả trước = sổ chi tiết không gắn
 * hóa đơn, số dư quỹ = sổ quỹ, bút toán cân).
 */
abstract class FinanceIntegrationSupport {

    static final String DATE = "2026-10-02";
    static final String CASH_DOCS = "/api/v1/cash-documents";

    protected final MockMvc mockMvc;
    protected final JdbcTemplate jdbc;

    protected Session admin;
    protected Session clerk;
    protected Session chief;
    protected Session cashier;

    FinanceIntegrationSupport(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void loginUsers() throws Exception {
        admin = login(mockMvc, "admin");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
        cashier = login(mockMvc, "thuquy.hn");
    }

    long partner(String... roles) throws Exception {
        String code = unique("DT");
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Đối tác " + code
                + "\",\"partnerKind\":\"ORGANIZATION\",\"roles\":[\"" + String.join("\",\"", roles)
                + "\"],\"paymentTermDays\":0,\"taxCode\":\"0300000001\",\"address\":\"Hà Nội\""
                + ",\"contacts\":[{\"name\":\"Liên hệ\",\"primaryContact\":true}]}";
        return jsonId(send(admin, post("/api/v1/partners"), body, newKey()).andExpect(status().isCreated()).andReturn());
    }

    static String openingLine(long partner, String invoiceNo, String invoiceDate, String dueDate, String amount) {
        return "{\"partnerId\":" + partner + ",\"invoiceNo\":\"" + invoiceNo + "\",\"invoiceDate\":\"" + invoiceDate
                + "\",\"dueDate\":\"" + dueDate + "\",\"amount\":" + amount + "}";
    }

    /** Công nợ đầu kỳ đã ghi sổ. */
    long opening(String debtType, List<String> lines) throws Exception {
        String body = "{\"branchId\":1,\"debtType\":\"" + debtType + "\",\"documentDate\":\"2026-10-01\","
                + "\"lines\":[" + String.join(",", lines) + "]}";
        long id = jsonId(send(clerk, post("/api/v1/debt-openings"), body, newKey()).andExpect(status().isCreated())
                .andReturn());
        act(clerk, "/api/v1/debt-openings", id, "submit", null).andExpect(status().isOk());
        act(chief, "/api/v1/debt-openings", id, "approve", "{}").andExpect(status().isOk());
        act(clerk, "/api/v1/debt-openings", id, "post", null).andExpect(status().isOk());
        return id;
    }

    long cashAccount(String type, String glCode) throws Exception {
        String code = unique("Q");
        String body = "{\"branchId\":1,\"code\":\"" + code + "\",\"name\":\"Quỹ " + code + "\",\"accountType\":\""
                + type + "\",\"glAccountId\":" + glAccount(glCode) + ("BANK".equals(type)
                ? ",\"bankName\":\"Ngân hàng thử\",\"bankAccountNo\":\"" + code + "\"" : "") + "}";
        return jsonId(send(chief, post("/api/v1/cash-accounts"), body, newKey()).andExpect(status().isCreated())
                .andReturn());
    }

    /** Chứng từ thu chi đã gửi và ghi sổ (dưới ngưỡng duyệt). */
    long postedCash(String body) throws Exception {
        long id = jsonId(send(clerk, post(CASH_DOCS), body, newKey()).andExpect(status().isCreated()).andReturn());
        act(clerk, CASH_DOCS, id, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(cashier, CASH_DOCS, id, "post", null).andExpect(status().isOk());
        return id;
    }

    /** Nạp tiền vào quỹ tiền mặt bằng phiếu thu khác (Có 4111). */
    long fund(long cash, String amount) throws Exception {
        return postedCash(cashDoc("PT", "OTHER_RECEIPT", cash, null, null, null,
                List.of(counterLine("4111", amount))));
    }

    static String cashDoc(String type, String purpose, long cash, Long partner, String mode, String amount,
                          List<String> lines) {
        StringBuilder body = new StringBuilder("{\"branchId\":1,\"documentType\":\"" + type + "\",\"purpose\":\""
                + purpose + "\",\"documentDate\":\"" + DATE + "\",\"cashAccountId\":" + cash);
        if (partner != null) {
            body.append(",\"partnerId\":").append(partner);
        }
        if (mode != null) {
            body.append(",\"allocationMode\":\"").append(mode).append('"');
        }
        if (amount != null) {
            body.append(",\"amount\":").append(amount);
        }
        return body.append(",\"lines\":[").append(String.join(",", lines)).append("]}").toString();
    }

    String counterLine(String accountCode, String amount) {
        return "{\"accountId\":" + glAccount(accountCode) + ",\"amount\":" + amount + "}";
    }

    long glAccount(String code) {
        return count("SELECT id FROM chart_of_accounts WHERE company_id = 1 AND code = '" + code + "'");
    }

    Map<String, Object> item(long partner, String invoiceNo) {
        return jdbc.queryForMap("SELECT id, status, settled_amount, remaining_amount FROM debt_items "
                + "WHERE partner_id = ? AND invoice_no = ?", partner, invoiceNo);
    }

    long itemId(long partner, String invoiceNo) {
        return ((Number) item(partner, invoiceNo).get("id")).longValue();
    }

    BigDecimal remaining(long partner, String invoiceNo) {
        return (BigDecimal) item(partner, invoiceNo).get("remaining_amount");
    }

    BigDecimal prepayment(long partner, String debtType) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM debt_prepayments WHERE partner_id = ? "
                + "AND debt_type = ?", BigDecimal.class, partner, debtType);
    }

    /** Dòng bút toán gốc "N|C:tài khoản:số tiền"; khẳng định cân Nợ = Có. */
    List<String> journal(String sourceType, long sourceId) {
        List<String> lines = jdbc.queryForList("""
                SELECT CONCAT(IF(l.debit_amount > 0, 'N', 'C'), ':', a.code, ':',
                              CAST(l.debit_amount + l.credit_amount AS DECIMAL(19,0)))
                FROM journal_entry_lines l
                JOIN journal_entries e ON e.id = l.journal_entry_id
                JOIN chart_of_accounts a ON a.id = l.account_id
                WHERE e.source_type = ? AND e.source_id = ? AND e.reversal_of_id IS NULL
                ORDER BY l.line_no
                """, String.class, sourceType, sourceId);
        BigDecimal net = jdbc.queryForObject("SELECT COALESCE(SUM(l.debit_amount) - SUM(l.credit_amount), 0) FROM "
                + "journal_entry_lines l JOIN journal_entries e ON e.id = l.journal_entry_id WHERE e.source_type = ? "
                + "AND e.source_id = ?", BigDecimal.class, sourceType, sourceId);
        assertThat(net).isEqualByComparingTo("0");
        return lines;
    }

    void assertBalance(long cash, String expected) {
        BigDecimal balance = jdbc.queryForObject("SELECT balance FROM cash_accounts WHERE id = ?", BigDecimal.class,
                cash);
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(debit_amount - credit_amount), 0) FROM "
                + "cash_ledger_entries WHERE cash_account_id = ?", BigDecimal.class, cash);
        assertThat(balance).isEqualByComparingTo(expected);
        assertThat(ledger).isEqualByComparingTo(expected);
    }

    /**
     * Sổ phụ khớp số dư của đối tượng: Σ còn lại của khoản công nợ = sổ chi tiết gắn khoản công nợ; trả trước =
     * sổ chi tiết không gắn khoản công nợ (theo chiều giảm công nợ).
     */
    void assertDebtConsistent(long partnerId) {
        for (String type : List.of("RECEIVABLE", "PAYABLE", "ADVANCE")) {
            String natural = "PAYABLE".equals(type) ? "credit_amount - debit_amount" : "debit_amount - credit_amount";
            BigDecimal remaining = jdbc.queryForObject("SELECT COALESCE(SUM(remaining_amount), 0) FROM debt_items "
                    + "WHERE partner_id = ? AND debt_type = ? AND status <> 'REVERSED'", BigDecimal.class, partnerId,
                    type);
            BigDecimal itemLedger = jdbc.queryForObject("SELECT COALESCE(SUM(" + natural + "), 0) FROM "
                    + "debt_ledger_entries WHERE partner_id = ? AND debt_type = ? AND debt_item_id IS NOT NULL",
                    BigDecimal.class, partnerId, type);
            BigDecimal prepaymentLedger = jdbc.queryForObject("SELECT COALESCE(SUM(" + natural + "), 0) FROM "
                    + "debt_ledger_entries WHERE partner_id = ? AND debt_type = ? AND debt_item_id IS NULL",
                    BigDecimal.class, partnerId, type);
            assertThat(remaining).as("còn lại " + type).isEqualByComparingTo(itemLedger);
            assertThat(prepayment(partnerId, type).negate()).as("trả trước " + type)
                    .isEqualByComparingTo(prepaymentLedger);
        }
    }

    long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
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

    static BigDecimal amount(MvcResult result, String path) throws Exception {
        return new BigDecimal(JsonPath.read(result.getResponse().getContentAsString(), path).toString());
    }
}
