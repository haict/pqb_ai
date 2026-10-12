package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
 * Giai đoạn 6A, 6B trên MySQL thật: giá theo thứ tự ưu tiên (BR-SAL-02), duyệt giá dưới sàn / chiết khấu vượt ngưỡng
 * (BR-SAL-03), vòng đời báo giá và chuyển đơn (BR-SAL-05), phiên bản (BR-SAL-01), hạn mức tín dụng (BR-SAL-14), giữ /
 * nhả hàng (BR-SAL-13, D-37), đề nghị mua phần thiếu (BR-SAL-15), "chỉ dữ liệu của mình" (AT-10).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesQuoteOrderIntegrationTest extends SalesIntegrationSupport {

    @Autowired
    SalesQuoteOrderIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-SAL-01..05: giá ưu tiên, duyệt giá, gửi khách, chấp nhận, chuyển đơn bán; phiên bản; phạm vi khách")
    void quoteLifecycle() throws Exception {
        long clinic = partner("KH_PK01");
        long agent = partner("KH_DL01");
        long tube = product("VT_ONG_01");
        long box = unit("HOP");
        mockMvc.perform(as(seller, get("/api/v1/sales/price")).param("customerId", String.valueOf(agent))
                        .param("productId", String.valueOf(tube)).param("unitId", String.valueOf(box)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(manager, get("/api/v1/sales/price")).param("customerId", String.valueOf(agent))
                        .param("productId", String.valueOf(tube)).param("unitId", String.valueOf(box)))
                .andExpect(jsonPath("$.data.unitPrice").value(700000))
                .andExpect(jsonPath("$.data.priceSource").value("CUSTOMER_GROUP"))
                .andExpect(jsonPath("$.data.floorPrice").value(600000))
                .andExpect(jsonPath("$.data.defaultDiscountPercent").value(5));

        long plain = jsonId(send(seller, post(QUOTES), quote(clinic, List.of(line(tube, box, "2", null, null))),
                newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(800000))
                .andExpect(jsonPath("$.data.lines[0].priceSource").value("LIST"))
                .andExpect(jsonPath("$.data.amount").value(1600000)).andReturn());
        act(seller, QUOTES, plain, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvalReasons").doesNotExist());

        long cheap = jsonId(send(seller, post(QUOTES), quote(clinic, List.of(line(tube, box, "1", null, "30"))),
                newKey()).andExpect(status().isCreated()).andReturn());
        act(seller, QUOTES, cheap, "submit", null).andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.approvalReasons").value(containsString("giá sàn")));
        act(seller, QUOTES, cheap, "send", null).andExpect(status().isConflict());
        act(seller, QUOTES, cheap, "approve", "{}").andExpect(status().isForbidden());
        act(manager, QUOTES, cheap, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(seller, QUOTES, cheap, "send", null).andExpect(jsonPath("$.data.status").value("SENT"));
        act(seller, QUOTES, cheap, "accept", null).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        long order = jsonId(send(seller, post(QUOTES + "/" + cheap + "/create-order"), "{\"warehouseId\":"
                + warehouse("KHO_HN") + "}", newKey()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(800000))
                .andExpect(jsonPath("$.data.lines[0].discountPercent").value(30)).andReturn());
        mockMvc.perform(as(seller, get(QUOTES + "/" + cheap))).andExpect(jsonPath("$.data.salesOrderId").value(order));
        send(seller, post(QUOTES + "/" + cheap + "/create-order"), "{\"warehouseId\":" + warehouse("KHO_HN") + "}",
                newKey()).andExpect(status().isConflict());

        act(seller, QUOTES, plain, "send", null);
        long revision = jsonId(act(seller, QUOTES, plain, "revise", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionNo").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn());
        assertThat(revision).isNotEqualTo(plain);
        mockMvc.perform(as(seller, get(QUOTES + "/" + plain))).andExpect(jsonPath("$.data.status").value("CLOSED"));

        send(seller, post(QUOTES), quote(partner("KH_BV01"), List.of(line(tube, box, "1", null, null))), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("customerId"));
        long managerQuote = jsonId(send(manager, post(QUOTES), quote(partner("KH_BV01"), List.of(
                line(tube, box, "1", null, null))), newKey()).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(as(seller, get(QUOTES + "/" + managerQuote))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SAL-12..15, D-22, D-37: hạn mức tín dụng phải duyệt; giữ / nhả hàng; đóng; đề nghị mua phần thiếu")
    void orderCreditReservationAndShortage() throws Exception {
        long tube = product("VT_ONG_01");
        long box = unit("HOP");
        long store = warehouse("KHO_HN");
        stockIn("10", unique("L"), "2028-06-30");

        long overLimit = jsonId(send(seller, post(ORDERS), order(partner("KH_PK01"), store, false, List.of(
                line(tube, box, "70", null, null))), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(56000000)).andReturn());
        act(seller, ORDERS, overLimit, "submit", null).andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.approvalReasons").value(containsString("hạn mức")));
        act(manager, ORDERS, overLimit, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));

        BigDecimal before = reserved(tube, store);
        long reserving = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), store, true, List.of(
                line(tube, box, "5", null, null))), newKey()).andExpect(status().isCreated()).andReturn());
        submitAndApprove(reserving);
        mockMvc.perform(as(manager, get(ORDERS + "/" + reserving)))
                .andExpect(jsonPath("$.data.reservedUntil").isNotEmpty())
                .andExpect(jsonPath("$.data.lines[0].reservedQuantity").value(500));
        assertThat(reserved(tube, store).subtract(before)).isEqualByComparingTo("500");
        act(manager, ORDERS, reserving, "release", null).andExpect(status().isOk());
        assertThat(reserved(tube, store)).isEqualByComparingTo(before);
        act(manager, ORDERS, reserving, "reserve", null).andExpect(jsonPath("$.data.lines[0].reservedQuantity")
                .value(500));
        act(manager, ORDERS, reserving, "reserve", null).andExpect(status().isOk());
        assertThat(reserved(tube, store).subtract(before)).isEqualByComparingTo("500");
        mockMvc.perform(as(seller, get(ORDERS + "/" + reserving))).andExpect(status().isNotFound());
        act(manager, ORDERS, reserving, "close", "{\"reason\":\"Khách đổi kế hoạch\"}")
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        assertThat(reserved(tube, store)).isEqualByComparingTo(before);

        long large = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), store, false, List.of(
                line(tube, box, "1000", null, null))), newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.availabilityWarnings.length()").value(1)).andReturn());
        submitAndApprove(large);
        long requisition = ((Number) json(act(manager, ORDERS, large, "create-purchase-request", null)
                .andExpect(status().isOk()).andReturn(), "$.data.requisitionId")).longValue();
        assertThat(jdbc.queryForMap("SELECT source_type, source_id FROM purchase_requisitions WHERE id = ?",
                requisition)).containsEntry("source_type", "SALES_ORDER").containsEntry("source_id", large);
    }

    /** Gửi duyệt; khách mẫu dùng chung có thể có nợ quá hạn từ test khác nên duyệt khi đơn phải duyệt. */
    private void submitAndApprove(long order) throws Exception {
        String status = json(act(manager, ORDERS, order, "submit", null).andExpect(status().isOk()).andReturn(),
                "$.data.status");
        if ("SUBMITTED".equals(status)) {
            act(manager, ORDERS, order, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        } else {
            assertThat(status).isEqualTo("APPROVED");
        }
    }
}
