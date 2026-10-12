package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Giai đoạn 5C trên MySQL thật: hóa đơn mua theo nhiều phiếu nhận hàng và dịch vụ (BR-PUR-09, 16), chống trùng,
 * hàng về trước hóa đơn về sau với chênh lệch giá (BR-PUR-10), cảnh báo đối chiếu ba chiều (BR-PUR-11), công nợ phải
 * trả và bút toán (D-34), đảo (AT-09). Sau ghi sổ: bút toán cân, số đã nhận hóa đơn khớp.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseInvoiceIntegrationTest extends PurchasingIntegrationSupport {

    private static final String RECEIPTS = "/api/v1/purchase-receipts";
    private static final String INVOICES = "/api/v1/purchase-invoices";

    private Session keeper;
    private Session clerk;
    private Session chief;

    @Autowired
    PurchaseInvoiceIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @BeforeEach
    void loginAccounting() throws Exception {
        keeper = login(mockMvc, "thukho.hn");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
    }

    @Test
    @DisplayName("BR-PUR-09..11, 16, D-34: một hóa đơn hai phiếu nhận + dịch vụ; công nợ, bút toán, chênh lệch giá; chống trùng; đảo")
    void invoiceReceiptsAndService() throws Exception {
        long supplier = partner("NCC_01");
        long tube = product("VT_ONG_01");
        long service = product("DV_VC");
        long box = unit("HOP");
        long order = jsonId(send(buyer, post(ORDERS), order(supplier, warehouse("KHO_HN"), List.of(
                orderLine(tube, box, "3", "500000", null, taxRate("VAT10"), null))), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(buyer, ORDERS, order, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        long orderLine = id("SELECT id FROM purchase_order_lines WHERE order_id = " + order);
        long first = receive(order, orderLine, "2");
        long second = receive(order, orderLine, "1");
        long firstLine = id("SELECT id FROM purchase_receipt_lines WHERE receipt_id = " + first);
        long secondLine = id("SELECT id FROM purchase_receipt_lines WHERE receipt_id = " + second);

        MvcResult open = mockMvc.perform(as(clerk, get(INVOICES + "/invoiceable-lines")).param("branchId", "1")
                .param("supplierId", String.valueOf(supplier))).andExpect(status().isOk()).andReturn();
        List<Number> remaining = json(open, "$.data[?(@.receiptLineId == " + firstLine + ")].remainingQuantity");
        assertThat(remaining).hasSize(1);
        assertThat(new BigDecimal(remaining.get(0).toString())).isEqualByComparingTo("2");

        String series = unique("C26T");
        String body = invoice(supplier, series, "0000123", List.of(goods(firstLine, "2", "520000"),
                goods(secondLine, "1", "500000"),
                "{\"productId\":" + service + ",\"unitId\":" + unit("CAI") + ",\"quantity\":1,\"unitPrice\":300000}"));
        long invoice = jsonId(send(clerk, post(INVOICES), body, newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(1840000))
                .andExpect(jsonPath("$.data.taxAmount").value(184000))
                .andExpect(jsonPath("$.data.totalAmount").value(2024000))
                .andExpect(jsonPath("$.data.dueDate").value("2026-11-04"))
                .andExpect(jsonPath("$.data.matchWarnings.length()").value(1)).andReturn());
        send(clerk, post(INVOICES), body, newKey()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
        act(clerk, INVOICES, invoice, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, INVOICES, invoice, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));

        Map<String, Object> debt = jdbc.queryForMap("SELECT debt_type, original_amount, due_date, status FROM "
                + "debt_items WHERE source_type = 'HDM' AND source_id = ?", invoice);
        assertThat(debt).containsEntry("debt_type", "PAYABLE").containsEntry("status", "OPEN");
        assertThat((BigDecimal) debt.get("original_amount")).isEqualByComparingTo("2024000");
        assertThat(journal(invoice)).containsExactly("N:1561:1040000", "C:331:1040000", "N:1561:500000",
                "C:331:500000", "N:6422:300000", "C:331:300000", "N:1331:184000", "C:331:184000");
        assertThat(costTotal(firstLine)).isEqualByComparingTo("40000");
        assertThat(invoiced("purchase_receipt_lines", firstLine)).isEqualByComparingTo("200");
        assertThat(invoiced("purchase_order_lines", orderLine)).isEqualByComparingTo("300");
        send(clerk, post(INVOICES), invoice(supplier, unique("C26T"), "1", List.of(goods(firstLine, "1", "500000"))),
                newKey()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        act(keeper, RECEIPTS, second, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        act(clerk, INVOICES, invoice, "reverse", "{\"reason\":\"Sai giá\"}").andExpect(status().isForbidden());
        act(chief, INVOICES, invoice, "reverse", "{\"reason\":\"Sai giá\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(jdbc.queryForObject("SELECT status FROM debt_items WHERE source_type = 'HDM' AND source_id = ?",
                String.class, invoice)).isEqualTo("REVERSED");
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'HDM' AND source_id = " + invoice
                + " AND reversal_of_id IS NOT NULL")).isEqualTo(1);
        assertThat(costTotal(firstLine)).isEqualByComparingTo("0");
        assertThat(invoiced("purchase_receipt_lines", firstLine)).isEqualByComparingTo("0");
        assertThat(invoiced("purchase_order_lines", orderLine)).isEqualByComparingTo("0");
        send(clerk, post(INVOICES), body, newKey()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("BR-PUR-09, 16: hàng quản lý tồn phải theo phiếu nhận hàng; dòng phiếu nhận khác nhà cung cấp bị chặn")
    void validation() throws Exception {
        long supplier = partner("NCC_01");
        send(clerk, post(INVOICES), invoice(supplier, unique("C26T"), "9", List.of("{\"productId\":"
                + product("VT_ONG_01") + ",\"unitId\":" + unit("CAI") + ",\"quantity\":1,\"unitPrice\":1000}")),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].receiptLineId"));
        send(clerk, post(INVOICES), invoice(supplier, unique("C26T"), "9", List.of(goods(-1L, "1", "1000"))),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].receiptLineId"));
    }

    private long receive(long order, long orderLine, String quantity) throws Exception {
        return jsonId(send(keeper, post(RECEIPTS + "/receive-and-stock"), "{\"purchaseOrderId\":" + order
                + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"orderLineId\":" + orderLine + ",\"quantity\":"
                + quantity + ",\"inspectionResult\":\"ACCEPTED\",\"lotNo\":\"" + unique("L")
                + "\",\"expiryDate\":\"2028-01-31\"}]}", newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("POSTED")).andReturn());
    }

    private static String goods(long receiptLine, String quantity, String price) {
        return "{\"receiptLineId\":" + receiptLine + ",\"quantity\":" + quantity + ",\"unitPrice\":" + price + "}";
    }

    private static String invoice(long supplier, String series, String number, List<String> lines) {
        return "{\"branchId\":1,\"supplierId\":" + supplier + ",\"documentDate\":\"" + DATE + "\",\"invoiceSeries\":\""
                + series + "\",\"invoiceNo\":\"" + number + "\",\"invoiceDate\":\"" + DATE + "\",\"lines\":["
                + String.join(",", lines) + "]}";
    }

    private BigDecimal costTotal(long receiptLine) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM purchase_cost_entries WHERE "
                + "receipt_line_id = ?", BigDecimal.class, receiptLine);
    }

    private BigDecimal invoiced(String table, long line) {
        return jdbc.queryForObject("SELECT invoiced_quantity FROM " + table + " WHERE id = ?", BigDecimal.class, line);
    }

    /** Dòng bút toán gốc "N|C:tài khoản:số tiền"; khẳng định cân Nợ = Có. */
    private List<String> journal(long invoice) {
        List<String> lines = jdbc.queryForList("""
                SELECT CONCAT(IF(l.debit_amount > 0, 'N', 'C'), ':', a.code, ':',
                              CAST(l.debit_amount + l.credit_amount AS DECIMAL(19,0)))
                FROM journal_entry_lines l
                JOIN journal_entries e ON e.id = l.journal_entry_id
                JOIN chart_of_accounts a ON a.id = l.account_id
                WHERE e.source_type = 'HDM' AND e.source_id = ? AND e.reversal_of_id IS NULL
                ORDER BY l.line_no
                """, String.class, invoice);
        BigDecimal net = jdbc.queryForObject("SELECT COALESCE(SUM(l.debit_amount) - SUM(l.credit_amount), 0) FROM "
                + "journal_entry_lines l JOIN journal_entries e ON e.id = l.journal_entry_id WHERE "
                + "e.source_type = 'HDM' AND e.source_id = ?", BigDecimal.class, invoice);
        assertThat(net).isEqualByComparingTo("0");
        return lines;
    }
}
