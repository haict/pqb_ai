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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Trả hàng bán, giảm giá hàng bán, đổi hàng (BR-SAL-32, 33, 36) và tiếp nhận hàng lỗi (BR-SAL-34) theo đơn bán đã ghi
 * sổ (D-54) trên MySQL thật: lô vào cách ly, doanh thu, công nợ, bút toán; chặn đảo đơn khi còn trả hàng (AT-06,
 * AT-09); phân quyền (AT-10).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesReturnIntegrationTest extends SalesIntegrationSupport {

    private static final String RETURNS = "/api/v1/sales-returns";
    private static final String COMPLAINTS = "/api/v1/sales-complaints";

    @Autowired
    SalesReturnIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("AT-06, BR-SAL-32, 33, 36, AT-09: trả hàng đúng lô vào cách ly, giảm doanh thu / công nợ; giảm giá; đổi hàng; đảo")
    void returnDiscountExchange() throws Exception {
        long item = newProduct(true, false);
        String lot = unique("L");
        stockIn(item, "10", lot, "2028-12-31", null);
        long clinic = partner("KH_PK01");
        long vat = id("SELECT id FROM tax_rates WHERE company_id = 1 AND code = 'VAT10'");
        long order = postedOrder(manager, "{\"branchId\":1,\"customerId\":" + clinic + ",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"productId\":" + item
                + ",\"unitId\":" + unit("CAI") + ",\"quantity\":5,\"unitPrice\":100000,\"taxRateId\":" + vat + "}]}");
        long orderLine = id("SELECT id FROM sales_order_lines WHERE order_id = " + order);
        assertThat(receivable(order)).isEqualByComparingTo("550000");
        mockMvc.perform(as(clerk, get(RETURNS + "/returnable-lines")).param("branchId", "1")
                        .param("customerId", String.valueOf(clinic)).param("orderId", String.valueOf(order)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].lotNo").value(lot))
                .andExpect(jsonPath("$.data[0].remainingQuantity").value(5));

        send(clerk, post(RETURNS), returnBody("TLB", clinic, order, "{\"orderLineId\":" + orderLine
                + ",\"lotNo\":\"" + lot + "\",\"quantity\":6}"), newKey()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        send(clerk, post(RETURNS), returnBody("TLB", clinic, order, "{\"orderLineId\":" + orderLine
                + ",\"lotNo\":\"KHONG_CO\",\"quantity\":1}"), newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        long goodsReturn = jsonId(send(clerk, post(RETURNS), returnBody("TLB", clinic, order, "{\"orderLineId\":"
                + orderLine + ",\"lotNo\":\"" + lot + "\",\"quantity\":2}"), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(220000))
                .andExpect(jsonPath("$.data.lines[0].qualityStatus").value("QUARANTINE")).andReturn());
        act(clerk, RETURNS, goodsReturn, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(clerk, RETURNS, goodsReturn, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty());
        assertThat(quarantined(lot)).isEqualByComparingTo("2");
        assertThat(receivable(order)).isEqualByComparingTo("330000");
        assertThat(journal("TLB", goodsReturn)).containsExactly("N:5111:200000", "C:131:200000", "N:3331:20000",
                "C:131:20000");
        mockMvc.perform(as(manager, get(ORDERS + "/" + order)))
                .andExpect(jsonPath("$.data.lines[0].returnedQuantity").value(2))
                .andExpect(jsonPath("$.data.lines[0].returnableQuantity").value(3));
        act(chief, ORDERS, order, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        act(manager, RETURNS, goodsReturn, "create-exchange-order", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.sourceReturnId").value(goodsReturn))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(100000));

        long discount = jsonId(send(clerk, post(RETURNS), returnBody("GGB", clinic, order, "{\"orderLineId\":"
                + orderLine + ",\"amount\":50000}"), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(55000)).andReturn());
        act(clerk, RETURNS, discount, "submit", null);
        act(clerk, RETURNS, discount, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(receivable(order)).isEqualByComparingTo("275000");
        assertThat(journal("GGB", discount)).containsExactly("N:5111:50000", "C:131:50000", "N:3331:5000",
                "C:131:5000");

        act(clerk, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Nhầm\"}").andExpect(status().isForbidden());
        act(chief, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        act(chief, RETURNS, discount, "reverse", "{\"reason\":\"Nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(quarantined(lot)).isEqualByComparingTo("0");
        assertThat(receivable(order)).isEqualByComparingTo("550000");
        act(chief, ORDERS, order, "reverse", "{\"reason\":\"Lập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("BR-SAL-34, AT-10: tiếp nhận hàng lỗi theo đơn bán, kiểm tra, quyết định; nhân viên kinh doanh không quyết định được")
    void complaint() throws Exception {
        long clinic = partner("KH_PK01");
        send(seller, post(COMPLAINTS), complaintBody(clinic, 999999999L), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("salesOrderId"));
        long complaint = jsonId(send(seller, post(COMPLAINTS), complaintBody(clinic, null), newKey())
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

    private String complaintBody(long customer, Long order) {
        return "{\"branchId\":1,\"customerId\":" + customer + ",\"receivedDate\":\"" + DATE + "\""
                + (order == null ? "" : ",\"salesOrderId\":" + order) + ",\"productId\":" + product("VT_ONG_01")
                + ",\"lotNo\":\"L01\",\"quantity\":1,\"description\":\"Ống nứt khi mở hộp\"}";
    }

    private static String returnBody(String type, long customer, long order, String line) {
        return "{\"documentType\":\"" + type + "\",\"branchId\":1,\"customerId\":" + customer + ",\"salesOrderId\":"
                + order + ",\"documentDate\":\"" + DATE + "\",\"lines\":[" + line + "]}";
    }

    private BigDecimal quarantined(String lot) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(b.on_hand_quantity), 0) FROM inventory_balances b "
                + "JOIN inventory_lots l ON l.id = b.lot_id WHERE l.lot_no = ? AND b.quality_status = 'QUARANTINE'",
                BigDecimal.class, lot);
    }
}
