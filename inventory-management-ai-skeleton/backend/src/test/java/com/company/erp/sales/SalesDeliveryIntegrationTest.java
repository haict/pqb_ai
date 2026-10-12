package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Giai đoạn 6C trên MySQL thật: xuất kho theo đơn bán theo FEFO dùng hàng đã giữ (AT-03, BR-SAL-21), giao nhiều đợt
 * (AT-05, BR-SAL-20), khách nhận / giao thất bại hàng về kho (BR-SAL-22, 23), hạn dùng còn lại tối thiểu (D-07).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesDeliveryIntegrationTest extends SalesIntegrationSupport {

    private static final String DELIVERIES = "/api/v1/sales-deliveries";

    @Autowired
    SalesDeliveryIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("AT-03, AT-05, BR-SAL-20..23: giao nhiều đợt theo FEFO, giữ hàng, khách nhận, giao thất bại, D-07")
    void multipleDeliveries() throws Exception {
        long item = newProduct(true, false);
        long piece = unit("CAI");
        long store = warehouse("KHO_HN");
        String later = unique("LA");
        String earlier = unique("LB");
        stockIn(item, "3", later, "2027-03-31", null);
        stockIn(item, "4", earlier, "2027-01-31", null);

        long order = jsonId(send(manager, post(ORDERS), order(partner("KH_PK01"), store, true, List.of(
                line(item, piece, "6", "100000", null))), newKey()).andExpect(status().isCreated()).andReturn());
        submitAndApproveOrder(order);
        assertThat(reserved(item, store)).isEqualByComparingTo("6");
        long orderLine = id("SELECT id FROM sales_order_lines WHERE order_id = " + order);

        long first = delivery(order, orderLine, "4", null);
        act(keeper, DELIVERIES, first, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.lines[0].items[0].lotNo").value(earlier))
                .andExpect(jsonPath("$.data.lines[0].items[0].baseQuantity").value(4))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty());
        assertThat(lotOnHand(earlier)).isEqualByComparingTo("0");
        assertThat(reserved(item, store)).isEqualByComparingTo("2");
        act(courier, DELIVERIES, first, "ship", "{\"shipperName\":\"Xe công ty\"}")
                .andExpect(jsonPath("$.data.deliveryStatus").value("IN_TRANSIT"));
        act(courier, DELIVERIES, first, "confirm", "{\"receivedDate\":\"" + DATE
                + "\",\"receivedBy\":\"Khoa Dược\",\"signedDocumentRef\":\"BBGN-01\"}")
                .andExpect(jsonPath("$.data.deliveryStatus").value("DELIVERED"));
        mockMvc.perform(as(manager, get(ORDERS + "/" + order)))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.lines[0].issuedQuantity").value(4))
                .andExpect(jsonPath("$.data.lines[0].deliveredQuantity").value(4))
                .andExpect(jsonPath("$.data.lines[0].outstandingQuantity").value(2));

        send(keeper, post(DELIVERIES), deliveryBody(order, orderLine, "3", null), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lines[0].quantity"));
        long second = delivery(order, orderLine, "2", null);
        act(keeper, DELIVERIES, second, "post", null).andExpect(jsonPath("$.data.lines[0].items[0].lotNo")
                .value(later));
        assertThat(reserved(item, store)).isEqualByComparingTo("0");
        act(courier, DELIVERIES, second, "fail", "{\"reason\":\"Khách vắng mặt\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("FAILED"));
        assertThat(lotOnHand(later)).isEqualByComparingTo("3");
        assertThat(reserved(item, store)).isEqualByComparingTo("2");

        long third = delivery(order, orderLine, "2", later);
        act(keeper, DELIVERIES, third, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        act(courier, DELIVERIES, third, "confirm", "{\"receivedDate\":\"" + DATE + "\"}")
                .andExpect(jsonPath("$.data.deliveryStatus").value("DELIVERED"));
        mockMvc.perform(as(manager, get(ORDERS + "/" + order))).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        act(chief, DELIVERIES, third, "reverse", "{\"reason\":\"Thử\"}").andExpect(status().isConflict());

        long hospitalOrder = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(
                line(item, piece, "1", "100000", null))), newKey()).andExpect(status().isCreated()).andReturn());
        submitAndApproveOrder(hospitalOrder);
        long tooShort = delivery(hospitalOrder, id("SELECT id FROM sales_order_lines WHERE order_id = "
                + hospitalOrder), "1", null);
        act(keeper, DELIVERIES, tooShort, "post", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LOT_NOT_SELLABLE"));
        assertThat(lotOnHand(later)).isEqualByComparingTo("1");
    }

    private long delivery(long order, long orderLine, String quantity, String lot) throws Exception {
        long delivery = jsonId(send(keeper, post(DELIVERIES), deliveryBody(order, orderLine, quantity, lot), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, DELIVERIES, delivery, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        return delivery;
    }

    private static String deliveryBody(long order, long orderLine, String quantity, String lot) {
        return "{\"orderId\":" + order + ",\"documentDate\":\"" + DATE + "\",\"lines\":[{\"orderLineId\":" + orderLine
                + ",\"quantity\":" + quantity + (lot == null ? "" : ",\"lotNo\":\"" + lot + "\"") + "}]}";
    }
}
