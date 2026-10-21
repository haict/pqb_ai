package com.company.erp.finance;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Giai đoạn 4D trên MySQL thật: bù trừ trả trước và phải thu - phải trả (BR-AR-04), đối chiếu công nợ và điều chỉnh có
 * duyệt (BR-AR-06, 07, AT-08), tạm ứng / thanh toán tạm ứng / hoàn ứng (BR-CSH-04), kiểm kê quỹ (BR-CSH-05), đảo
 * (AT-09), đồng thời trên tiền trả trước (BR-GEN-52). Sau mỗi kịch bản: sổ phụ khớp số dư.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class DebtSettlementIntegrationTest extends FinanceIntegrationSupport {

    private static final String OFFSETS = "/api/v1/debt-offsets";
    private static final String ADJUSTMENTS = "/api/v1/debt-adjustments";
    private static final String COUNTS = "/api/v1/cash-counts";

    @Autowired
    DebtSettlementIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-AR-04, AT-09: bù trừ trả trước vào hóa đơn; trả trước đã dùng thì không đảo được phiếu thu; bỏ bù trừ rồi đảo")
    void prepaymentOffset() throws Exception {
        long customer = partner("CUSTOMER");
        String invoiceA = unique("HD");
        String invoiceB = unique("HD");
        opening("RECEIVABLE", List.of(openingLine(customer, invoiceA, "2026-08-31", "1000000"),
                openingLine(customer, invoiceB, "2026-09-30", "800000")));
        long cash = cashAccount("CASH", "1111");
        long receipt = postedCash(cashDoc("PT", "CUSTOMER_RECEIPT", cash, customer, "MANUAL", "1500000",
                List.of("{\"debtItemId\":" + itemId(customer, invoiceA) + ",\"amount\":1000000}")));
        assertThat(prepayment(customer, "RECEIVABLE")).isEqualByComparingTo("500000");
        mockMvc.perform(as(clerk, get("/api/v1/debt-prepayments")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(customer)))
                .andExpect(jsonPath("$.data[0].amount").value(500000));

        long itemB = itemId(customer, invoiceB);
        send(clerk, post(OFFSETS), offset("PREPAYMENT", "RECEIVABLE", customer, List.of(line(itemB, "600000"))),
                newKey()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lines"));
        long offset = create(OFFSETS, offset("PREPAYMENT", "RECEIVABLE", customer, List.of(line(itemB, "500000"))));
        act(clerk, OFFSETS, offset, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(remaining(customer, invoiceB)).isEqualByComparingTo("300000");
        assertThat(prepayment(customer, "RECEIVABLE")).isEqualByComparingTo("0");
        assertThat(count("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DTCN' AND source_id = " + offset))
                .isZero();
        assertDebtConsistent(customer);

        act(chief, CASH_DOCS, receipt, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));
        act(clerk, OFFSETS, offset, "reverse", "{\"reason\":\"Bỏ đối trừ\"}").andExpect(status().isForbidden());
        act(chief, OFFSETS, offset, "reverse", "{\"reason\":\"Bỏ đối trừ\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(remaining(customer, invoiceB)).isEqualByComparingTo("800000");
        assertThat(prepayment(customer, "RECEIVABLE")).isEqualByComparingTo("500000");
        act(chief, CASH_DOCS, receipt, "reverse", "{\"reason\":\"Thu nhầm\"}").andExpect(status().isOk());
        assertThat(prepayment(customer, "RECEIVABLE")).isEqualByComparingTo("0");
        assertThat(remaining(customer, invoiceA)).isEqualByComparingTo("1000000");
        assertDebtConsistent(customer);
        assertBalance(cash, "0");
    }

    @Test
    @DisplayName("BR-AR-04: bù trừ phải thu với phải trả cùng đối tượng, bút toán Nợ 331 / Có 131; tổng hai phía phải bằng nhau")
    void receivablePayableOffset() throws Exception {
        long both = partner("CUSTOMER", "SUPPLIER");
        String sale = unique("HD");
        String purchase = unique("HDM");
        opening("RECEIVABLE", List.of(openingLine(both, sale, "2026-09-30", "1000000")));
        opening("PAYABLE", List.of(openingLine(both, purchase, "2026-10-05", "600000")));
        long receivable = itemId(both, sale);
        long payable = itemId(both, purchase);

        send(clerk, post(OFFSETS), offset("AR_AP", null, both, List.of(line(receivable, "700000"),
                line(payable, "600000"))), newKey()).andExpect(status().isBadRequest());
        long offset = create(OFFSETS, offset("AR_AP", null, both, List.of(line(receivable, "600000"),
                line(payable, "600000"))));
        act(clerk, OFFSETS, offset, "post", null).andExpect(status().isOk());
        assertThat(remaining(both, sale)).isEqualByComparingTo("400000");
        assertThat(item(both, purchase)).containsEntry("status", "SETTLED");
        assertThat(journal("DTCN", offset)).containsExactly("N:331:600000", "C:131:600000");
        assertDebtConsistent(both);

        long customerOnly = partner("CUSTOMER");
        send(clerk, post(OFFSETS), offset("AR_AP", null, customerOnly, List.of(line(receivable, "1"))), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("partnerId"));
    }

    @Test
    @DisplayName("AT-08, BR-AR-06, BR-AR-07: biên bản đối chiếu khớp sổ; lệch thì điều chỉnh công nợ; đảo điều chỉnh")
    void reconciliationAndAdjustment() throws Exception {
        long customer = partner("CUSTOMER");
        String invoice = unique("HD");
        opening("RECEIVABLE", List.of(openingLine(customer, invoice, "2026-09-30", "1000000")));
        long cash = cashAccount("CASH", "1111");
        postedCash(cashDoc("PT", "CUSTOMER_RECEIPT", cash, customer, "AUTO", "300000", List.of()));

        MvcResult statement = statement(customer);
        assertThat(amount(statement, "$.data.closingBalance")).isEqualByComparingTo("700000");
        assertThat(amount(statement, "$.data.openItemsAmount")).isEqualByComparingTo("700000");
        assertThat(amount(statement, "$.data.prepaymentAmount")).isEqualByComparingTo("0");

        String reconciliationBody = "{\"companyId\":1,\"debtType\":\"RECEIVABLE\",\"partnerId\":" + customer
                + ",\"periodFrom\":\"2026-01-01\",\"asOfDate\":\"" + DATE + "\"}";
        long reconciliation = jsonId(send(clerk, post("/api/v1/debt-reconciliations"), reconciliationBody, newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.systemBalance").value(700000))
                .andExpect(jsonPath("$.data.status").value("PENDING")).andReturn());
        act(clerk, "/api/v1/debt-reconciliations", reconciliation, "confirm",
                "{\"confirmedBalance\":650000,\"contactName\":\"Kế toán khách\"}")
                .andExpect(jsonPath("$.data.status").value("DIFFERENT"))
                .andExpect(jsonPath("$.data.difference").value(-50000));
        String branchUser = unique("kthcm").toLowerCase();
        createUser(jdbc, branchUser, "AR_ACCOUNTANT", "BRANCH", 2, 2, false);
        send(login(mockMvc, branchUser), post("/api/v1/debt-reconciliations"), reconciliationBody, newKey())
                .andExpect(status().isForbidden());

        long target = itemId(customer, invoice);
        String adjustment = "{\"branchId\":1,\"documentType\":\"DCCN\",\"debtType\":\"RECEIVABLE\",\"partnerId\":"
                + customer + ",\"reconciliationId\":" + reconciliation + ",\"documentDate\":\"" + DATE
                + "\",\"description\":\"Chênh lệch đối chiếu\",\"lines\":[{\"direction\":\"DECREASE\",\"debtItemId\":"
                + target + ",\"accountId\":" + glAccount("811") + ",\"amount\":50000},{\"direction\":\"INCREASE\","
                + "\"accountId\":" + glAccount("711") + ",\"amount\":20000,\"invoiceNo\":\"" + unique("PS") + "\"}]}";
        long document = create(ADJUSTMENTS, adjustment);
        act(clerk, ADJUSTMENTS, document, "post", null).andExpect(status().isOk());
        assertThat(remaining(customer, invoice)).isEqualByComparingTo("650000");
        assertThat(journal("DCCN", document)).containsExactly("N:811:50000", "C:131:50000", "N:131:20000",
                "C:711:20000");
        assertThat(amount(statement(customer), "$.data.closingBalance")).isEqualByComparingTo("670000");
        assertDebtConsistent(customer);

        act(chief, ADJUSTMENTS, document, "reverse", "{\"reason\":\"Lập sai\"}").andExpect(status().isOk());
        assertThat(remaining(customer, invoice)).isEqualByComparingTo("700000");
        assertThat(count("SELECT COUNT(*) FROM debt_items WHERE source_type = 'DCCN' AND source_id = " + document
                + " AND status = 'REVERSED'")).isOne();
        assertThat(amount(statement(customer), "$.data.closingBalance")).isEqualByComparingTo("700000");
        assertDebtConsistent(customer);
    }

    @Test
    @DisplayName("BR-CSH-04: chi tạm ứng sinh công nợ tạm ứng, thanh toán tạm ứng bằng chi phí, hoàn ứng phần còn lại bằng tiền")
    void employeeAdvance() throws Exception {
        long employee = partner("EMPLOYEE");
        long cash = cashAccount("CASH", "1111");
        fund(cash, "5000000");
        long advance = postedCash(cashDoc("PC", "EMPLOYEE_ADVANCE", cash, employee, null, "3000000", List.of()));
        assertThat(journal("PC", advance)).containsExactly("N:141:3000000", "C:1111:3000000");
        assertThat(jdbc.queryForObject("SELECT due_date FROM debt_items WHERE source_type = 'PC' AND source_id = ?",
                LocalDate.class, advance)).isEqualTo(LocalDate.parse(DATE).plusDays(30));
        long advanceItem = count("SELECT id FROM debt_items WHERE source_type = 'PC' AND source_id = " + advance);

        long settlement = create(ADJUSTMENTS, "{\"branchId\":1,\"documentType\":\"TTTU\",\"debtType\":\"ADVANCE\","
                + "\"partnerId\":" + employee + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"direction\":"
                + "\"DECREASE\",\"debtItemId\":" + advanceItem + ",\"accountId\":" + glAccount("6422")
                + ",\"amount\":2000000,\"description\":\"Công tác phí\"}]}");
        act(clerk, ADJUSTMENTS, settlement, "post", null).andExpect(status().isOk());
        assertThat(journal("TTTU", settlement)).containsExactly("N:6422:2000000", "C:141:2000000");

        long refund = postedCash(cashDoc("PT", "ADVANCE_RETURN", cash, employee, "AUTO", "1000000", List.of()));
        assertThat(journal("PT", refund)).containsExactly("N:1111:1000000", "C:141:1000000");
        assertThat(jdbc.queryForObject("SELECT status FROM debt_items WHERE id = ?", String.class, advanceItem))
                .isEqualTo("SETTLED");
        assertBalance(cash, "3000000");
        assertDebtConsistent(employee);
        act(chief, CASH_DOCS, advance, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        long customer = partner("CUSTOMER");
        send(clerk, post(CASH_DOCS), cashDoc("PC", "EMPLOYEE_ADVANCE", cash, customer, null, "1", List.of()),
                newKey()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field")
                .value("partnerId"));
    }

    @Test
    @DisplayName("BR-CSH-05: kiểm kê quỹ ghi sổ thẳng từ nháp; thiếu ghi sổ quỹ và bút toán chênh lệch; khớp thì không sinh sổ; đảo")
    void cashCount() throws Exception {
        long cash = cashAccount("CASH", "1111");
        fund(cash, "1000000");
        long shortage = create(COUNTS, countBody(cash, "950000"));
        act(cashier, COUNTS, shortage, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookBalance").value(1000000))
                .andExpect(jsonPath("$.data.differenceAmount").value(-50000));
        assertBalance(cash, "950000");
        assertThat(journal("KKQ", shortage)).containsExactly("N:811:50000", "C:1111:50000");

        long matched = create(COUNTS, countBody(cash, "950000"));
        act(cashier, COUNTS, matched, "post", null).andExpect(jsonPath("$.data.differenceAmount").value(0));
        assertThat(count("SELECT COUNT(*) FROM cash_ledger_entries WHERE reference_type = 'KKQ' AND reference_id = "
                + matched)).isZero();

        act(chief, COUNTS, shortage, "reverse", "{\"reason\":\"Đếm nhầm\"}").andExpect(status().isOk());
        assertBalance(cash, "1000000");
        send(cashier, post(COUNTS), countBody(cashAccount("BANK", "1121"), "1"), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cashAccountId"));
    }

    @Test
    @DisplayName("BR-GEN-52: hai chứng từ cùng dùng hết một khoản trả trước đồng thời - chỉ một chứng từ ghi sổ")
    void concurrentPrepaymentOffsets() throws Exception {
        long customer = partner("CUSTOMER");
        String paid = unique("HD");
        String first = unique("HD");
        String second = unique("HD");
        opening("RECEIVABLE", List.of(openingLine(customer, paid, "2026-08-31", "100000"),
                openingLine(customer, first, "2026-09-30", "500000"),
                openingLine(customer, second, "2026-09-30", "500000")));
        long cash = cashAccount("CASH", "1111");
        postedCash(cashDoc("PT", "CUSTOMER_RECEIPT", cash, customer, "MANUAL", "500000",
                List.of("{\"debtItemId\":" + itemId(customer, paid) + ",\"amount\":100000}")));
        List<Long> offsets = new ArrayList<>();
        for (String invoice : List.of(first, second)) {
            long offset = create(OFFSETS, offset("PREPAYMENT", "RECEIVABLE", customer,
                    List.of(line(itemId(customer, invoice), "400000"))));
            offsets.add(offset);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Long offset : offsets) {
            Callable<Integer> task = () -> {
                start.await();
                return act(clerk, OFFSETS, offset, "post", null).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        assertThat(prepayment(customer, "RECEIVABLE")).isEqualByComparingTo("0");
        assertDebtConsistent(customer);
    }

    // ---- hỗ trợ ----

    private MvcResult statement(long partner) throws Exception {
        return mockMvc.perform(as(clerk, get("/api/v1/debt-statements")).param("companyId", "1")
                        .param("debtType", "RECEIVABLE").param("partnerId", String.valueOf(partner))
                        .param("dateFrom", "2026-01-01").param("dateTo", "2099-12-31"))
                .andExpect(status().isOk()).andReturn();
    }

    private long create(String base, String body) throws Exception {
        return jsonId(send(base.equals(COUNTS) ? cashier : clerk, post(base), body, newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn());
    }

    private static String offset(String type, String debtType, long partner, List<String> lines) {
        return "{\"branchId\":1,\"offsetType\":\"" + type + "\"" + (debtType == null ? ""
                : ",\"debtType\":\"" + debtType + "\"") + ",\"partnerId\":" + partner + ",\"documentDate\":\"" + DATE
                + "\",\"lines\":[" + String.join(",", lines) + "]}";
    }

    private static String line(long debtItemId, String amount) {
        return "{\"debtItemId\":" + debtItemId + ",\"amount\":" + amount + "}";
    }

    private static String countBody(long cash, String counted) {
        return "{\"branchId\":1,\"cashAccountId\":" + cash + ",\"documentDate\":\"" + DATE + "\",\"countedAmount\":"
                + counted + "}";
    }
}
