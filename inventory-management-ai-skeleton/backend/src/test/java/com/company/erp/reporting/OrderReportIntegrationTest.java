package com.company.erp.reporting;

import static com.company.erp.support.ApiTestSupport.unique;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Báo cáo mua / bán đợt 10B (BR-RPT-25, 26, ADR-18) trên MySQL thật: số liệu khớp chứng từ, trả lại, phiếu thu
 * (AT-12, AT-08); lịch sử giá, hàng lỗi truy NCC qua lô; tồn so với đặt chưa giao; quyền (AT-10).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class OrderReportIntegrationTest extends ReportIntegrationSupport {

    @Autowired
    OrderReportIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-RPT-26, AT-08, AT-12: đơn bán đã trả lại một phần, thu một phần; sổ nhật ký; tồn so với đặt chưa giao")
    void salesReports() throws Exception {
        long item = newProduct("CONSUMABLE");
        String lot = unique("L");
        stockIn(item, "10", lot);
        long customer = customer();
        long sold = postedOrder(customer, item, "4");
        postedReturn(customer, sold, lot, "1");
        long debtItem = id("SELECT id FROM debt_items WHERE source_type = 'DBH' AND source_id = " + sold);
        long cash = id("SELECT id FROM cash_accounts WHERE company_id = 1 AND branch_id = 1 AND account_type = 'CASH' "
                + "AND status = 'ACTIVE' ORDER BY id LIMIT 1");
        long receipt = jsonId(send(clerk, post("/api/v1/cash-documents"), "{\"branchId\":1,\"documentType\":\"PT\","
                + "\"purpose\":\"CUSTOMER_RECEIPT\",\"documentDate\":\"" + DATE + "\",\"cashAccountId\":" + cash
                + ",\"partnerId\":" + customer + ",\"allocationMode\":\"MANUAL\",\"amount\":200000,\"lines\":[{"
                + "\"debtItemId\":" + debtItem + ",\"amount\":200000}]}").andExpect(status().isCreated()).andReturn());
        act(cashier, "/api/v1/cash-documents", receipt, "post").andExpect(status().isOk());
        long pending = draftOrder(customer, item, "9", ",\"expectedDate\":\"2026-10-06\"");

        String soldRow = "$.data.rows[?(@.documentId == " + sold + ")]";
        String pendingRow = "$.data.rows[?(@.documentId == " + pending + ")]";
        mockMvc.perform(report(salesManager, "/sales-order-progress").param("customerId", String.valueOf(customer))
                        .param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(soldRow + ".totalAmount").value(contains(400000.0)))
                .andExpect(jsonPath(soldRow + ".returnedAmount").value(contains(100000.0)))
                .andExpect(jsonPath(soldRow + ".paidAmount").value(contains(200000.0)))
                .andExpect(jsonPath(soldRow + ".remainingAmount").value(contains(100000.0)))
                .andExpect(jsonPath(pendingRow + ".status").value(contains("DRAFT")))
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty());
        if (LocalDate.now().isAfter(LocalDate.parse("2026-10-07"))) {
            mockMvc.perform(report(salesManager, "/sales-order-progress").param("customerId", String.valueOf(customer))
                            .param("dateFrom", DATE).param("dateTo", DATE))
                    .andExpect(jsonPath(pendingRow + ".overdueDays").value(not(empty())));
        }

        String journalRow = "$.data.rows[?(@.documentId == " + sold + " && @.documentType == 'DBH')]";
        mockMvc.perform(report(salesManager, "/sales-journal").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(journalRow + ".receivable").value(contains(400000.0)))
                .andExpect(jsonPath(journalRow + ".goodsAmount").value(contains(400000.0)))
                .andExpect(jsonPath(journalRow + ".serviceAmount").value(contains(0.0)))
                .andExpect(jsonPath("$.data.rows[?(@.documentType == 'TLB' && @.customerCode == 'KH_PK01')]")
                        .isNotEmpty());

        mockMvc.perform(report(salesManager, "/stock-backlog").param("productIds", String.valueOf(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].ordered").value(9))
                .andExpect(jsonPath("$.data.rows[0].orderCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].onHand").value(7))
                .andExpect(jsonPath("$.data.rows[0].shortage").value(2));
    }

    @Test
    @DisplayName("BR-RPT-25, AT-12: tình hình đơn mua, lịch sử giá theo đơn vị cơ sở, hàng lỗi truy NCC qua lô")
    void purchaseReports() throws Exception {
        long item = newProduct("CONSUMABLE");
        long supplier = id("SELECT id FROM partners WHERE company_id = 1 AND code = 'NCC_01'");
        String lot = unique("L");
        long first = postedPurchase(supplier, item, "5", "500000", lot);
        postedPurchase(supplier, item, "2", "450000", unique("L"));

        mockMvc.perform(report(buyer, "/purchase-order-progress").param("branchId", "1")
                        .param("supplierId", String.valueOf(supplier)).param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[?(@.documentId == " + first + ")].totalAmount")
                        .value(contains(2500000.0)))
                .andExpect(jsonPath("$.data.rows[?(@.documentId == " + first + ")].remainingAmount")
                        .value(contains(2500000.0)))
                .andExpect(jsonPath("$.data.rows[?(@.documentId == " + first + ")].paidAmount").value(contains(0.0)));

        mockMvc.perform(report(buyer, "/purchase-price-history").param("productIds", String.valueOf(item))
                        .param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.summaries[0].purchaseCount").value(2))
                .andExpect(jsonPath("$.data.summaries[0].minPrice").value(450000))
                .andExpect(jsonPath("$.data.summaries[0].maxPrice").value(500000))
                .andExpect(jsonPath("$.data.summaries[0].lastPrice").value(450000));

        long customer = customer();
        postedOrder(customer, item, "1");
        long complaint = jsonId(send(salesManager, post("/api/v1/sales-complaints"), "{\"branchId\":1,\"customerId\":"
                + customer + ",\"receivedDate\":\"" + DATE + "\",\"productId\":" + item + ",\"lotNo\":\"" + lot
                + "\",\"quantity\":1,\"description\":\"Hộp móp\"}").andExpect(status().isCreated()).andReturn());
        mockMvc.perform(report(buyer, "/supplier-defects").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[?(@.complaintId == " + complaint + ")].supplierId")
                        .value(contains((int) supplier)));
    }

    @Test
    @DisplayName("AT-10, BR-RPT-12: không quyền 403, chưa đăng nhập 401, chi nhánh ngoài phạm vi và điều kiện sai 400")
    void accessAndValidation() throws Exception {
        mockMvc.perform(report(keeper, "/purchase-order-progress").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(report(buyer, "/sales-journal").param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(REPORTS + "/sales-journal").param("companyId", "1").param("dateFrom", DATE)
                        .param("dateTo", DATE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(report(salesManager, "/sales-order-progress").param("branchId", "2")
                        .param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        mockMvc.perform(report(salesManager, "/sales-order-progress").param("status", "UNKNOWN")
                        .param("dateFrom", DATE).param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("status"));
        mockMvc.perform(report(buyer, "/supplier-defects").param("dateFrom", "2026-10-31").param("dateTo", DATE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dateFrom"));
    }
}
