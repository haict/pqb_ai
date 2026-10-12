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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Giai đoạn 5D trên MySQL thật: trả hàng nhà cung cấp chưa / đã có hóa đơn (BR-PUR-14, đúng lô, giảm tồn và công nợ),
 * giảm giá hàng mua, phân bổ chi phí mua theo giá trị (BR-PUR-12, 16), đảo và chặn đảo khi có chứng từ phụ thuộc
 * (AT-09). Sau ghi sổ: bút toán cân, tồn theo lô, số còn nợ của hóa đơn khớp.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseReturnIntegrationTest extends PurchasingIntegrationSupport {

    private static final String RECEIPTS = "/api/v1/purchase-receipts";
    private static final String INVOICES = "/api/v1/purchase-invoices";
    private static final String RETURNS = "/api/v1/purchase-returns";
    private static final String ALLOCATIONS = "/api/v1/purchase-cost-allocations";

    private Session keeper;
    private Session clerk;
    private Session chief;

    @Autowired
    PurchaseReturnIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @BeforeEach
    void loginAccounting() throws Exception {
        keeper = login(mockMvc, "thukho.hn");
        clerk = login(mockMvc, "ketoancongno");
        chief = login(mockMvc, "ketoantruong");
    }

    @Test
    @DisplayName("BR-PUR-14, AT-09: trả hàng chưa / đã có hóa đơn, giảm giá; tồn theo lô, công nợ, bút toán; đảo")
    void returnsAndDiscount() throws Exception {
        long supplier = partner("NCC_01");
        long order = approvedOrder(supplier, "5");
        long orderLine = id("SELECT id FROM purchase_order_lines WHERE order_id = " + order);
        String lot = unique("L");
        long receipt = receive(order, orderLine, "5", lot);
        long receiptLine = id("SELECT id FROM purchase_receipt_lines WHERE receipt_id = " + receipt);
        assertThat(lotOnHand(lot)).isEqualByComparingTo("500");

        MvcResult open = mockMvc.perform(as(clerk, get(RETURNS + "/returnable-lines")).param("branchId", "1")
                .param("supplierId", String.valueOf(supplier))).andExpect(status().isOk()).andReturn();
        List<Object> remaining = json(open, "$.data[?(@.receiptLineId == " + receiptLine + ")].remainingQuantity");
        assertThat(new BigDecimal(remaining.get(0).toString())).isEqualByComparingTo("5");

        long uninvoiced = jsonId(send(clerk, post(RETURNS), returnBody("THN", supplier, null,
                "{\"receiptLineId\":" + receiptLine + ",\"quantity\":1}"), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(500000)).andReturn());
        act(clerk, RETURNS, uninvoiced, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, RETURNS, uninvoiced, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty());
        assertThat(lotOnHand(lot)).isEqualByComparingTo("400");
        assertThat(id("SELECT COUNT(*) FROM debt_items WHERE source_type = 'THN' AND source_id = " + uninvoiced))
                .isZero();
        send(clerk, post(RETURNS), returnBody("THN", supplier, null, "{\"receiptLineId\":" + receiptLine
                + ",\"quantity\":5}"), newKey()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));

        long invoice = postedInvoice(supplier, receiptLine, "4");
        long invoiceLine = id("SELECT id FROM purchase_invoice_lines WHERE invoice_id = " + invoice);
        long goodsReturn = jsonId(send(clerk, post(RETURNS), returnBody("THN", supplier, invoice,
                "{\"invoiceLineId\":" + invoiceLine + ",\"quantity\":2}"), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(1100000)).andReturn());
        act(clerk, RETURNS, goodsReturn, "submit", null);
        act(clerk, RETURNS, goodsReturn, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("200");
        assertThat(debtRemaining(invoice)).isEqualByComparingTo("1100000");
        assertThat(journal("THN", goodsReturn)).containsExactly("N:331:1000000", "C:1561:1000000", "N:331:100000",
                "C:1331:100000");
        act(chief, INVOICES, invoice, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));
        act(keeper, RECEIPTS, receipt, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        act(clerk, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Sai\"}").andExpect(status().isForbidden());
        act(chief, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("400");
        assertThat(debtRemaining(invoice)).isEqualByComparingTo("2200000");
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'THN' AND source_id = " + goodsReturn
                + " AND reversal_of_id IS NOT NULL")).isEqualTo(1);

        long discount = jsonId(send(clerk, post(RETURNS), returnBody("GGM", supplier, invoice,
                "{\"invoiceLineId\":" + invoiceLine + ",\"amount\":200000}"), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(220000)).andReturn());
        act(clerk, RETURNS, discount, "submit", null);
        act(clerk, RETURNS, discount, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("400");
        assertThat(debtRemaining(invoice)).isEqualByComparingTo("1980000");
        assertThat(costTotal(receiptLine, "DISCOUNT")).isEqualByComparingTo("-200000");
        assertThat(journal("GGM", discount)).containsExactly("N:331:200000", "C:1561:200000", "N:331:20000",
                "C:1331:20000");
    }

    @Test
    @DisplayName("BR-PUR-12, 16: phân bổ chi phí vận chuyển theo giá trị vào hai phiếu nhận; vượt phần còn lại; đảo")
    void landedCostAllocation() throws Exception {
        long supplier = partner("NCC_01");
        long order = approvedOrder(supplier, "3");
        long orderLine = id("SELECT id FROM purchase_order_lines WHERE order_id = " + order);
        long first = receive(order, orderLine, "2", unique("L"));
        long second = receive(order, orderLine, "1", unique("L"));
        long firstLine = id("SELECT id FROM purchase_receipt_lines WHERE receipt_id = " + first);
        long secondLine = id("SELECT id FROM purchase_receipt_lines WHERE receipt_id = " + second);
        long serviceInvoice = jsonId(send(clerk, post(INVOICES), invoiceBody(supplier, "{\"productId\":"
                + product("DV_VC") + ",\"unitId\":" + unit("CAI") + ",\"quantity\":1,\"unitPrice\":300000}"), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(clerk, INVOICES, serviceInvoice, "submit", null);
        act(clerk, INVOICES, serviceInvoice, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        long serviceLine = id("SELECT id FROM purchase_invoice_lines WHERE invoice_id = " + serviceInvoice);

        send(clerk, post(ALLOCATIONS), allocationBody(serviceLine, "350000", first, second), newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        long allocation = jsonId(send(clerk, post(ALLOCATIONS), allocationBody(serviceLine, "100000", first, second),
                newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targets[0].allocatedAmount").value(66667))
                .andExpect(jsonPath("$.data.targets[1].allocatedAmount").value(33333)).andReturn());
        act(clerk, ALLOCATIONS, allocation, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, ALLOCATIONS, allocation, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(costTotal(firstLine, "LANDED_COST")).isEqualByComparingTo("66667");
        assertThat(costTotal(secondLine, "LANDED_COST")).isEqualByComparingTo("33333");
        assertThat(journal("PBC", allocation)).containsExactly("N:1562:100000", "C:6422:100000");
        MvcResult costs = mockMvc.perform(as(clerk, get(ALLOCATIONS + "/cost-lines")).param("branchId", "1"))
                .andExpect(status().isOk()).andReturn();
        List<Object> left = json(costs, "$.data[?(@.invoiceLineId == " + serviceLine + ")].remainingAmount");
        assertThat(new BigDecimal(left.get(0).toString())).isEqualByComparingTo("200000");
        act(chief, INVOICES, serviceInvoice, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict());
        act(keeper, RECEIPTS, first, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        act(chief, ALLOCATIONS, allocation, "reverse", "{\"reason\":\"Sai tỷ lệ\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(costTotal(firstLine, "LANDED_COST")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT allocated_amount FROM purchase_invoice_lines WHERE id = ?",
                BigDecimal.class, serviceLine)).isEqualByComparingTo("0");
    }

    private long approvedOrder(long supplier, String quantity) throws Exception {
        long order = jsonId(send(buyer, post(ORDERS), order(supplier, warehouse("KHO_HN"), List.of(
                orderLine(product("VT_ONG_01"), unit("HOP"), quantity, "500000", null, taxRate("VAT10"), null))),
                newKey()).andExpect(status().isCreated()).andReturn());
        act(buyer, ORDERS, order, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        return order;
    }

    private long receive(long order, long orderLine, String quantity, String lot) throws Exception {
        return jsonId(send(keeper, post(RECEIPTS + "/receive-and-stock"), "{\"purchaseOrderId\":" + order
                + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"orderLineId\":" + orderLine + ",\"quantity\":"
                + quantity + ",\"inspectionResult\":\"ACCEPTED\",\"lotNo\":\"" + lot
                + "\",\"expiryDate\":\"2028-01-31\"}]}", newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("POSTED")).andReturn());
    }

    private long postedInvoice(long supplier, long receiptLine, String quantity) throws Exception {
        long invoice = jsonId(send(clerk, post(INVOICES), invoiceBody(supplier, "{\"receiptLineId\":" + receiptLine
                + ",\"quantity\":" + quantity + ",\"unitPrice\":500000}"), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(clerk, INVOICES, invoice, "submit", null);
        act(clerk, INVOICES, invoice, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        return invoice;
    }

    private static String invoiceBody(long supplier, String line) {
        return "{\"branchId\":1,\"supplierId\":" + supplier + ",\"documentDate\":\"" + DATE
                + "\",\"invoiceSeries\":\"" + unique("C26T") + "\",\"invoiceNo\":\"1\",\"invoiceDate\":\"" + DATE
                + "\",\"lines\":[" + line + "]}";
    }

    private static String returnBody(String type, long supplier, Long invoice, String line) {
        return "{\"documentType\":\"" + type + "\",\"branchId\":1,\"supplierId\":" + supplier
                + (invoice == null ? "" : ",\"purchaseInvoiceId\":" + invoice) + ",\"documentDate\":\"" + DATE
                + "\",\"lines\":[" + line + "]}";
    }

    private static String allocationBody(long serviceLine, String amount, long... receipts) {
        StringBuilder ids = new StringBuilder();
        for (long receipt : receipts) {
            ids.append(ids.length() == 0 ? "" : ",").append(receipt);
        }
        return "{\"branchId\":1,\"documentDate\":\"" + DATE + "\",\"method\":\"VALUE\",\"sources\":[{\"invoiceLineId\":"
                + serviceLine + ",\"amount\":" + amount + "}],\"receiptIds\":[" + ids + "]}";
    }

    private BigDecimal lotOnHand(String lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(b.on_hand_quantity), 0) FROM inventory_balances b "
                + "JOIN inventory_lots l ON l.id = b.lot_id WHERE l.lot_no = ?", BigDecimal.class, lot);
    }

    private BigDecimal debtRemaining(long invoice) {
        return jdbc.queryForObject("SELECT original_amount - settled_amount FROM debt_items WHERE source_type = 'HDM' "
                + "AND source_id = ?", BigDecimal.class, invoice);
    }

    private BigDecimal costTotal(long receiptLine, String type) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM purchase_cost_entries WHERE "
                + "receipt_line_id = ? AND entry_type = ?", BigDecimal.class, receiptLine, type);
    }

    /** Dòng bút toán gốc "N|C:tài khoản:số tiền"; khẳng định cân Nợ = Có. */
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
