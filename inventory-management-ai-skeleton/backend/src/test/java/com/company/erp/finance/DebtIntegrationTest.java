package com.company.erp.finance;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Giai đoạn 4B - sổ công nợ trên MySQL thật: công nợ đầu kỳ theo đối tượng và hạn thanh toán, không qua duyệt (BR-MIG-01,
 * D-60),
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
    @DisplayName("BR-MIG-01, D-60, BR-AR-05: công nợ đầu kỳ ghi sổ thẳng từ nháp (không duyệt) thành khoản công nợ theo đối tượng, hạn thanh toán; tuổi nợ, sổ chi tiết")
    void openingReceivables() throws Exception {
        long customer = partner("CUSTOMER", 30);
        LocalDate today = calendar.today();
        long document = create(clerk, "RECEIVABLE", List.of(
                line(customer, today.minusDays(45), "1000000"),
                line(customer, today.plusDays(20), "2500000")));
        mockMvc.perform(as(clerk, get(OPENINGS + "/" + document))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(3500000))
                .andExpect(jsonPath("$.data.lines[0].invoiceNo").doesNotExist());

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
        List<String> overdueDueDates = json(items, "$.data.items[*].dueDate");
        assertThat(overdueDueDates).containsExactly(today.minusDays(45).toString());
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

        // Đối tượng không phải khách hàng.
        long supplier = partner("SUPPLIER", 15);
        send(clerk, post(OPENINGS), body("RECEIVABLE", List.of(line(supplier, today, "1"))),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].partnerId"));
    }

    @Test
    @DisplayName("AT-09, AT-13, D-60: đảo công nợ đầu kỳ chỉ người có quyền ghi sổ, bắt buộc lý do; hạn trống = ngày chứng từ + số ngày nợ; khoản công nợ và sổ chi tiết về 0")
    void reverseOpening() throws Exception {
        long supplier = partner("SUPPLIER", 15);
        long document = create(clerk, "PAYABLE", List.of(line(supplier, null, "500000"),
                line(supplier, LocalDate.parse("2026-11-30"), "300000")));
        postOpening(document);
        assertThat(jdbc.queryForList("SELECT due_date FROM debt_items WHERE partner_id = ? ORDER BY id",
                LocalDate.class, supplier)).containsExactly(LocalDate.parse("2026-10-16"), LocalDate.parse("2026-11-30"));
        assertThat(jdbc.queryForObject("SELECT SUM(credit_amount) FROM debt_ledger_entries WHERE partner_id = ?",
                BigDecimal.class, supplier)).isEqualByComparingTo("800000");

        String director = unique("gd").toLowerCase();
        createUser(jdbc, director, "DIRECTOR", "COMPANY", 1, 1, false);
        action(login(mockMvc, director), document, "reverse", "{\"reason\":\"Nhập sai\"}")
                .andExpect(status().isForbidden());
        action(chief, document, "reverse", "{\"reason\":\" \"}").andExpect(status().isBadRequest());
        action(chief, document, "reverse", "{\"reason\":\"Nhập sai số tiền\",\"postingDate\":\"2026-10-05\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVERSED"));
        action(chief, document, "reverse", "{\"reason\":\"Lần hai\"}").andExpect(status().isConflict());
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM debt_items WHERE partner_id = ?", String.class,
                supplier)).containsExactly("REVERSED");
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
        String body = body("RECEIVABLE", List.of(line(customer, null, "100"))).replace("\"documentDate\":\"" + POSTING_DATE + "\"", "\"documentDate\":\"2066-01-05\"");
        long document = jsonId(send(clerk, post(OPENINGS), body, newKey()).andExpect(status().isCreated()).andReturn());
        action(clerk, document, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_LOCKED"));
        assertThat(jdbc.queryForObject("SELECT status FROM debt_opening_documents WHERE id = ?", String.class,
                document)).isEqualTo("DRAFT");
        assertThat(count("SELECT COUNT(*) FROM debt_items WHERE partner_id = " + customer)).isZero();
    }

    @Test
    @DisplayName("AT-10: không có quyền công nợ 403; chứng từ chi nhánh khác 404; sửa đồng thời 409")
    void accessAndConcurrency() throws Exception {
        long customer = partner("CUSTOMER", 0);
        long document = create(clerk, "RECEIVABLE", List.of(line(customer, LocalDate.parse("2026-09-20"), "500000")));
        mockMvc.perform(as(login(mockMvc, "kho.hcm"), get(OPENINGS + "/" + document)))
                .andExpect(status().isForbidden());
        String otherBranch = unique("kthcm").toLowerCase();
        createUser(jdbc, otherBranch, "AR_ACCOUNTANT", "BRANCH", 2, 2, false);
        mockMvc.perform(as(login(mockMvc, otherBranch), get(OPENINGS + "/" + document)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(login(mockMvc, otherBranch), get("/api/v1/debt-items")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));

        String update = body("RECEIVABLE", List.of(line(customer, LocalDate.parse("2026-09-21"), "600000"))).replace("}]", "}],\"version\":0");
        mockMvc.perform(as(clerk, put(OPENINGS + "/" + document)).contentType(MediaType.APPLICATION_JSON)
                .content(update.replace("\"version\":0", "\"version\":" + version(document))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(600000));
        mockMvc.perform(as(clerk, put(OPENINGS + "/" + document)).contentType(MediaType.APPLICATION_JSON)
                .content(update.replace("\"version\":0", "\"version\":" + (version(document) - 1))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // ---- hỗ trợ ----

    @Test
    @DisplayName("BR-MIG-01, BR-UX-05: đọc dòng công nợ đầu kỳ từ Excel để điền form; lỗi theo dòng; không lưu chứng từ")
    void importLines() throws Exception {
        mockMvc.perform(as(clerk, get(OPENINGS + "/line-import-template"))).andExpect(status().isOk());
        long customer = partner("CUSTOMER", 30);
        long supplier = partner("SUPPLIER", 30);
        String customerCode = partnerCode(customer);
        long documentsBefore = count("SELECT COUNT(*) FROM debt_opening_documents");

        importLines(workbookOf(List.of(
                new String[] {partnerCode(supplier), "", "1000"},
                new String[] {customerCode, "31-12-2026", "0"},
                new String[] {"KHONG_CO_MA", "", "1000"})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("rows[2].partnerId", "rows[3].dueDate",
                        "rows[3].amount", "rows[4].partnerId")));

        importLines(workbookOf(List.of(
                new String[] {customerCode.toLowerCase(), "", "1,500,000", "Số dư MISA"},
                new String[] {customerCode, "30/09/2026", "250000.5"})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.lines[0].partnerId").value(customer))
                .andExpect(jsonPath("$.data.lines[0].dueDate").doesNotExist())
                .andExpect(jsonPath("$.data.lines[0].amount").value(1500000))
                .andExpect(jsonPath("$.data.lines[0].note").value("Số dư MISA"))
                .andExpect(jsonPath("$.data.lines[1].dueDate").value("2026-09-30"));
        assertThat(count("SELECT COUNT(*) FROM debt_opening_documents")).isEqualTo(documentsBefore);

        mockMvc.perform(as(login(mockMvc, "kho.hcm"), multipart(OPENINGS + "/line-import")
                        .file(new MockMultipartFile("file", "cong-no.xlsx", "application/octet-stream",
                                workbookOf(List.<String[]>of(new String[] {customerCode, "", "1"})))))
                        .param("companyId", "1").param("debtType", "RECEIVABLE"))
                .andExpect(status().isForbidden());
    }

    private ResultActions importLines(byte[] content) throws Exception {
        return mockMvc.perform(as(clerk, multipart(OPENINGS + "/line-import")
                        .file(new MockMultipartFile("file", "cong-no.xlsx", "application/octet-stream", content)))
                .param("companyId", "1").param("debtType", "RECEIVABLE"));
    }

    private static byte[] workbookOf(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Cong no dau ky");
            sheet.createRow(0).createCell(0).setCellValue("Mã đối tượng*");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows.get(r).length; c++) {
                    row.createCell(c).setCellValue(rows.get(r)[c]);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String partnerCode(long partnerId) {
        return jdbc.queryForObject("SELECT code FROM partners WHERE id = ?", String.class, partnerId);
    }

    private long partner(String role, int termDays) throws Exception {
        String code = unique("DT");
        String termField = "SUPPLIER".equals(role) ? "supplierPaymentTermDays" : "paymentTermDays";
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Đối tác " + code
                + "\",\"partnerKind\":\"ORGANIZATION\",\"roles\":[\"" + role + "\"],\"" + termField + "\":" + termDays
                + ",\"taxCode\":\"0300000001\",\"address\":\"Hà Nội\",\"contacts\":[{\"name\":\"Liên hệ\",\"primaryContact\":true}]}";
        return jsonId(send(admin, post("/api/v1/partners"), body, newKey()).andExpect(status().isCreated()).andReturn());
    }

    private static String line(long partner, LocalDate dueDate, String amount) {
        return "{\"partnerId\":" + partner + (dueDate == null ? "" : ",\"dueDate\":\"" + dueDate + "\"")
                + ",\"amount\":" + amount + "}";
    }

    private static String body(String debtType, List<String> lines) {
        return "{\"branchId\":1,\"debtType\":\"" + debtType + "\",\"documentDate\":\"" + POSTING_DATE
                + "\",\"description\":\"Số dư chuyển từ MISA\",\"lines\":[" + String.join(",", lines) + "]}";
    }

    private long create(Session session, String debtType, List<String> lines) throws Exception {
        return jsonId(send(session, post(OPENINGS), body(debtType, lines), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn());
    }

    private void postOpening(long document) throws Exception {
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
