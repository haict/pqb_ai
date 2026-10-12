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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Giai đoạn 6D trên MySQL thật: hóa đơn bán từ hai phiếu giao và dòng dịch vụ (BR-SAL-30), cảnh báo lập trước khi
 * giao, số hóa đơn điện tử MISA chống trùng (BR-SAL-31), công nợ phải thu và bút toán doanh thu (D-35), đảo (AT-09).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesInvoiceIntegrationTest extends SalesIntegrationSupport {

    static final String DELIVERIES = "/api/v1/sales-deliveries";
    static final String INVOICES = "/api/v1/sales-invoices";

    @Autowired
    SalesInvoiceIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-SAL-30, 31, D-35, AT-09: hóa đơn hai phiếu giao + dịch vụ; HĐĐT chống trùng; công nợ, bút toán; đảo")
    void invoiceDeliveriesAndService() throws Exception {
        long item = newProduct(true, false);
        stockIn(item, "10", unique("L"), "2028-12-31", null);
        long clinic = partner("KH_PK01");
        long vat = id("SELECT id FROM tax_rates WHERE company_id = 1 AND code = 'VAT10'");
        long order = jsonId(send(manager, post(ORDERS), "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"productId\":" + item
                + ",\"unitId\":" + unit("CAI") + ",\"quantity\":5,\"unitPrice\":100000,\"taxRateId\":" + vat
                + "},{\"productId\":" + product("DV_VC") + ",\"unitId\":" + unit("CAI")
                + ",\"quantity\":1,\"unitPrice\":200000}]}", newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.lines[1].serviceLine").value(true))
                .andReturn());
        submitAndApproveOrder(order);
        long goodsLine = id("SELECT id FROM sales_order_lines WHERE order_id = " + order + " AND line_no = 1");
        long serviceLine = id("SELECT id FROM sales_order_lines WHERE order_id = " + order + " AND line_no = 2");
        long first = issued(order, goodsLine, "3");
        act(courier, DELIVERIES, first, "confirm", "{\"receivedDate\":\"" + DATE + "\"}");
        long second = issued(order, goodsLine, "2");
        long firstLine = id("SELECT id FROM sales_delivery_lines WHERE delivery_id = " + first);
        long secondLine = id("SELECT id FROM sales_delivery_lines WHERE delivery_id = " + second);

        mockMvc.perform(as(clerk, get(INVOICES + "/invoiceable-lines")).param("branchId", "1")
                        .param("customerId", String.valueOf(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.deliveryLineId == " + firstLine + ")].remainingQuantity").value(3.0));
        String body = invoice(clinic, List.of(goods(firstLine, "3"), goods(secondLine, "2"),
                "{\"orderLineId\":" + serviceLine + ",\"quantity\":1}"));
        long invoice = jsonId(send(clerk, post(INVOICES), body, newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(700000))
                .andExpect(jsonPath("$.data.taxAmount").value(70000))
                .andExpect(jsonPath("$.data.totalAmount").value(770000))
                .andExpect(jsonPath("$.data.dueDate").value("2026-11-04"))
                .andExpect(jsonPath("$.data.warnings.length()").value(1)).andReturn());
        String series = unique("1C26T");
        act(clerk, INVOICES, invoice, "einvoice-reference", "{\"series\":\"" + series + "\",\"number\":\"0000001\","
                + "\"date\":\"" + DATE + "\",\"taxAuthorityCode\":\"M1-26-ABC\"}")
                .andExpect(jsonPath("$.data.einvoiceNo").value("0000001"));
        act(clerk, INVOICES, invoice, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, INVOICES, invoice, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));

        Map<String, Object> debt = jdbc.queryForMap("SELECT debt_type, original_amount, due_date, invoice_no FROM "
                + "debt_items WHERE source_type = 'HDB' AND source_id = ?", invoice);
        assertThat(debt).containsEntry("debt_type", "RECEIVABLE").containsEntry("invoice_no", series + "-0000001");
        assertThat((BigDecimal) debt.get("original_amount")).isEqualByComparingTo("770000");
        assertThat(journal(invoice)).containsExactly("N:131:300000", "C:5111:300000", "N:131:200000",
                "C:5111:200000", "N:131:200000", "C:5113:200000", "N:131:70000", "C:3331:70000");
        mockMvc.perform(as(manager, get(ORDERS + "/" + order)))
                .andExpect(jsonPath("$.data.lines[0].invoicedQuantity").value(5))
                .andExpect(jsonPath("$.data.lines[1].invoicedQuantity").value(1));

        send(clerk, post(INVOICES), invoice(clinic, List.of("{\"orderLineId\":" + serviceLine + ",\"quantity\":1}")),
                newKey()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        act(chief, DELIVERIES, second, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        act(clerk, INVOICES, invoice, "reverse", "{\"reason\":\"Sai giá\"}").andExpect(status().isForbidden());
        act(chief, INVOICES, invoice, "reverse", "{\"reason\":\"Sai giá\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(jdbc.queryForObject("SELECT status FROM debt_items WHERE source_type = 'HDB' AND source_id = ?",
                String.class, invoice)).isEqualTo("REVERSED");
        mockMvc.perform(as(manager, get(ORDERS + "/" + order)))
                .andExpect(jsonPath("$.data.lines[0].invoicedQuantity").value(0));

        long reissued = jsonId(send(clerk, post(INVOICES), body, newKey()).andExpect(status().isCreated()).andReturn());
        act(clerk, INVOICES, reissued, "einvoice-reference", "{\"series\":\"" + series + "\",\"number\":\"0000001\","
                + "\"date\":\"" + DATE + "\"}").andExpect(status().isOk());
        long duplicate = jsonId(send(clerk, post(INVOICES), invoice(clinic, List.of(goods(firstLine, "1"))), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(clerk, INVOICES, duplicate, "einvoice-reference", "{\"series\":\"" + series + "\",\"number\":\"0000001\","
                + "\"date\":\"" + DATE + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
    }

    private long issued(long order, long orderLine, String quantity) throws Exception {
        long delivery = jsonId(send(keeper, post(DELIVERIES), "{\"orderId\":" + order + ",\"documentDate\":\"" + DATE
                + "\",\"lines\":[{\"orderLineId\":" + orderLine + ",\"quantity\":" + quantity + "}]}", newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, DELIVERIES, delivery, "submit", null);
        act(keeper, DELIVERIES, delivery, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        return delivery;
    }

    static String goods(long deliveryLine, String quantity) {
        return "{\"deliveryLineId\":" + deliveryLine + ",\"quantity\":" + quantity + "}";
    }

    static String invoice(long customer, List<String> lines) {
        return "{\"branchId\":1,\"customerId\":" + customer + ",\"documentDate\":\"" + DATE + "\",\"lines\":["
                + String.join(",", lines) + "]}";
    }

    /** Dòng bút toán gốc "N|C:tài khoản:số tiền"; khẳng định cân Nợ = Có. */
    private List<String> journal(long invoice) {
        List<String> lines = jdbc.queryForList("""
                SELECT CONCAT(IF(l.debit_amount > 0, 'N', 'C'), ':', a.code, ':',
                              CAST(l.debit_amount + l.credit_amount AS DECIMAL(19,0)))
                FROM journal_entry_lines l
                JOIN journal_entries e ON e.id = l.journal_entry_id
                JOIN chart_of_accounts a ON a.id = l.account_id
                WHERE e.source_type = 'HDB' AND e.source_id = ? AND e.reversal_of_id IS NULL
                ORDER BY l.line_no
                """, String.class, invoice);
        BigDecimal net = jdbc.queryForObject("SELECT COALESCE(SUM(l.debit_amount) - SUM(l.credit_amount), 0) FROM "
                + "journal_entry_lines l JOIN journal_entries e ON e.id = l.journal_entry_id WHERE "
                + "e.source_type = 'HDB' AND e.source_id = ?", BigDecimal.class, invoice);
        assertThat(net).isEqualByComparingTo("0");
        return lines;
    }
}
