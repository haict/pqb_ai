package com.company.erp.inventory;

import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Giai đoạn 3 - kiểm soát chất lượng và an toàn tồn kho trên MySQL thật: chuyển trạng thái chất lượng (BR-INV-03,
 * D-09), khóa / mở khóa lô (BR-INV-23), thu hồi (BR-INV-42), cảnh báo tồn (BR-INV-40, D-52), điều kiện khóa kỳ kho
 * (BR-SYS-31), chuyển kho cùng chi nhánh và đảo (BR-INV-30, AT-09). Sau mỗi kịch bản ghi sổ: số dư = tổng sổ nhật ký.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class InventoryControlIntegrationTest extends InventoryIntegrationSupport {

    private static final String RECALLS = "/api/v1/recalls";

    private final BusinessCalendar calendar;

    @Autowired
    InventoryControlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, BusinessCalendar calendar) {
        super(mockMvc, jdbc);
        this.calendar = calendar;
    }

    @Test
    @DisplayName("BR-INV-03, D-09: thuốc thử nhập ở trạng thái chờ kiểm tra, không xuất được cho tới khi phiếu chuyển trạng thái được duyệt và ghi sổ")
    void qualityStatusChange() throws Exception {
        long warehouse = warehouse(1);
        long reagent = product("REAGENT", true, true, false);
        createAndPost(manager, "PNK", warehouse, List.of(receiptLine(reagent, 4, "LQ" + reagent, "2027-09-30", null)));
        assertThat(onHand(reagent, warehouse, "PENDING_INSPECTION")).isEqualByComparingTo("4");

        long blockedIssue = create(manager, "PXK", warehouse, List.of(issueLine(reagent, 1, null, null)));
        submitAndApprove(blockedIssue);
        action(manager, blockedIssue, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        send(manager, post(DOCS), document("PCL", warehouse,
                List.of(statusLine(reagent, 3, "PENDING_INSPECTION", "PENDING_INSPECTION")), "2026-10-06"), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(endsWith("targetQualityStatus")));
        send(manager, post(DOCS), document("PCL", warehouse,
                List.of(statusLine(reagent, 3, "EXPIRED", "PASSED")), "2026-10-06"), newKey())
                .andExpect(status().isBadRequest());

        long change = create(manager, "PCL", warehouse, List.of(statusLine(reagent, 3, "PENDING_INSPECTION",
                "PASSED")), "2026-10-06");
        submit(change);
        assertThat(docStatus(change)).isEqualTo("SUBMITTED");
        action(manager, change, "approve", "{}").andExpect(status().isForbidden());
        approve(director, change);
        action(manager, change, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(onHand(reagent, warehouse, "PASSED")).isEqualByComparingTo("3");
        assertThat(onHand(reagent, warehouse, "PENDING_INSPECTION")).isEqualByComparingTo("1");

        action(manager, blockedIssue, "post", null).andExpect(status().isOk());
        assertThat(onHand(reagent, warehouse, "PASSED")).isEqualByComparingTo("2");
        assertThat(onHand(reagent, warehouse)).isEqualByComparingTo("3");
        assertLedgerConsistent(reagent);
    }

    @Test
    @DisplayName("BR-INV-23: khóa lô có lý do thì không xuất được, tồn khả dụng giảm; mở khóa xuất lại được; sai quyền 403")
    void lotBlocking() throws Exception {
        long warehouse = warehouse(1);
        long product = product(true, true, false);
        createAndPost(manager, "PNK", warehouse, List.of(receiptLine(product, 10, "LK" + product, "2027-12-31", null)));
        long lot = lotId(product, "LK" + product);

        Session sales = login(mockMvc, "kinhdoanh.hn");
        send(sales, post("/api/v1/inventory/lots/" + lot + "/block"), "{\"reason\":\"Nghi lỗi\"}", newKey())
                .andExpect(status().isForbidden());
        send(manager, post("/api/v1/inventory/lots/" + lot + "/block"), "{\"reason\":\" \"}", newKey())
                .andExpect(status().isBadRequest());
        send(manager, post("/api/v1/inventory/lots/" + lot + "/block"), "{\"reason\":\"Nghi lỗi bao bì\"}", newKey())
                .andExpect(status().isOk());
        assertThat(lotBlocked(lot)).isTrue();
        assertThat(available(product, warehouse)).isEqualByComparingTo("0");

        long issue = create(manager, "PXK", warehouse, List.of(issueLine(product, 2, lot, null)));
        submitAndApprove(issue);
        action(manager, issue, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LOT_NOT_SELLABLE"));
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("10");

        send(manager, post("/api/v1/inventory/lots/" + lot + "/unblock"), "{\"reason\":\"Đã kiểm tra, đạt\"}",
                newKey()).andExpect(status().isOk());
        assertThat(lotBlocked(lot)).isFalse();
        action(manager, issue, "post", null).andExpect(status().isOk());
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("8");
        assertThat(count("SELECT COUNT(*) FROM sys_audit_logs WHERE entity_id = " + lot
                + " AND document_no = 'LK" + product + "' AND action IN ('BLOCK', 'UNBLOCK')")).isEqualTo(2);
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("BR-INV-42: thu hồi được duyệt thì khóa lô; theo dõi khách đã nhận / đã trả; đóng thu hồi vẫn giữ khóa")
    void recall() throws Exception {
        long warehouse = warehouse(1);
        long product = product(true, true, false);
        long customer = id("SELECT id FROM partners WHERE company_id = 1 AND code = 'KH_BV01'");
        createAndPost(manager, "PNK", warehouse, List.of(receiptLine(product, 10, "LH" + product, "2027-12-31", null)));
        long lot = lotId(product, "LH" + product);
        long delivered = createDocument(manager, withHeader(document("PXK", warehouse,
                List.of(issueLine(product, 3, null, null)), "2026-10-05"), "\"partnerId\":" + customer));
        submitApproveAndPost(manager, delivered);

        long recall = jsonValue(send(manager, post(RECALLS), "{\"branchId\":1,\"recallDate\":\"2026-10-07\","
                        + "\"productId\":" + product + ",\"lotId\":" + lot + ",\"reason\":\"Thông báo thu hồi của hãng\"}",
                newKey()).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn(), "$.data.id", Integer.class);
        send(manager, post(RECALLS + "/" + recall + "/submit"), "", newKey())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        assertThat(lotBlocked(lot)).isFalse();
        send(manager, post(RECALLS + "/" + recall + "/approve"), "{}", newKey()).andExpect(status().isForbidden());
        send(director, post(RECALLS + "/" + recall + "/approve"), "{}", newKey())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(lotBlocked(lot)).isTrue();

        long issue = create(manager, "PXK", warehouse, List.of(issueLine(product, 1, null, null)));
        submitAndApprove(issue);
        action(manager, issue, "post", null).andExpect(status().isUnprocessableEntity());

        long returned = createDocument(manager, withHeader(document("PNK", warehouse,
                        List.of(receiptLine(product, 2, "LH" + product, "2027-12-31", null)), "2026-10-08"),
                "\"subType\":\"RECALL_IN\",\"partnerId\":" + customer));
        submitApproveAndPost(manager, returned);
        assertThat(onHand(product, warehouse, "RECALLED")).isEqualByComparingTo("2");

        MvcResult detail = mockMvc.perform(as(manager, get(RECALLS + "/" + recall))).andExpect(status().isOk())
                .andReturn();
        assertThat(decimal(detail, "$.data.customers[0].partnerId")).isEqualByComparingTo(BigDecimal.valueOf(customer));
        assertThat(decimal(detail, "$.data.customers[0].deliveredQuantity")).isEqualByComparingTo("3");
        assertThat(decimal(detail, "$.data.customers[0].recoveredQuantity")).isEqualByComparingTo("2");

        send(director, post(RECALLS + "/" + recall + "/close"), "{\"reason\":\"Đã thu đủ theo thông báo\"}", newKey())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        assertThat(lotBlocked(lot)).isTrue();
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("BR-INV-40, D-52: cảnh báo lô sắp hết hạn trong ngưỡng tham số và tồn dưới định mức tối thiểu")
    void stockAlerts() throws Exception {
        long warehouse = warehouse(1);
        long product = product(true, true, false);
        LocalDate today = calendar.today();
        createAndPost(manager, "PNK", warehouse, List.of(
                receiptLine(product, 10, "LS" + product, today.plusDays(30).toString(), null),
                receiptLine(product, 5, "LF" + product, today.plusDays(400).toString(), null)));
        send(admin, put("/api/v1/products/" + product + "/stock-levels"),
                "{\"levels\":[{\"warehouseId\":" + warehouse + ",\"minQuantity\":50}]}", newKey())
                .andExpect(status().isOk());

        MvcResult alerts = mockMvc.perform(as(manager, get("/api/v1/inventory/alerts"))
                        .param("companyId", "1").param("warehouseId", String.valueOf(warehouse)))
                .andExpect(status().isOk()).andReturn();
        List<String> expiringLots = json(alerts, "$.data[?(@.alertType == 'EXPIRING')].lotNo");
        assertThat(expiringLots).containsExactly("LS" + product);
        List<Object> days = json(alerts, "$.data[?(@.alertType == 'EXPIRING')].daysToExpiry");
        assertThat(((Number) days.get(0)).longValue()).isEqualTo(30L);
        List<Object> belowMin = json(alerts, "$.data[?(@.alertType == 'BELOW_MIN' && @.productId == " + product
                + ")].onHandQuantity");
        assertThat(belowMin).hasSize(1);
        assertThat(new BigDecimal(belowMin.get(0).toString())).isEqualByComparingTo("15");

        Session otherBranch = login(mockMvc, "kho.hcm");
        mockMvc.perform(as(otherBranch, get("/api/v1/inventory/alerts")).param("companyId", "1")
                .param("warehouseId", String.valueOf(warehouse))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SYS-31: không khóa được kỳ kho khi còn chứng từ đã duyệt hoặc đang vận chuyển chưa ghi sổ trong kỳ")
    void periodCloseCheck() throws Exception {
        Session accountant = login(mockMvc, "ketoantruong");
        send(accountant, post("/api/v1/fiscal-periods/generate"), "{\"companyId\":1,\"year\":2070}", newKey())
                .andExpect(status().isOk());
        long january = id("SELECT id FROM fiscal_periods WHERE company_id = 1 AND period_year = 2070 "
                + "AND period_month = 1");
        long source = warehouse(1);
        long destination = warehouse(2);
        long product = product(false, false, false);

        long receipt = create(manager, "PNK", source, List.of(receiptLine(product, 5, null, null, null)),
                "2070-01-10");
        submit(receipt);
        lockInventory(accountant, january).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_CLOSE_PRECONDITION"))
                .andExpect(jsonPath("$.message").value(containsString(documentNo(receipt))));
        action(manager, receipt, "post", null).andExpect(status().isOk());

        long transfer = createDocument(manager, withHeader(document("PCK", source,
                List.of(issueLine(product, 2, null, null)), "2070-01-12"), "\"destinationWarehouseId\":" + destination));
        submit(transfer);
        action(manager, transfer, "post", null).andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
        lockInventory(accountant, january).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString(documentNo(transfer))));

        action(login(mockMvc, "kho.hcm"), transfer, "receive", "{\"postingDate\":\"2070-01-13\"}")
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        lockInventory(accountant, january).andExpect(status().isOk());
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("BR-INV-30, AT-09: chuyển kho cùng chi nhánh ghi sổ một bước, giữ lô; đảo trả hàng về kho nguồn")
    void sameBranchTransfer() throws Exception {
        long source = warehouse(1);
        long destination = warehouse(1);
        long transit = id("SELECT id FROM warehouses WHERE company_id = 1 AND code = 'VC_HN'");
        long product = product(true, true, false);
        createAndPost(manager, "PNK", source, List.of(receiptLine(product, 6, "LN" + product, "2027-10-31", null)));

        long transfer = createDocument(manager, withHeader(document("PCK", source,
                List.of(issueLine(product, 4, null, null)), "2026-10-07"), "\"destinationWarehouseId\":" + destination));
        submit(transfer);
        action(manager, transfer, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(onHand(product, source)).isEqualByComparingTo("2");
        assertThat(onHand(product, destination)).isEqualByComparingTo("4");
        assertThat(onHand(product, transit)).isEqualByComparingTo("0");
        assertThat(count("SELECT COUNT(DISTINCT lot_id) FROM inventory_balances WHERE product_id = " + product))
                .isEqualTo(1);

        long reversal = reverse(transfer);
        approve(login(mockMvc, "ketoantruong"), reversal);
        action(manager, reversal, "post", null).andExpect(status().isOk());
        assertThat(docStatus(transfer)).isEqualTo("REVERSED");
        assertThat(onHand(product, source)).isEqualByComparingTo("6");
        assertThat(onHand(product, destination)).isEqualByComparingTo("0");
        assertLedgerConsistent(product);
    }

    @Test
    @DisplayName("BR-GEN-53: sửa phiếu nháp thay toàn bộ dòng (thêm, bớt dòng); gửi version cũ thì 409")
    void editDraftLines() throws Exception {
        long warehouse = warehouse(1);
        long product = product(false, false, false);
        long receipt = create(manager, "PNK", warehouse, List.of(receiptLine(product, 1, null, null, null)));
        int version = jdbc.queryForObject("SELECT version FROM inventory_documents WHERE id = ?", Integer.class,
                receipt);
        String twoLines = withHeader(document("PNK", warehouse, List.of(receiptLine(product, 2, null, null, null),
                receiptLine(product, 3, null, null, null)), "2026-10-05"), "\"version\":" + version);
        mockMvc.perform(as(manager, put(DOCS + "/" + receipt)).contentType(MediaType.APPLICATION_JSON)
                        .content(twoLines))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines.length()").value(2));
        mockMvc.perform(as(manager, put(DOCS + "/" + receipt)).contentType(MediaType.APPLICATION_JSON)
                        .content(twoLines))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        int current = jdbc.queryForObject("SELECT version FROM inventory_documents WHERE id = ?", Integer.class,
                receipt);
        mockMvc.perform(as(manager, put(DOCS + "/" + receipt)).contentType(MediaType.APPLICATION_JSON)
                        .content(withHeader(document("PNK", warehouse, List.of(receiptLine(product, 7, null, null,
                                null)), "2026-10-05"), "\"version\":" + current)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lines.length()").value(1));
        assertThat(count("SELECT COUNT(*) FROM inventory_document_lines WHERE document_id = " + receipt)).isEqualTo(1);
        submitApproveAndPost(manager, receipt);
        assertThat(onHand(product, warehouse)).isEqualByComparingTo("7");
    }

    // ---- hỗ trợ ----

    private String statusLine(long product, int quantity, String from, String to) {
        return "{\"productId\":" + product + ",\"unitId\":" + unitId + ",\"quantity\":" + quantity
                + ",\"qualityStatus\":\"" + from + "\",\"targetQualityStatus\":\"" + to + "\"}";
    }

    private ResultActions lockInventory(Session accountant, long periodId) throws Exception {
        return send(accountant, post("/api/v1/fiscal-periods/" + periodId + "/lock"),
                "{\"module\":\"INVENTORY\",\"branchIds\":[1]}", newKey());
    }

    private long lotId(long product, String lotNo) {
        return jdbc.queryForObject("SELECT id FROM inventory_lots WHERE product_id = ? AND lot_no = ?", Long.class,
                product, lotNo);
    }

    private boolean lotBlocked(long lot) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT blocked FROM inventory_lots WHERE id = ?",
                Boolean.class, lot));
    }

    private String documentNo(long documentId) {
        return jdbc.queryForObject("SELECT document_no FROM inventory_documents WHERE id = ?", String.class,
                documentId);
    }

    private BigDecimal available(long product, long warehouse) throws Exception {
        MvcResult summary = mockMvc.perform(as(manager, get("/api/v1/inventory/stock-summaries"))
                        .param("companyId", "1").param("warehouseId", String.valueOf(warehouse))
                        .param("productId", String.valueOf(product)))
                .andExpect(status().isOk()).andReturn();
        return decimal(summary, "$.data.items[0].availableQuantity");
    }

    private static BigDecimal decimal(MvcResult result, String path) throws Exception {
        Object value = json(result, path);
        return new BigDecimal(value.toString());
    }
}
