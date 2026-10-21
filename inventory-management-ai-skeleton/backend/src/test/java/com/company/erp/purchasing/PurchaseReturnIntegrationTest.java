package com.company.erp.purchasing;

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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Trả hàng nhà cung cấp, giảm giá hàng mua (BR-PUR-14) và phân bổ chi phí mua (BR-PUR-12, 16) theo đơn mua đã ghi sổ
 * (D-54) trên MySQL thật: tồn theo lô, công nợ phải trả, điều chỉnh giá trị nhập, bút toán; đảo và chặn đảo đơn mua khi
 * còn chứng từ phụ thuộc (AT-06, AT-09).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseReturnIntegrationTest extends PurchasingIntegrationSupport {

    static final String RETURNS = "/api/v1/purchase-returns";
    static final String ALLOCATIONS = "/api/v1/purchase-cost-allocations";

    @Autowired
    PurchaseReturnIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("AT-06, AT-09, BR-PUR-14: trả hàng đúng lô, giảm giá theo dòng đơn mua; tồn, công nợ, bút toán; chặn đảo đơn mua; đảo")
    void returnsAndDiscount() throws Exception {
        long supplier = partner("NCC_01");
        String lot = unique("L");
        long order = postedTubeOrder("3", lot);
        long orderLine = orderLineId(order, 1);
        assertThat(lotOnHand(lot)).isEqualByComparingTo("300");
        assertThat(debtRemaining(order)).isEqualByComparingTo("1650000");

        MvcResult open = mockMvc.perform(as(clerk, get(RETURNS + "/returnable-lines")).param("branchId", "1")
                .param("supplierId", String.valueOf(supplier)).param("orderId", String.valueOf(order)))
                .andExpect(status().isOk()).andReturn();
        List<Object> remaining = json(open, "$.data[?(@.orderLineId == " + orderLine + ")].remainingQuantity");
        assertThat(new BigDecimal(remaining.get(0).toString())).isEqualByComparingTo("3");

        long goodsReturn = jsonId(send(clerk, post(RETURNS), returnBody("THN", supplier, order,
                "{\"orderLineId\":" + orderLine + ",\"quantity\":1}"), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(550000)).andReturn());
        act(clerk, RETURNS, goodsReturn, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty());
        assertThat(lotOnHand(lot)).isEqualByComparingTo("200");
        assertThat(debtRemaining(order)).isEqualByComparingTo("1100000");
        assertThat(journal("THN", goodsReturn)).containsExactly("N:331:500000", "C:1561:500000", "N:331:50000",
                "C:1331:50000");
        send(clerk, post(RETURNS), returnBody("THN", supplier, order, "{\"orderLineId\":" + orderLine
                + ",\"quantity\":3}"), newKey()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        act(chief, ORDERS, order, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        long discount = jsonId(send(clerk, post(RETURNS), returnBody("GGM", supplier, order,
                "{\"orderLineId\":" + orderLine + ",\"amount\":200000}"), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.totalAmount").value(220000)).andReturn());
        act(clerk, RETURNS, discount, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("200");
        assertThat(debtRemaining(order)).isEqualByComparingTo("880000");
        assertThat(costTotal(orderLine, "DISCOUNT")).isEqualByComparingTo("-200000");
        assertThat(journal("GGM", discount)).containsExactly("N:331:200000", "C:1561:200000", "N:331:20000",
                "C:1331:20000");

        act(clerk, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Sai\"}").andExpect(status().isForbidden());
        act(chief, RETURNS, goodsReturn, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        act(chief, RETURNS, discount, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("300");
        assertThat(debtRemaining(order)).isEqualByComparingTo("1650000");
        assertThat(costTotal(orderLine, "DISCOUNT")).isEqualByComparingTo("0");
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'THN' AND source_id = " + goodsReturn
                + " AND reversal_of_id IS NOT NULL")).isEqualTo(1);
        act(chief, ORDERS, order, "reverse", "{\"reason\":\"Nhận nhầm đơn\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("BR-PUR-12, 16: phân bổ chi phí vận chuyển theo giá trị vào hai đơn mua; vượt phần còn lại; chặn đảo nguồn / đích; đảo")
    void landedCostAllocation() throws Exception {
        long first = postedTubeOrder("2", unique("L"));
        long second = postedTubeOrder("1", unique("L"));
        long firstLine = orderLineId(first, 1);
        long secondLine = orderLineId(second, 1);
        long serviceOrder = postedOrder(orderWithInvoice(partner("NCC_01"), warehouse("KHO_HN"), unique("C26T"), "9",
                List.of(orderLine(product("DV_VC"), unit("CAI"), "1", "300000", null, null))));
        long serviceLine = orderLineId(serviceOrder, 1);
        assertThat(id("SELECT COUNT(*) FROM inventory_documents WHERE source_type = 'DMH' AND source_id = "
                + serviceOrder)).isZero();

        send(clerk, post(ALLOCATIONS), allocationBody(serviceLine, "350000", first, second), newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        long allocation = jsonId(send(clerk, post(ALLOCATIONS), allocationBody(serviceLine, "100000", first, second),
                newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targets[0].allocatedAmount").value(66667))
                .andExpect(jsonPath("$.data.targets[1].allocatedAmount").value(33333)).andReturn());
        act(clerk, ALLOCATIONS, allocation, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertThat(costTotal(firstLine, "LANDED_COST")).isEqualByComparingTo("66667");
        assertThat(costTotal(secondLine, "LANDED_COST")).isEqualByComparingTo("33333");
        assertThat(journal("PBC", allocation)).containsExactly("N:1562:100000", "C:6422:100000");
        MvcResult costs = mockMvc.perform(as(clerk, get(ALLOCATIONS + "/cost-lines")).param("branchId", "1"))
                .andExpect(status().isOk()).andReturn();
        List<Object> left = json(costs, "$.data[?(@.orderLineId == " + serviceLine + ")].remainingAmount");
        assertThat(new BigDecimal(left.get(0).toString())).isEqualByComparingTo("200000");
        act(chief, ORDERS, serviceOrder, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));

        act(chief, ALLOCATIONS, allocation, "reverse", "{\"reason\":\"Sai tỷ lệ\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(costTotal(firstLine, "LANDED_COST")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT allocated_amount FROM purchase_order_lines WHERE id = ?",
                BigDecimal.class, serviceLine)).isEqualByComparingTo("0");
        act(chief, ORDERS, serviceOrder, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
    }

    private static String returnBody(String type, long supplier, long order, String line) {
        return "{\"documentType\":\"" + type + "\",\"branchId\":1,\"supplierId\":" + supplier + ",\"purchaseOrderId\":"
                + order + ",\"documentDate\":\"" + DATE + "\",\"lines\":[" + line + "]}";
    }

    private static String allocationBody(long serviceLine, String amount, long... orders) {
        StringBuilder ids = new StringBuilder();
        for (long order : orders) {
            ids.append(ids.length() == 0 ? "" : ",").append(order);
        }
        return "{\"branchId\":1,\"documentDate\":\"" + DATE + "\",\"method\":\"VALUE\",\"sources\":[{\"orderLineId\":"
                + serviceLine + ",\"amount\":" + amount + "}],\"orderIds\":[" + ids + "]}";
    }

    private BigDecimal costTotal(long orderLine, String type) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM purchase_cost_entries WHERE "
                + "order_line_id = ? AND entry_type = ?", BigDecimal.class, orderLine, type);
    }
}
