package com.company.erp.inventory;

import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.inventory.application.InventoryReservationService;
import com.company.erp.inventory.application.dto.ReservationRequest;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Giai đoạn 3 - lõi kho trên MySQL thật: ghi sổ nhập / xuất theo lô, hạn dùng, serial, FEFO (AT-02, AT-03), xuất
 * đồng thời (AT-04), đảo chứng từ (AT-09), phân quyền và phạm vi (AT-10), khóa kỳ (AT-11), chống xử lý trùng (AT-13),
 * kiểm kê, chuyển kho khác chi nhánh, chính sách xuất vượt tồn. Sau mỗi kịch bản: số dư = tổng sổ nhật ký.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class InventoryIntegrationTest extends InventoryIntegrationSupport {

    private final InventoryReservationService reservations;
    private final TransactionTemplate transactions;

    @Autowired
    InventoryIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, InventoryReservationService reservations,
                             TransactionTemplate transactions) {
        super(mockMvc, jdbc);
        this.reservations = reservations;
        this.transactions = transactions;
    }

    @Test
    @DisplayName("AT-02, AT-03, BR-INV-21: nhập theo lô / hạn dùng / serial, xuất theo FEFO, chọn lô khác phải có lý do")
    void lotsSerialsAndFefo() throws Exception {
        long warehouse = warehouse(1);
        long product = product(true, true, false);
        long receipt = createAndPost(manager, "PNK", warehouse, List.of(
                receiptLine(product, 5, "LA" + product, "2027-06-30", null),
                receiptLine(product, 5, "LB" + product, "2027-01-31", null)));
        assertThat(docStatus(receipt)).isEqualTo("POSTED");
        long lateLot = id("SELECT id FROM inventory_lots WHERE product_id = " + product + " AND lot_no = 'LA"
                + product + "'");

        MvcResult fefo = mockMvc.perform(as(manager, get("/api/v1/inventory/fefo-suggestions"))
                        .param("warehouseId", String.valueOf(warehouse)).param("productId", String.valueOf(product))
                        .param("quantity", "7"))
                .andExpect(status().isOk()).andReturn();
        List<String> lots = json(fefo, "$.data[*].lotNo");
        assertThat(lots).containsExactly("LB" + product, "LA" + product);

        long override = create(manager, "PXK", warehouse, List.of(issueLine(product, 2, lateLot, null)));
        action(manager, override, "post", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lotOverrideReason"));

        long issue = createAndPost(manager, "PXK", warehouse, List.of(issueLine(product, 7, null, null)));
        MvcResult detail = mockMvc.perform(as(manager, get(DOCS + "/" + issue))).andExpect(status().isOk()).andReturn();
        List<Object> earlyLot = json(detail, "$.data.transactions[?(@.lotNo == 'LB" + product + "')].quantity");
        List<Object> lateLotIssued = json(detail, "$.data.transactions[?(@.lotNo == 'LA" + product + "')].quantity");
        assertThat(new BigDecimal(earlyLot.get(0).toString())).isEqualByComparingTo("5");
        assertThat(new BigDecimal(lateLotIssued.get(0).toString())).isEqualByComparingTo("2");
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("3");

        long sameLotOtherExpiry = create(manager, "PNK", warehouse,
                List.of(receiptLine(product, 1, "LA" + product, "2028-01-01", null)));
        action(manager, sameLotOtherExpiry, "post", null).andExpect(status().isBadRequest());

        long serialProduct = product(false, false, true);
        createAndPost(manager, "PNK", warehouse, List.of(receiptLine(serialProduct, 2, null, null,
                List.of("S1-" + serialProduct, "S2-" + serialProduct))));
        long duplicateSerial = create(manager, "PNK", warehouse, List.of(receiptLine(serialProduct, 1, null, null,
                List.of("S1-" + serialProduct))));
        action(manager, duplicateSerial, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERIAL_UNAVAILABLE"));
        assertThat(onHand(serialProduct, warehouse)).isEqualByComparingTo("2");
        assertLedgerConsistent(product);
        assertLedgerConsistent(serialProduct);
    }

    @Test
    @DisplayName("AT-04: năm người cùng ghi sổ xuất trên một dòng tồn - không âm, không vượt tồn, số dư khớp sổ")
    void concurrentIssues() throws Exception {
        long warehouse = warehouse(1);
        long product = product(false, false, false);
        createAndPost(manager, "PNK", warehouse, List.of(receiptLine(product, 10, null, null, null)));
        List<Long> issues = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long issue = create(manager, "PXK", warehouse, List.of(issueLine(product, 3, null, null)));
            issues.add(issue);
        }
        ExecutorService pool = Executors.newFixedThreadPool(issues.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Long issue : issues) {
            Callable<Integer> task = () -> {
                start.await();
                return action(manager, issue, "post", null).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(statuses).filteredOn(code -> code == 200).hasSize(3);
        assertThat(statuses).filteredOn(code -> code == 422).hasSize(2);
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("1");
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("AT-09: đảo phiếu nhập bị chặn khi hàng đã xuất; đảo phiếu xuất trước rồi đảo phiếu nhập")
    void reversal() throws Exception {
        long warehouse = warehouse(1);
        long product = product(true, true, false);
        long receipt = createAndPost(manager, "PNK", warehouse,
                List.of(receiptLine(product, 10, "LR" + product, "2027-12-31", null)));
        long issue = createAndPost(manager, "PXK", warehouse, List.of(issueLine(product, 4, null, null)));
        Session accountant = login(mockMvc, "ketoantruong");

        long reverseReceipt = reverse(receipt);
        action(manager, reverseReceipt, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));
        action(manager, receipt, "reverse", "{\"reason\":\"Lần hai\"}").andExpect(status().isConflict());

        long reverseIssue = reverse(issue);
        action(manager, reverseIssue, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(docStatus(issue)).isEqualTo("REVERSED");
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("10");

        action(manager, reverseReceipt, "post", null).andExpect(status().isOk());
        assertThat(docStatus(receipt)).isEqualTo("REVERSED");
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("0");
        Long reversalRows = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transactions WHERE product_id = ? "
                + "AND transaction_type = 'REVERSAL' AND reversal_of_id IS NOT NULL", Long.class, product);
        assertThat(reversalRows).isEqualTo(2L);
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("AT-11: chứng từ thuộc kỳ kho đã khóa không ghi sổ được, không để lại dữ liệu dở dang")
    void lockedPeriod() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        mockMvc.perform(as(accountant, post("/api/v1/fiscal-periods/generate")).header(IdempotentCommand.HEADER,
                        newKey()).contentType(MediaType.APPLICATION_JSON).content("{\"companyId\":1,\"year\":2062}"))
                .andExpect(status().isOk());
        long january = id("SELECT id FROM fiscal_periods WHERE company_id = 1 AND period_year = 2062 "
                + "AND period_month = 1");
        mockMvc.perform(as(accountant, post("/api/v1/fiscal-periods/" + january + "/lock"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"INVENTORY\",\"branchIds\":[1]}"))
                .andExpect(status().isOk());
        long warehouse = warehouse(1);
        long product = product(false, false, false);
        long receipt = create(manager, "PNK", warehouse, List.of(receiptLine(product, 3, null, null, null)),
                "2062-01-15");
        action(manager, receipt, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_LOCKED"));
        assertThat(docStatus(receipt)).isEqualTo("DRAFT");
        assertThat(count("SELECT COUNT(*) FROM inventory_transactions WHERE product_id = " + product)).isZero();
        assertThat(count("SELECT COUNT(*) FROM inventory_balances WHERE product_id = " + product)).isZero();
    }

    @Test
    @DisplayName("AT-13, AT-10: gửi lại cùng Idempotency-Key không tạo / ghi sổ trùng; sai quyền 403, ngoài phạm vi 404")
    void idempotencyAndAccess() throws Exception {
        long warehouse = warehouse(1);
        long product = product(false, false, false);
        String body = document("PNK", warehouse, List.of(receiptLine(product, 4, null, null, null)), "2026-10-05");
        String key = newKey();
        Integer first = json(send(manager, post(DOCS), body, key).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        Integer replay = json(send(manager, post(DOCS), body, key).andExpect(status().isCreated()).andReturn(),
                "$.data.id");
        assertThat(replay).isEqualTo(first);
        long receipt = first.longValue();
        String postKey = newKey();
        actionWithKey(manager, receipt, "post", null, postKey).andExpect(status().isOk());
        actionWithKey(manager, receipt, "post", null, postKey).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        action(manager, receipt, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        assertThat(count("SELECT COUNT(*) FROM inventory_transactions WHERE product_id = " + product)).isEqualTo(1);
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("4");

        Session sales = login(mockMvc, "kinhdoanh.hn");
        send(sales, post(DOCS), body, newKey()).andExpect(status().isForbidden());
        Session otherBranch = login(mockMvc, "kho.hcm");
        mockMvc.perform(as(otherBranch, get(DOCS + "/" + receipt))).andExpect(status().isNotFound());
        mockMvc.perform(get(DOCS + "/" + receipt)).andExpect(status().isUnauthorized());

        long issue = create(manager, "PXK", warehouse, List.of(issueLine(product, 1, null, null)));
        action(sales, issue, "post", null).andExpect(status().isForbidden());
        action(manager, issue, "cancel", "{\"reason\":\"Lập nhầm\"}").andExpect(status().isOk());
        action(manager, issue, "post", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("BR-INV-31, D-11: đang kiểm kê thì kho không nhập xuất; ghi sổ kiểm kê sinh phiếu điều chỉnh chênh lệch")
    void stockCount() throws Exception {
        long warehouse = warehouse(1);
        long product = product(false, false, false);
        createAndPost(manager, "PNK", warehouse, List.of(receiptLine(product, 5, null, null, null)));

        MvcResult created = send(manager, post("/api/v1/stock-counts"), "{\"warehouseId\":" + warehouse
                + ",\"countDate\":\"2026-10-06\"}", newKey()).andExpect(status().isCreated()).andReturn();
        long countId = jsonValue(created, "$.data.id", Integer.class);
        MvcResult started = send(manager, post("/api/v1/stock-counts/" + countId + "/start"), "", newKey())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COUNTING")).andReturn();
        long lineId = jsonValue(started, "$.data.lines[0].id", Integer.class);

        long blocked = create(manager, "PNK", warehouse, List.of(receiptLine(product, 1, null, null, null)));
        action(manager, blocked, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STOCK_COUNT_IN_PROGRESS"));

        mockMvc.perform(as(manager, put("/api/v1/stock-counts/" + countId + "/counts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"lineId\":" + lineId + ",\"countedQuantity\":3}]}"))
                .andExpect(status().isOk());
        send(manager, post("/api/v1/stock-counts/" + countId + "/post"), "", newKey())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("POSTED"));

        assertThat(onHand(product, warehouse)).isEqualByComparingTo("3");
        assertThat(count("SELECT COUNT(*) FROM inventory_transactions WHERE product_id = " + product
                + " AND transaction_type = 'COUNT_LOSS'")).isEqualTo(1);
        action(manager, blocked, "post", null).andExpect(status().isOk());
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("4");
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("BR-INV-30: chuyển kho khác chi nhánh qua kho đang vận chuyển; chỉ người thuộc kho đích nhận hàng")
    void crossBranchTransfer() throws Exception {
        long source = warehouse(1);
        long destination = warehouse(2);
        long transit = id("SELECT id FROM warehouses WHERE company_id = 1 AND code = 'VC_HN'");
        long product = product(true, true, false);
        createAndPost(manager, "PNK", source, List.of(receiptLine(product, 6, "LT" + product, "2027-08-31", null)));

        String body = document("PCK", source, List.of(issueLine(product, 4, null, null)), "2026-10-07")
                .replace("\"lines\"", "\"destinationWarehouseId\":" + destination + ",\"lines\"");
        long transfer = jsonValue(send(manager, post(DOCS), body, newKey()).andExpect(status().isCreated()).andReturn(),
                "$.data.id", Integer.class);
        action(manager, transfer, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
        assertThat(onHand(product, source)).isEqualByComparingTo("2");
        assertThat(onHand(product, transit)).isEqualByComparingTo("4");

        Session hanoiOnly = login(mockMvc, "thukho.hn");
        action(hanoiOnly, transfer, "receive", "{}").andExpect(status().isForbidden());
        Session hcm = login(mockMvc, "kho.hcm");
        action(hcm, transfer, "receive", "{\"postingDate\":\"2026-10-08\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(onHand(product, transit)).isEqualByComparingTo("0");
        assertThat(onHand(product, destination)).isEqualByComparingTo("4");
        Long lotsAtDestination = jdbc.queryForObject("SELECT COUNT(DISTINCT lot_id) FROM inventory_balances "
                + "WHERE product_id = ? AND warehouse_id = ? AND on_hand_quantity > 0", Long.class, product,
                destination);
        assertThat(lotsAtDestination).isEqualTo(1L);
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("BR mục 5.4, BR-GEN-31, D-53, D-61: giữ chỗ giảm tồn khả dụng; xuất vượt chỉ với chính sách Giám đốc kích hoạt, một chính sách một chứng từ")
    void reservationAndPolicy() throws Exception {
        long warehouse = warehouse(1);
        long product = product(false, false, false);
        createAndPost(manager, "PNK", warehouse, List.of(receiptLine(product, 10, null, null, null)));
        transactions.executeWithoutResult(tx -> reservations.reserve(new ReservationRequest(1L, product,
                warehouse, "TEST", product, null, new BigDecimal("8"))));

        MvcResult summary = mockMvc.perform(as(manager, get("/api/v1/inventory/stock-summaries"))
                        .param("companyId", "1").param("warehouseId", String.valueOf(warehouse))
                        .param("productId", String.valueOf(product)))
                .andExpect(status().isOk()).andReturn();
        assertThat(new BigDecimal(json(summary, "$.data.items[0].availableQuantity").toString()))
                .isEqualByComparingTo("2");

        long issue = create(manager, "PXK", warehouse, List.of(issueLine(product, 5, null, null)));
        action(manager, issue, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        long policy = jsonValue(send(manager, post("/api/v1/stock-issue-policies"), "{\"warehouseId\":" + warehouse
                        + ",\"productId\":" + product + ",\"maxQuantity\":5,\"validFrom\":\"2026-10-01\","
                        + "\"validTo\":\"2026-10-31\",\"reason\":\"Hàng gấp cho bệnh viện\"}", newKey())
                .andExpect(status().isCreated()).andReturn(), "$.data.id", Integer.class);
        send(manager, post("/api/v1/stock-issue-policies/" + policy + "/activate"), "", newKey())
                .andExpect(status().isForbidden());
        send(director, post("/api/v1/stock-issue-policies/" + policy + "/activate"), "", newKey())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        String body = document("PXK", warehouse, List.of(issueLine(product, 5, null, null)), "2026-10-08")
                .replace("\"lines\"", "\"issuePolicyId\":" + policy + ",\"lines\"");
        long withPolicy = jsonValue(send(manager, post(DOCS), body, newKey()).andExpect(status().isCreated()).andReturn(),
                "$.data.id", Integer.class);
        action(manager, withPolicy, "post", null).andExpect(status().isOk());
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM stock_issue_policies WHERE id = ?",
                BigDecimal.class, policy)).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT used_by_document_id FROM stock_issue_policies WHERE id = ?",
                Long.class, policy)).isEqualTo(withPolicy);

        // D-53: mỗi chính sách dùng cho một chứng từ xuất; đã dùng thì không tham chiếu lại, không hủy được.
        send(manager, post(DOCS), body, newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("issuePolicyId"));
        send(manager, post("/api/v1/stock-issue-policies/" + policy + "/cancel"), "{\"reason\":\"Thử hủy\"}",
                newKey()).andExpect(status().isConflict());
        assertLedgerConsistent(product);
    }
}
