package com.company.erp.accounting;

import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.JournalEntryRef;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Giai đoạn 4A - kế toán lõi trên MySQL thật: hệ thống tài khoản dạng cây và nhập Excel (BR-ACC-01), quy tắc hạch
 * toán cấu hình (BR-ACC-02, ADR-11), ghi / đảo bút toán cân Nợ = Có (BR-ACC-03, AT-09), khóa kỳ kế toán (AT-11),
 * chống bút toán trùng (AT-13), phân quyền (AT-10).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class AccountingIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String RULES = "/api/v1/posting-rules";
    private static final String DATE = "2026-10-05";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final JournalPostingService journals;
    private final TransactionTemplate transactions;

    private Session accountant;
    private long customer;

    @Autowired
    AccountingIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, JournalPostingService journals,
                              TransactionTemplate transactions) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.journals = journals;
        this.transactions = transactions;
    }

    @BeforeEach
    void setUp() throws Exception {
        accountant = login(mockMvc, "ketoantruong");
        customer = jdbc.queryForObject("SELECT id FROM partners WHERE company_id = 1 AND code = 'KH_BV01'",
                Long.class);
    }

    @Test
    @DisplayName("BR-ACC-01: tài khoản dạng cây; tài khoản cha thành tổng hợp; mã con theo mã cha; trùng mã 409; sai quyền 403")
    void chartOfAccounts() throws Exception {
        String parentCode = unique("Z");
        long parent = account(parentCode, null, "DEBIT", false);
        long child = account(parentCode + "1", parent, "DEBIT", false);
        assertThat(postable(parent)).isFalse();
        assertThat(postable(child)).isTrue();
        assertThat(jdbc.queryForObject("SELECT account_level FROM chart_of_accounts WHERE id = ?", Integer.class,
                child)).isEqualTo(2);

        send(accountant, post(ACCOUNTS), accountBody(unique("Y"), parent, "DEBIT", false))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code"));
        send(accountant, post(ACCOUNTS), accountBody(parentCode, null, "DEBIT", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        send(login(mockMvc, "ketoancongno"), post(ACCOUNTS), accountBody(unique("Z"), null, "DEBIT", false))
                .andExpect(status().isForbidden());

        send(accountant, patch(ACCOUNTS + "/" + parent + "/status"),
                "{\"status\":\"INACTIVE\",\"version\":" + version(parent) + ",\"reason\":\"Thử\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_IN_USE"));

        MvcResult tree = mockMvc.perform(as(login(mockMvc, "ketoancongno"), get(ACCOUNTS + "/tree"))
                .param("companyId", "1")).andExpect(status().isOk()).andReturn();
        List<String> codes = json(tree, "$.data[*].code");
        assertThat(codes).contains("1111", "131", parentCode, parentCode + "1");
        assertThat(codes.indexOf(parentCode)).isLessThan(codes.indexOf(parentCode + "1"));
    }

    @Test
    @DisplayName("BR-ACC-01, BR-MIG-01: nhập hệ thống tài khoản từ Excel - tất cả hoặc không, lỗi theo dòng")
    void importAccounts() throws Exception {
        String root = unique("X");
        byte[] invalid = workbook(List.of(
                new String[] {root, "Gốc", "", "NO", ""},
                new String[] {root + "1", "Con", root, "SAI", ""},
                new String[] {unique("W"), "Không có cha", root + "9", "CO", "C"}));
        importFile(invalid).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems(
                        "rows[3].balanceSide", "rows[4].parentCode")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chart_of_accounts WHERE code LIKE ?", Integer.class,
                root + "%")).isZero();

        byte[] valid = workbook(List.of(
                new String[] {root + "11", "Cháu", root + "1", "LT", "C"},
                new String[] {root, "Gốc", "", "NO", ""},
                new String[] {root + "1", "Con", root, "CO", "K"}));
        importFile(valid).andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(3));
        Map<String, Object> grandChild = jdbc.queryForMap("SELECT account_level, balance_side, partner_required, "
                + "postable FROM chart_of_accounts WHERE code = ?", root + "11");
        assertThat(grandChild).containsEntry("account_level", 3).containsEntry("balance_side", "BOTH");
        assertThat((Boolean) grandChild.get("partner_required")).isTrue();
        assertThat(postable(id("SELECT id FROM chart_of_accounts WHERE code = '" + root + "1'"))).isFalse();
    }

    @Test
    @DisplayName("BR-ACC-02, 03, ADR-11: ghi bút toán theo quy tắc (vế do chứng từ truyền, quy tắc cụ thể thắng), cân Nợ = Có; ghi lại 409; đảo đổi vế")
    void postAndReverse() throws Exception {
        String code = unique("Q");
        long cash = account(code + "A", null, "DEBIT", false);
        long receivable = account(code + "B", null, "BOTH", true);
        long revenue = account(code + "C", null, "CREDIT", false);
        long reagentRevenue = account(code + "D", null, "CREDIT", false);
        String transaction = "TEST_" + code;
        rule(transaction, null, null, receivable, null);
        rule(transaction + "_SALE", null, receivable, revenue, null);
        rule(transaction + "_SALE", "REAGENT", receivable, reagentRevenue, null);

        long sourceId = System.nanoTime();
        JournalEntryRef ref = inTransaction(() -> journals.post(request(sourceId, LocalDate.parse(DATE), List.of(
                line(transaction, "1500000", null, cash, customer),
                line(transaction + "_SALE", "200000", "CONSUMABLE", null, customer),
                line(transaction + "_SALE", "300000", "REAGENT", null, customer)))));
        assertThat(ref.entryNo()).startsWith("BT-");
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT account_id, debit_amount, credit_amount "
                + "FROM journal_entry_lines WHERE journal_entry_id = ? ORDER BY line_no", ref.id());
        assertThat(lines).hasSize(6);
        assertThat(lines.get(0)).containsEntry("account_id", cash);
        assertThat(lines.get(1)).containsEntry("account_id", receivable);
        assertThat(lines.get(3)).containsEntry("account_id", revenue);
        assertThat(lines.get(5)).containsEntry("account_id", reagentRevenue);
        assertBalanced(ref.id());
        assertThat(jdbc.queryForObject("SELECT total_amount FROM journal_entries WHERE id = ?", BigDecimal.class,
                ref.id())).isEqualByComparingTo("2000000");

        assertCode(ErrorCode.DUPLICATE_DOCUMENT, () -> journals.post(request(sourceId, LocalDate.parse(DATE),
                List.of(line(transaction, "1", null, cash, customer)))));

        JournalEntryRef reversal = inTransaction(() -> journals.reverse(new ReversalRequest(1L, "PNK", sourceId,
                LocalDate.parse("2026-10-09"), "Đảo thử", null)));
        assertBalanced(reversal.id());
        assertThat(jdbc.queryForObject("SELECT status FROM journal_entries WHERE id = ?", String.class, ref.id()))
                .isEqualTo("REVERSED");
        assertThat(jdbc.queryForObject("SELECT SUM(debit_amount) - SUM(credit_amount) FROM journal_entry_lines l "
                + "JOIN journal_entries e ON e.id = l.journal_entry_id WHERE e.source_type = 'PNK' AND e.source_id = ? "
                + "AND l.account_id = ?", BigDecimal.class, sourceId, cash)).isEqualByComparingTo("0");
        assertCode(ErrorCode.INVALID_DOCUMENT_STATE, () -> journals.reverse(new ReversalRequest(1L, "PNK", sourceId,
                LocalDate.parse("2026-10-09"), "Lần hai", null)));

        // Tài khoản đã phát sinh hạch toán không thêm được tài khoản con (BR-ACC-01).
        send(accountant, post(ACCOUNTS), accountBody(code + "A1", cash, "DEBIT", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_IN_USE"));

        MvcResult bySource = mockMvc.perform(as(login(mockMvc, "ketoancongno"), get("/api/v1/journal-entries/by-source"))
                        .param("companyId", "1").param("sourceType", "PNK").param("sourceId", String.valueOf(sourceId)))
                .andExpect(status().isOk()).andReturn();
        List<Object> entries = json(bySource, "$.data");
        assertThat(entries).hasSize(2);
        mockMvc.perform(as(accountant, get("/api/v1/journal-entries/" + ref.id()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reversedBy.id").value(reversal.id().intValue()))
                .andExpect(jsonPath("$.data.lines[0].accountCode").value(code + "A"));
        mockMvc.perform(as(login(mockMvc, "kho.hcm"), get("/api/v1/journal-entries/" + ref.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BR-ACC-02, 03: thiếu quy tắc, quy tắc thiếu vế, thiếu đối tượng, tài khoản tổng hợp - từ chối, không ghi gì")
    void rejectsInvalidPostings() throws Exception {
        String code = unique("R");
        long cash = account(code + "A", null, "DEBIT", false);
        long receivable = account(code + "B", null, "BOTH", true);
        String transaction = "TEST_" + code;
        rule(transaction, null, null, receivable, "2026-10-01");
        long before = count();

        assertCode(ErrorCode.POSTING_RULE_NOT_FOUND, () -> journals.post(request(System.nanoTime(),
                LocalDate.parse(DATE), List.of(line("KHONG_CO_" + code, "100", null, cash, customer)))));
        assertCode(ErrorCode.POSTING_RULE_INCOMPLETE, () -> journals.post(request(System.nanoTime(),
                LocalDate.parse(DATE), List.of(line(transaction, "100", null, null, customer)))));
        assertCode(ErrorCode.ACCOUNT_NOT_POSTABLE, () -> journals.post(request(System.nanoTime(),
                LocalDate.parse(DATE), List.of(line(transaction, "100", null, cash, null)))));
        // Quy tắc chưa hiệu lực vào ngày hạch toán.
        assertCode(ErrorCode.POSTING_RULE_NOT_FOUND, () -> journals.post(request(System.nanoTime(),
                LocalDate.parse("2026-09-30"), List.of(line(transaction, "100", null, cash, customer)))));

        long parent = account(code + "P", null, "DEBIT", false);
        long child = account(code + "P1", parent, "DEBIT", false);
        assertThat(child).isPositive();
        assertCode(ErrorCode.ACCOUNT_NOT_POSTABLE, () -> journals.post(request(System.nanoTime(),
                LocalDate.parse(DATE), List.of(line(transaction, "100", null, parent, customer)))));
        send(accountant, post(RULES), ruleBody(transaction + "_X", null, parent, receivable, DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("debitAccountId"));
        send(accountant, post(RULES), ruleBody(transaction, null, null, receivable, "2026-10-01"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        assertThat(count()).isEqualTo(before);
    }

    @Test
    @DisplayName("AT-11: kỳ kế toán đã khóa thì không ghi bút toán")
    void lockedAccountingPeriod() throws Exception {
        send(accountant, post("/api/v1/fiscal-periods/generate"), "{\"companyId\":1,\"year\":2064}")
                .andExpect(status().isOk());
        long january = id("SELECT id FROM fiscal_periods WHERE company_id = 1 AND period_year = 2064 "
                + "AND period_month = 1");
        for (String module : List.of("INVENTORY", "ACCOUNTING")) {
            send(accountant, post("/api/v1/fiscal-periods/" + january + "/lock"),
                    "{\"module\":\"" + module + "\",\"branchIds\":[1]}").andExpect(status().isOk());
        }
        String code = unique("L");
        long cash = account(code + "A", null, "DEBIT", false);
        long revenue = account(code + "B", null, "CREDIT", false);
        String transaction = "TEST_" + code;
        send(accountant, post(RULES), ruleBody(transaction, null, cash, revenue, "2064-01-01"))
                .andExpect(status().isCreated());
        long before = count();
        assertCode(ErrorCode.PERIOD_LOCKED, () -> journals.post(request(System.nanoTime(),
                LocalDate.parse("2064-01-10"), List.of(line(transaction, "100", null, null, null)))));
        assertThat(count()).isEqualTo(before);
    }

    // ---- hỗ trợ ----

    private long account(String code, Long parentId, String side, boolean partnerRequired) throws Exception {
        MvcResult result = send(accountant, post(ACCOUNTS), accountBody(code, parentId, side, partnerRequired))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static String accountBody(String code, Long parentId, String side, boolean partnerRequired) {
        return "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"TK " + code + "\",\"parentId\":" + parentId
                + ",\"balanceSide\":\"" + side + "\",\"partnerRequired\":" + partnerRequired + "}";
    }

    private void rule(String transaction, String productType, Long debit, Long credit, String validFrom)
            throws Exception {
        send(accountant, post(RULES), ruleBody(transaction, productType, debit, credit,
                validFrom == null ? "2026-01-01" : validFrom)).andExpect(status().isCreated());
    }

    private static String ruleBody(String transaction, String productType, Long debit, Long credit,
                                   String validFrom) {
        return "{\"companyId\":1,\"documentType\":\"PNK\",\"transactionCode\":\"" + transaction + "\","
                + (productType == null ? "" : "\"productType\":\"" + productType + "\",")
                + "\"debitAccountId\":" + debit + ",\"creditAccountId\":" + credit + ",\"validFrom\":\""
                + validFrom + "\"}";
    }

    private static PostingLine line(String transaction, String amount, String productType, Long debitAccount,
                                    Long partnerId) {
        return new PostingLine(transaction, new BigDecimal(amount), productType, null, debitAccount, null, partnerId,
                null, null, null, null, null, "Dòng thử");
    }

    private static PostingRequest request(long sourceId, LocalDate date, List<PostingLine> lines) {
        return new PostingRequest(1L, 1L, date, "PNK", sourceId, "TEST-" + sourceId, "Bút toán thử", null, lines);
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private void assertCode(ErrorCode code, Supplier<?> action) {
        assertThatThrownBy(() -> inTransaction(action)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo(code));
    }

    private void assertBalanced(long entryId) {
        Map<String, Object> sums = jdbc.queryForMap("SELECT SUM(debit_amount) AS d, SUM(credit_amount) AS c "
                + "FROM journal_entry_lines WHERE journal_entry_id = ?", entryId);
        assertThat((BigDecimal) sums.get("d")).isEqualByComparingTo((BigDecimal) sums.get("c"));
    }

    private boolean postable(long accountId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT postable FROM chart_of_accounts WHERE id = ?",
                Boolean.class, accountId));
    }

    private int version(long accountId) {
        return jdbc.queryForObject("SELECT version FROM chart_of_accounts WHERE id = ?", Integer.class, accountId);
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries", Long.class);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private ResultActions importFile(byte[] content) throws Exception {
        return mockMvc.perform(as(accountant, multipart(ACCOUNTS + "/import")
                        .file(new MockMultipartFile("file", "tk.xlsx", "application/octet-stream", content)))
                .param("companyId", "1").header(IdempotentCommand.HEADER, newKey()));
    }

    private static byte[] workbook(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("TK");
            sheet.createRow(0).createCell(0).setCellValue("Mã TK");
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

    private ResultActions send(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(as(session, request).header(IdempotentCommand.HEADER, newKey())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
