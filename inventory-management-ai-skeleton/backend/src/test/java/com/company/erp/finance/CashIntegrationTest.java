package com.company.erp.finance;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Giai đoạn 4C - quỹ, ngân hàng trên MySQL thật: thu tiền phân bổ hóa đơn cũ nhất trước, thu một phần, trả trước
 * (AT-08, BR-AR-03), duyệt chi theo ngưỡng và quỹ không âm (BR-CSH-03), đảo (AT-09), đồng thời (BR-GEN-52), khóa kỳ quỹ
 * (AT-11), phân quyền (AT-10), chống xử lý trùng (AT-13). Sau mỗi kịch bản: số dư quỹ = sổ quỹ, số còn lại của hóa đơn
 * = sổ chi tiết công nợ, bút toán cân Nợ = Có.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class CashIntegrationTest {

    private static final String DOCS = "/api/v1/cash-documents";
    private static final String DATE = "2026-10-02";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    private Session admin;
    private Session clerk;
    private Session chief;
    private Session cashier;

    @Autowired
    CashIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = login(mockMvc, "admin");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
        cashier = login(mockMvc, "thuquy.hn");
    }

    @Test
    @DisplayName("AT-08, BR-AR-03, AT-13: thu một phần nhiều hóa đơn - tự động hóa đơn cũ nhất trước, chỉ định hóa đơn, phần dư là trả trước")
    void receiptAllocation() throws Exception {
        long customer = partner("CUSTOMER");
        String invoiceA = unique("HD");
        String invoiceB = unique("HD");
        String invoiceC = unique("HD");
        opening("RECEIVABLE", List.of(
                openingLine(customer, invoiceC, "2026-09-01", "2026-09-30", "3000000"),
                openingLine(customer, invoiceA, "2026-07-01", "2026-07-31", "1000000"),
                openingLine(customer, invoiceB, "2026-08-01", "2026-08-31", "2000000")));
        long cash = cashAccount("CASH", "1111");
        long bank = cashAccount("BANK", "1121");

        long auto = create(clerk, doc("PT", "CUSTOMER_RECEIPT", cash, null, customer, "AUTO", "1500000", List.of()));
        action(clerk, auto, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        String postKey = newKey();
        actionWithKey(cashier, auto, "post", null, postKey).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.allocatedAmount").value(1500000))
                .andExpect(jsonPath("$.data.prepaymentAmount").value(0))
                .andExpect(jsonPath("$.data.allocations.length()").value(2));
        actionWithKey(cashier, auto, "post", null, postKey).andExpect(status().isOk());
        action(cashier, auto, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        assertThat(item(customer, invoiceA)).containsEntry("status", "SETTLED");
        assertThat(item(customer, invoiceB)).containsEntry("status", "PARTIAL");
        assertThat((BigDecimal) item(customer, invoiceB).get("remaining_amount")).isEqualByComparingTo("1500000");
        assertThat(item(customer, invoiceC)).containsEntry("status", "OPEN");
        assertThat(journal("PT", auto)).containsExactly("N:1111:1500000", "C:131:1500000");

        // Chỉ định hóa đơn C, phần dư 200.000 là trả trước; phân bổ vượt số còn lại bị chặn ngay khi lập.
        long itemC = itemId(customer, invoiceC);
        send(clerk, post(DOCS), doc("BC", "CUSTOMER_RECEIPT", bank, null, customer, "MANUAL", "5000000",
                List.of(allocationLine(itemC, "3000001"))), newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].amount"));
        long manual = create(clerk, doc("BC", "CUSTOMER_RECEIPT", bank, null, customer, "MANUAL", "1200000",
                List.of(allocationLine(itemC, "1000000"))));
        action(clerk, manual, "submit", null).andExpect(status().isOk());
        action(cashier, manual, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prepaymentAmount").value(200000));
        assertThat((BigDecimal) item(customer, invoiceC).get("remaining_amount")).isEqualByComparingTo("2000000");
        assertThat(count("SELECT COUNT(*) FROM debt_ledger_entries WHERE partner_id = " + customer
                + " AND entry_type = 'PREPAYMENT' AND debt_item_id IS NULL AND credit_amount = 200000")).isOne();

        MvcResult ledger = mockMvc.perform(as(clerk, get("/api/v1/debt-ledger")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(customer))
                        .param("dateFrom", "2026-01-01").param("dateTo", "2026-12-31"))
                .andExpect(status().isOk()).andReturn();
        assertThat(amount(ledger, "$.data.closingBalance")).isEqualByComparingTo("3300000");
        assertBalance(cash, "1500000");
        assertBalance(bank, "1200000");
        assertDebtConsistent(customer);
    }

    @Test
    @DisplayName("BR-CSH-03, AT-10: chi từ ngưỡng phải duyệt, dưới ngưỡng tự duyệt; quỹ tiền mặt không âm; sổ quỹ khớp")
    void paymentApprovalAndCashLimit() throws Exception {
        long cash = cashAccount("CASH", "1111");
        fund(cash, "30000000");
        assertThat(journal("PT", lastDocument(cash))).containsExactly("N:1111:30000000", "C:4111:30000000");

        long large = create(clerk, doc("PC", "OTHER_PAYMENT", cash, null, null, null, null,
                List.of(counterLine("6422", "25000000"))));
        action(clerk, large, "submit", null).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        action(cashier, large, "post", null).andExpect(status().isConflict());
        action(clerk, large, "approve", "{}").andExpect(status().isForbidden());
        action(chief, large, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        action(cashier, large, "post", null).andExpect(status().isOk());
        assertBalance(cash, "5000000");

        long small = create(clerk, doc("PC", "OTHER_PAYMENT", cash, null, null, null, null,
                List.of(counterLine("6422", "10000000"))));
        action(clerk, small, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        action(cashier, small, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CASH_BALANCE_NEGATIVE"));
        assertThat(jdbc.queryForObject("SELECT status FROM cash_documents WHERE id = ?", String.class, small))
                .isEqualTo("APPROVED");
        assertThat(count("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'PC' AND source_id = " + small))
                .isZero();
        assertBalance(cash, "5000000");

        MvcResult book = mockMvc.perform(as(cashier, get("/api/v1/cash-book")).param("cashAccountId",
                        String.valueOf(cash)).param("dateFrom", "2026-01-01").param("dateTo", "2026-12-31"))
                .andExpect(status().isOk()).andReturn();
        assertThat(amount(book, "$.data.totalIn")).isEqualByComparingTo("30000000");
        assertThat(amount(book, "$.data.totalOut")).isEqualByComparingTo("25000000");
        assertThat(amount(book, "$.data.closingBalance")).isEqualByComparingTo("5000000");
        assertThat(amount(book, "$.data.lines[1].balance")).isEqualByComparingTo("5000000");
    }

    @Test
    @DisplayName("AT-09: đảo phiếu thu hoàn phân bổ vào hóa đơn, sổ quỹ, bút toán; công nợ đầu kỳ đã thu không đảo được trước")
    void reverseReceipt() throws Exception {
        long customer = partner("CUSTOMER");
        String invoice = unique("HD");
        long openingDoc = opening("RECEIVABLE", List.of(openingLine(customer, invoice, "2026-09-01", "2026-09-30",
                "1000000")));
        long cash = cashAccount("CASH", "1111");
        long receipt = create(clerk, doc("PT", "CUSTOMER_RECEIPT", cash, null, customer, "AUTO", "400000",
                List.of()));
        action(clerk, receipt, "submit", null).andExpect(status().isOk());
        action(cashier, receipt, "post", null).andExpect(status().isOk());

        mockMvc.perform(as(chief, post("/api/v1/debt-openings/" + openingDoc + "/reverse"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Thử đảo\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));
        action(clerk, receipt, "reverse", "{\"reason\":\"Thu nhầm\"}").andExpect(status().isForbidden());
        action(chief, receipt, "reverse", "{\"reason\":\"Thu nhầm khách\",\"postingDate\":\"2026-10-05\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.allocations[0].status").value("REVERSED"));
        assertThat(item(customer, invoice)).containsEntry("status", "OPEN");
        assertBalance(cash, "0");
        assertThat(jdbc.queryForList("SELECT status FROM journal_entries WHERE source_type = 'PT' AND source_id = ? "
                + "ORDER BY id", String.class, receipt)).containsExactly("REVERSED", "POSTED");
        assertDebtConsistent(customer);
        mockMvc.perform(as(chief, post("/api/v1/debt-openings/" + openingDoc + "/reverse"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Nhập sai\"}"))
                .andExpect(status().isOk());

        // Tiền của phiếu thu đã chi bớt: đảo phiếu thu làm quỹ âm nên bị từ chối, không thay đổi gì.
        long income = fund(cash, "500000");
        long spend = create(clerk, doc("PC", "OTHER_PAYMENT", cash, null, null, null, null,
                List.of(counterLine("6422", "400000"))));
        action(clerk, spend, "submit", null).andExpect(status().isOk());
        action(cashier, spend, "post", null).andExpect(status().isOk());
        action(chief, income, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CASH_BALANCE_NEGATIVE"));
        assertThat(jdbc.queryForObject("SELECT status FROM cash_documents WHERE id = ?", String.class, income))
                .isEqualTo("POSTED");
        assertBalance(cash, "100000");
    }

    @Test
    @DisplayName("BR-GEN-52: hai phiếu thu cùng phân bổ một hóa đơn đồng thời - chỉ một phiếu ghi sổ, không vượt số còn lại")
    void concurrentAllocation() throws Exception {
        long customer = partner("CUSTOMER");
        String invoice = unique("HD");
        opening("RECEIVABLE", List.of(openingLine(customer, invoice, "2026-09-01", "2026-09-30", "1000000")));
        long target = itemId(customer, invoice);
        long cash = cashAccount("CASH", "1111");
        List<Long> receipts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            long receipt = create(clerk, doc("PT", "CUSTOMER_RECEIPT", cash, null, customer, "MANUAL", "700000",
                    List.of(allocationLine(target, "700000"))));
            action(clerk, receipt, "submit", null).andExpect(status().isOk());
            receipts.add(receipt);
        }
        List<Integer> statuses = postConcurrently(receipts);
        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        assertThat((BigDecimal) item(customer, invoice).get("settled_amount")).isEqualByComparingTo("700000");
        assertBalance(cash, "700000");
        assertDebtConsistent(customer);
    }

    @Test
    @DisplayName("BR-GEN-52, BR-CSH-03: hai phiếu chi cùng một quỹ đồng thời - quỹ không âm, số dư khớp sổ quỹ")
    void concurrentPayments() throws Exception {
        long cash = cashAccount("CASH", "1111");
        fund(cash, "1000000");
        List<Long> payments = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            long payment = create(clerk, doc("PC", "OTHER_PAYMENT", cash, null, null, null, null,
                    List.of(counterLine("6422", "700000"))));
            action(clerk, payment, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
            payments.add(payment);
        }
        List<Integer> statuses = postConcurrently(payments);
        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        assertBalance(cash, "300000");
    }

    @Test
    @DisplayName("BR-CSH-02, BR-CSH-06, AT-11, AT-10: chuyển tiền nội bộ, dòng tiền thực tế; kỳ quỹ khóa; trái quyền, ngoài phạm vi")
    void transferFlowPeriodAndAccess() throws Exception {
        long cash = cashAccount("CASH", "1111");
        long bank = cashAccount("BANK", "1121");
        fund(cash, "2000000");
        long transfer = create(clerk, doc("CTN", "INTERNAL_TRANSFER", cash, bank, null, null, "1500000", List.of()));
        action(clerk, transfer, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        action(cashier, transfer, "post", null).andExpect(status().isOk());
        assertBalance(cash, "500000");
        assertBalance(bank, "1500000");
        assertThat(journal("CTN", transfer)).containsExactly("N:1121:1500000", "C:1111:1500000");

        MvcResult flow = mockMvc.perform(as(chief, get("/api/v1/cash-flow")).param("companyId", "1")
                        .param("branchId", "1").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isOk()).andReturn();
        List<Integer> ids = json(flow, "$.data.accounts[*].cashAccountId");
        int cashRow = ids.indexOf((int) cash);
        int bankRow = ids.indexOf((int) bank);
        assertThat(amount(flow, "$.data.accounts[" + cashRow + "].inAmount")).isEqualByComparingTo("2000000");
        assertThat(amount(flow, "$.data.accounts[" + cashRow + "].outAmount")).isEqualByComparingTo("1500000");
        assertThat(amount(flow, "$.data.accounts[" + bankRow + "].closingBalance")).isEqualByComparingTo("1500000");

        send(chief, post("/api/v1/fiscal-periods/generate"), "{\"companyId\":1,\"year\":2068}", newKey())
                .andExpect(status().isOk());
        long january = count("SELECT id FROM fiscal_periods WHERE company_id = 1 AND period_year = 2068 "
                + "AND period_month = 1");
        send(chief, post("/api/v1/fiscal-periods/" + january + "/lock"), "{\"module\":\"CASH\",\"branchIds\":[1]}",
                newKey()).andExpect(status().isOk());
        long locked = create(clerk, doc("PT", "OTHER_RECEIPT", cash, null, null, null, null,
                List.of(counterLine("4111", "100"))).replace(DATE, "2068-01-05"));
        action(clerk, locked, "submit", null).andExpect(status().isOk());
        action(cashier, locked, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_LOCKED"));
        assertThat(count("SELECT COUNT(*) FROM cash_ledger_entries WHERE reference_id = " + locked
                + " AND reference_type = 'PT'")).isZero();
        assertBalance(cash, "500000");

        mockMvc.perform(as(login(mockMvc, "kho.hcm"), get(DOCS + "/" + transfer))).andExpect(status().isForbidden());
        String otherBranch = unique("tqhcm").toLowerCase();
        createUser(jdbc, otherBranch, "CASHIER", "BRANCH", 2, 2, false);
        Session hcm = login(mockMvc, otherBranch);
        mockMvc.perform(as(hcm, get(DOCS + "/" + transfer))).andExpect(status().isNotFound());
        action(hcm, transfer, "reverse", "{\"reason\":\"x\"}").andExpect(status().isForbidden());
        mockMvc.perform(as(hcm, get("/api/v1/cash-book")).param("cashAccountId", String.valueOf(cash))
                .param("dateFrom", DATE).param("dateTo", DATE)).andExpect(status().isNotFound());
    }

    // ---- hỗ trợ ----

    private List<Integer> postConcurrently(List<Long> documents) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(documents.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Long document : documents) {
            Callable<Integer> task = () -> {
                start.await();
                return action(cashier, document, "post", null).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return statuses;
    }

    private long partner(String role) throws Exception {
        String code = unique("DT");
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Đối tác " + code
                + "\",\"partnerKind\":\"ORGANIZATION\",\"roles\":[\"" + role + "\"],\"paymentTermDays\":0"
                + ",\"taxCode\":\"0300000001\",\"address\":\"Hà Nội\",\"contacts\":[{\"name\":\"Liên hệ\",\"primaryContact\":true}]}";
        return jsonId(send(admin, post("/api/v1/partners"), body, newKey()).andExpect(status().isCreated()).andReturn());
    }

    private static String openingLine(long partner, String invoiceNo, String invoiceDate, String dueDate,
                                      String amount) {
        return "{\"partnerId\":" + partner + ",\"invoiceNo\":\"" + invoiceNo + "\",\"invoiceDate\":\"" + invoiceDate
                + "\",\"dueDate\":\"" + dueDate + "\",\"amount\":" + amount + "}";
    }

    /** Công nợ đầu kỳ đã ghi sổ (nguồn khoản công nợ để thu / chi). */
    private long opening(String debtType, List<String> lines) throws Exception {
        String body = "{\"branchId\":1,\"debtType\":\"" + debtType + "\",\"documentDate\":\"2026-10-01\","
                + "\"lines\":[" + String.join(",", lines) + "]}";
        long id = jsonId(send(clerk, post("/api/v1/debt-openings"), body, newKey()).andExpect(status().isCreated())
                .andReturn());
        for (String[] step : new String[][] {{"submit", null}, {"approve", "{}"}, {"post", null}}) {
            Session session = "approve".equals(step[0]) ? chief : clerk;
            MockHttpServletRequestBuilder request = as(session, post("/api/v1/debt-openings/" + id + "/" + step[0]))
                    .header(IdempotentCommand.HEADER, newKey());
            if (step[1] != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(step[1]);
            }
            mockMvc.perform(request).andExpect(status().isOk());
        }
        return id;
    }

    private long cashAccount(String type, String glCode) throws Exception {
        String code = unique("Q");
        long gl = glAccount(glCode);
        String body = "{\"branchId\":1,\"code\":\"" + code + "\",\"name\":\"Quỹ " + code + "\",\"accountType\":\""
                + type + "\",\"glAccountId\":" + gl + ("BANK".equals(type)
                ? ",\"bankName\":\"Ngân hàng thử\",\"bankAccountNo\":\"" + code + "\"" : "") + "}";
        return jsonId(send(chief, post("/api/v1/cash-accounts"), body, newKey()).andExpect(status().isCreated())
                .andReturn());
    }

    /** Nạp tiền vào quỹ bằng phiếu thu khác (Có 4111), trả id phiếu thu. */
    private long fund(long cash, String amount) throws Exception {
        String type = "CASH".equals(jdbc.queryForObject("SELECT account_type FROM cash_accounts WHERE id = ?",
                String.class, cash)) ? "PT" : "BC";
        long receipt = create(clerk, doc(type, "OTHER_RECEIPT", cash, null, null, null, null,
                List.of(counterLine("4111", amount))));
        action(clerk, receipt, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        action(cashier, receipt, "post", null).andExpect(status().isOk());
        return receipt;
    }

    private long lastDocument(long cash) {
        return count("SELECT MAX(id) FROM cash_documents WHERE cash_account_id = " + cash);
    }

    private static String doc(String type, String purpose, long cash, Long target, Long partner, String mode,
                              String amount, List<String> lines) {
        StringBuilder body = new StringBuilder("{\"branchId\":1,\"documentType\":\"" + type + "\",\"purpose\":\""
                + purpose + "\",\"documentDate\":\"" + DATE + "\",\"cashAccountId\":" + cash);
        if (target != null) {
            body.append(",\"targetCashAccountId\":").append(target);
        }
        if (partner != null) {
            body.append(",\"partnerId\":").append(partner);
        }
        if (mode != null) {
            body.append(",\"allocationMode\":\"").append(mode).append('"');
        }
        if (amount != null) {
            body.append(",\"amount\":").append(amount);
        }
        return body.append(",\"description\":\"Thử nghiệm\",\"lines\":[").append(String.join(",", lines))
                .append("]}").toString();
    }

    private static String allocationLine(long debtItemId, String amount) {
        return "{\"debtItemId\":" + debtItemId + ",\"amount\":" + amount + "}";
    }

    private String counterLine(String accountCode, String amount) {
        return "{\"accountId\":" + glAccount(accountCode) + ",\"amount\":" + amount + "}";
    }

    private long glAccount(String code) {
        return count("SELECT id FROM chart_of_accounts WHERE company_id = 1 AND code = '" + code + "'");
    }

    private long create(Session session, String body) throws Exception {
        return jsonId(send(session, post(DOCS), body, newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn());
    }

    private Map<String, Object> item(long partner, String invoiceNo) {
        return jdbc.queryForMap("SELECT status, settled_amount, remaining_amount FROM debt_items WHERE partner_id = ? "
                + "AND invoice_no = ?", partner, invoiceNo);
    }

    private long itemId(long partner, String invoiceNo) {
        return jdbc.queryForObject("SELECT id FROM debt_items WHERE partner_id = ? AND invoice_no = ?", Long.class,
                partner, invoiceNo);
    }

    /** Dòng bút toán gốc của chứng từ dạng "N|C:tài khoản:số tiền"; khẳng định cân Nợ = Có. */
    private List<String> journal(String sourceType, long sourceId) {
        List<String> lines = jdbc.queryForList("""
                SELECT CONCAT(IF(l.debit_amount > 0, 'N', 'C'), ':', a.code, ':',
                              CAST(l.debit_amount + l.credit_amount AS DECIMAL(19,0)))
                FROM journal_entry_lines l
                JOIN journal_entries e ON e.id = l.journal_entry_id
                JOIN chart_of_accounts a ON a.id = l.account_id
                WHERE e.source_type = ? AND e.source_id = ? AND e.reversal_of_id IS NULL
                ORDER BY l.line_no
                """, String.class, sourceType, sourceId);
        BigDecimal net = jdbc.queryForObject("SELECT SUM(l.debit_amount) - SUM(l.credit_amount) FROM "
                + "journal_entry_lines l JOIN journal_entries e ON e.id = l.journal_entry_id WHERE e.source_type = ? "
                + "AND e.source_id = ?", BigDecimal.class, sourceType, sourceId);
        assertThat(net).isEqualByComparingTo("0");
        return lines;
    }

    /** Số dư quỹ = số tiền mong đợi = Σ Nợ − Σ Có của sổ quỹ. */
    private void assertBalance(long cash, String expected) {
        BigDecimal balance = jdbc.queryForObject("SELECT balance FROM cash_accounts WHERE id = ?", BigDecimal.class,
                cash);
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(debit_amount - credit_amount), 0) FROM "
                + "cash_ledger_entries WHERE cash_account_id = ?", BigDecimal.class, cash);
        assertThat(balance).isEqualByComparingTo(expected);
        assertThat(ledger).isEqualByComparingTo(expected);
    }

    /** Số còn lại của các hóa đơn chưa đảo = số dư sổ chi tiết gắn hóa đơn (phần trả trước theo dõi riêng). */
    private void assertDebtConsistent(long partnerId) {
        BigDecimal remaining = jdbc.queryForObject("SELECT COALESCE(SUM(remaining_amount), 0) FROM debt_items "
                + "WHERE partner_id = ? AND status <> 'REVERSED'", BigDecimal.class, partnerId);
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN debt_type = 'RECEIVABLE' "
                + "THEN debit_amount - credit_amount ELSE credit_amount - debit_amount END), 0) "
                + "FROM debt_ledger_entries WHERE partner_id = ? AND debt_item_id IS NOT NULL", BigDecimal.class,
                partnerId);
        assertThat(remaining).isEqualByComparingTo(ledger);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private ResultActions action(Session session, long id, String action, String body) throws Exception {
        return actionWithKey(session, id, action, body, newKey());
    }

    private ResultActions actionWithKey(Session session, long id, String action, String body, String key)
            throws Exception {
        MockHttpServletRequestBuilder request = as(session, post(DOCS + "/" + id + "/" + action))
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
