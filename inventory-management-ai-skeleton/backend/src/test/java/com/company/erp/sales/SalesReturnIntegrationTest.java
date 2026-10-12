package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Giai đoạn 6E trên MySQL thật: trả hàng bán đúng lô vào CÁCH LY, giảm doanh thu và công nợ, cập nhật số đã giao của đơn
 * (AT-06, BR-SAL-32); giảm giá hàng bán (BR-SAL-36); đổi hàng (BR-SAL-33); đảo (AT-09); tiếp nhận hàng lỗi (BR-SAL-34).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesReturnIntegrationTest extends SalesIntegrationSupport {

    private static final String DELIVERIES = "/api/v1/sales-deliveries";
    private static final String INVOICES = "/api/v1/sales-invoices";
    private static final String RETURNS = "/api/v1/sales-returns";
    private static final String COMPLAINTS = "/api/v1/sales-complaints";

    @Autowired
    SalesReturnIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("AT-06, BR-SAL-32, 33, 36, AT-09: trả hàng đúng lô vào cách ly, giảm doanh thu / công nợ; giảm giá; đổi; đảo")
    void returnDiscountExchange() throws Exception {
        long item = newProduct(true, false);
        String lot = unique("L");
        stockIn(item, "10", lot, "2028-12-31", null);
        long clinic = partner("KH_PK01");
        long vat = id("SELECT id FROM tax_rates WHERE company_id = 1 AND code = 'VAT10'");
        long order = jsonId(send(manager, post(ORDERS), "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"productId\":" + item
                + ",\"unitId\":" + unit("CAI") + ",\"quantity\":5,\"unitPrice\":100000,\"taxRateId\":" + vat + "}]}",
                newKey()).andExpect(status().isCreated()).andReturn());
        submitAndApproveOrder(order);
        long orderLine = id("SELECT id FROM sales_order_lines WHERE order_id = " + order);
        long delivery = jsonId(send(keeper, post(DELIVERIES), "{\"orderId\":" + order + ",\"documentDate\":\"" + DATE
                + "\",\"lines\":[{\"orderLineId\":" + orderLine + ",\"quantity\":5}]}", newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, DELIVERIES, delivery, "submit", null);
        act(keeper, DELIVERIES, delivery, "post", null);
        act(courier, DELIVERIES, delivery, "confirm", "{\"receivedDate\":\"" + DATE + "\"}");
        long deliveryLine = id("SELECT id FROM sales_delivery_lines WHERE delivery_id = " + delivery);
        long invoice = jsonId(send(clerk, post(INVOICES), "{\"branchId\":1,\"customerId\":" + clinic
                + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"deliveryLineId\":" + deliveryLine
                + ",\"quantity\":5}]}", newKey()).andExpect(status().isCreated()).andReturn());
        act(clerk, INVOICES, invoice, "submit", null);
        act(clerk, INVOICES, invoice, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        long invoiceLine = id("SELECT id FROM sales_invoice_lines WHERE invoice_id = " + invoice);
        mockMvc.perform(as(manager, get(ORDERS + "/" + order))).andExpect(jsonPath("$.data.status").value("COMPLETED"));

        send(clerk, post(RETURNS), returnBody("TLB", clinic, invoice, "{\"invoiceLineId\":" + invoiceLine
                + ",\"lotNo\":\"" + lot + "\",\"quantity\":6}"), newKey()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        send(clerk, post(RETURNS), returnBody("TLB", clinic, invoice, "{\"invoiceLineId\":" + invoiceLine
                + ",\"lotNo\":\"KHONG_CO\",\"quantity\":1}"), newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        long goodsReturn = jsonId(send(clerk, post(RETURNS), returnBody("TLB", clinic, invoice, "{\"invoiceLineId\":"
                + invoiceLine + ",\"lotNo\":\"" + lot + "\",\"quantity\":2}"), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(220000))
                .andExpect(jsonPath("$.data.lines[0].qualityStatus").value("QUARANTINE")).andReturn());
        act(clerk, RETURNS, goodsReturn, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, RETURNS, goodsReturn, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty());
        assertThat(quarantined(lot)).isEqualByComparingTo("2");
        assertThat(receivable(invoice)).isEqualByComparingTo("330000");
        assertThat(journal("TLB", goodsReturn)).containsExactly("N:5111:200000", "C:131:200000", "N:3331:20000",
                "C:131:20000");
        mockMvc.perform(as(manager, get(ORDERS + "/" + order)))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.lines[0].returnedQuantity").value(2))
                .andExpect(jsonPath("$.data.lines[0].outstandingQuantity").value(2));
        act(chief, INVOICES, invoice, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        act(manager, RETURNS, goodsReturn, "create-exchange-delivery", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.sourceReturnId").value(goodsReturn))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(2));

        long discount = jsonId(send(clerk, post(RETURNS), returnBody("GGB", clinic, invoice, "{\"invoiceLineId\":"
                + invoiceLine + ",\"amount\":50000}"), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(55000)).andReturn());
        act(clerk, RETURNS, discount, "submit", null);
        act(clerk, RETURNS, discount, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(receivable(invoice)).isEqualByComparingTo("275000");
        assertThat(journal("GGB", discount)).containsExactly("N:5111:50000", "C:131:50000", "N:3331:5000",
                "C:131:5000");

        act(clerk, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Nhầm\"}").andExpect(status().isForbidden());
        act(chief, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(quarantined(lot)).isEqualByComparingTo("0");
        assertThat(receivable(invoice)).isEqualByComparingTo("495000");
        mockMvc.perform(as(manager, get(ORDERS + "/" + order))).andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("BR-SAL-34, AT-10: tiếp nhận hàng lỗi, kiểm tra, quyết định; nhân viên kinh doanh không quyết định được")
    void complaint() throws Exception {
        long complaint = jsonId(send(seller, post(COMPLAINTS), "{\"branchId\":1,\"customerId\":" + partner("KH_PK01")
                + ",\"receivedDate\":\"" + DATE + "\",\"productId\":" + product("VT_ONG_01")
                + ",\"lotNo\":\"L01\",\"quantity\":1,\"description\":\"Ống nứt khi mở hộp\"}", newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("OPEN")).andReturn());
        act(seller, COMPLAINTS, complaint, "inspect", "{\"result\":\"Lỗi\"}").andExpect(status().isForbidden());
        act(manager, COMPLAINTS, complaint, "inspect", "{\"result\":\"Lỗi do vận chuyển\"}")
                .andExpect(jsonPath("$.data.status").value("INSPECTED"));
        act(manager, COMPLAINTS, complaint, "resolve", "{\"decision\":\"EXCHANGE\",\"note\":\"Đổi hộp mới\"}")
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.decision").value("EXCHANGE"));
        act(manager, COMPLAINTS, complaint, "cancel", "{\"reason\":\"Thử\"}").andExpect(status().isConflict());
        mockMvc.perform(as(seller, get(COMPLAINTS + "/" + complaint))).andExpect(status().isOk());
    }

    private static String returnBody(String type, long customer, Long invoice, String line) {
        return "{\"documentType\":\"" + type + "\",\"branchId\":1,\"customerId\":" + customer
                + (invoice == null ? "" : ",\"salesInvoiceId\":" + invoice) + ",\"documentDate\":\"" + DATE
                + "\",\"lines\":[" + line + "]}";
    }

    private BigDecimal quarantined(String lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(b.on_hand_quantity), 0) FROM inventory_balances b "
                + "JOIN inventory_lots l ON l.id = b.lot_id WHERE l.lot_no = ? AND b.quality_status = 'QUARANTINE'",
                BigDecimal.class, lot);
    }

    private BigDecimal receivable(long invoice) {
        return jdbc.queryForObject("SELECT original_amount - settled_amount FROM debt_items WHERE source_type = 'HDB' "
                + "AND source_id = ?", BigDecimal.class, invoice);
    }

    private List<String> journal(String type, long source) {
        List<String> lines = jdbc.queryForList("""
                SELECT CONCAT(IF(l.debit_amount > 0, 'N', 'C'), ':', a.code, ':',
                              CAST(l.debit_amount + l.credit_amount AS DECIMAL(19,0)))
                FROM journal_entry_lines l
                JOIN journal_entries e ON e.id = l.journal_entry_id
                JOIN chart_of_accounts a ON a.id = l.account_id
                WHERE e.source_type = ? AND e.source_id = ? AND e.reversal_of_id IS NULL
                ORDER BY l.line_no
                """, String.class, type, source);
        BigDecimal net = jdbc.queryForObject("SELECT COALESCE(SUM(l.debit_amount) - SUM(l.credit_amount), 0) FROM "
                + "journal_entry_lines l JOIN journal_entries e ON e.id = l.journal_entry_id WHERE "
                + "e.source_type = ? AND e.source_id = ?", BigDecimal.class, type, source);
        assertThat(net).isEqualByComparingTo("0");
        return lines;
    }
}
