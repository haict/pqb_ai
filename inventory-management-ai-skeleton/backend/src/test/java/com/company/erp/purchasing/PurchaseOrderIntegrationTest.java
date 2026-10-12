package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Giai đoạn 5A trên MySQL thật: đề nghị mua có duyệt (BR-PUR-01), đơn mua theo đề nghị (BR-PUR-04), duyệt theo ngưỡng
 * (D-04), tiến độ và đóng phần còn lại (BR-PUR-05), hàng đang về (BR mục 5.4), phân quyền (AT-10), chống xử lý trùng
 * (AT-13).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseOrderIntegrationTest extends PurchasingIntegrationSupport {

    @Autowired
    PurchaseOrderIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("BR-PUR-01, 04, 05: đề nghị mua → đơn mua theo đề nghị; số đã đặt, hàng đang về theo duyệt / đóng / hủy")
    void requisitionToOrder() throws Exception {
        long tube = product("VT_ONG_01");
        long box = unit("HOP");
        long piece = unit("CAI");
        long warehouse = warehouse("KHO_HN");
        long supplier = partner("NCC_01");
        BigDecimal incomingBefore = incoming(tube, warehouse);

        long requisition = jsonId(send(buyer, post(REQUISITIONS), requisition(List.of(requisitionLine(tube, box, "2"))),
                newKey()).andExpect(status().isCreated()).andReturn());
        act(buyer, REQUISITIONS, requisition, "submit", null).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        act(buyer, REQUISITIONS, requisition, "approve", "{}").andExpect(status().isForbidden());
        act(branchDirector, REQUISITIONS, requisition, "approve", "{}")
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        MvcResult open = mockMvc.perform(as(buyer, get(REQUISITIONS + "/open-lines")).param("branchId", "1")
                .param("productId", String.valueOf(tube))).andExpect(status().isOk()).andReturn();
        List<Integer> lineIds = json(open, "$.data[?(@.requisitionId == " + requisition + ")].lineId");
        assertThat(lineIds).hasSize(1);
        long requisitionLine = lineIds.get(0);

        MvcResult created = send(buyer, post(ORDERS), order(supplier, warehouse, List.of(orderLine(tube, piece, "150",
                "10000", "10", taxRate("VAT10"), requisitionLine))), newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(1500000))
                .andExpect(jsonPath("$.data.discountAmount").value(150000))
                .andExpect(jsonPath("$.data.taxAmount").value(135000))
                .andExpect(jsonPath("$.data.totalAmount").value(1485000))
                .andExpect(jsonPath("$.data.paymentTermDays").value(30))
                .andExpect(jsonPath("$.data.currencyCode").value("VND")).andReturn();
        long first = jsonId(created);
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(incomingBefore);
        act(buyer, ORDERS, first, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(incomingBefore.add(new BigDecimal("150")));
        assertRequisition(requisition, "IN_PROGRESS", "150");

        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(orderLine(tube, piece, "60", "10000", null, null,
                requisitionLine))), newKey()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_QUANTITY_EXCEEDED"));
        long second = jsonId(send(buyer, post(ORDERS), order(supplier, warehouse, List.of(orderLine(tube, piece, "50",
                "10000", null, null, requisitionLine))), newKey()).andExpect(status().isCreated()).andReturn());
        act(buyer, ORDERS, second, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertRequisition(requisition, "COMPLETED", "200");
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(incomingBefore.add(new BigDecimal("200")));

        act(buyer, ORDERS, second, "close", "{\"reason\":\"NCC hết hàng\"}")
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.lines[0].closedQuantity").value(50))
                .andExpect(jsonPath("$.data.lines[0].outstandingQuantity").value(0));
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(incomingBefore.add(new BigDecimal("150")));
        act(buyer, ORDERS, second, "cancel", "{\"reason\":\"Thử\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));

        act(buyer, ORDERS, first, "cancel", "{\"reason\":\"Đổi nhà cung cấp\"}")
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(incomingBefore);
        assertRequisition(requisition, "IN_PROGRESS", "50");

        act(buyer, REQUISITIONS, requisition, "close", "{\"reason\":\"Không mua thêm\"}")
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.lines[0].closedQuantity").value(150))
                .andExpect(jsonPath("$.data.lines[0].remainingQuantity").value(0));
    }

    @Test
    @DisplayName("D-04, AT-10, AT-13: đơn mua từ ngưỡng phải duyệt; từ chối rồi sửa gửi lại; gửi lại cùng key không xử lý hai lần")
    void thresholdApproval() throws Exception {
        long machine = product("MAY_XN_01");
        long piece = unit("CAI");
        long warehouse = warehouse("KHO_HN");
        String body = order(partner("NCC_01"), warehouse, List.of(orderLine(machine, piece, "1", "60000000", null,
                null, null)));
        long order = jsonId(send(buyer, post(ORDERS), body, newKey()).andExpect(status().isCreated()).andReturn());
        BigDecimal incomingBefore = incoming(machine, warehouse);

        String key = newKey();
        submit(order, key).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        submit(order, key).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        assertThat(id("SELECT COUNT(*) FROM approval_requests WHERE document_type = 'DMH' AND document_id = "
                + order)).isEqualTo(1);
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(incomingBefore);

        act(buyer, ORDERS, order, "approve", "{}").andExpect(status().isForbidden());
        mockMvc.perform(as(hcmKeeper, get(ORDERS + "/" + order))).andExpect(status().isNotFound());
        act(director, ORDERS, order, "reject", "{\"reason\":\"Giá cao\"}")
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        long version = id("SELECT version FROM purchase_orders WHERE id = " + order);
        String revised = body.replace("60000000", "55000000").replace("}]}", "}],\"version\":" + version + "}");
        mockMvc.perform(as(buyer, put(ORDERS + "/" + order)).contentType("application/json").content(revised))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value(55000000));
        act(buyer, ORDERS, order, "submit", null).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        act(director, ORDERS, order, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(incomingBefore.add(BigDecimal.ONE));
    }

    @Test
    @DisplayName("BR-PUR-04: kiểm tra nhà cung cấp, kho cùng chi nhánh, tiền tệ VND, ngoài phạm vi chi nhánh")
    void validation() throws Exception {
        long tube = product("VT_ONG_01");
        long piece = unit("CAI");
        String line = orderLine(tube, piece, "1", "1000", null, null, null);
        send(buyer, post(ORDERS), order(partner("KH_BV01"), warehouse("KHO_HN"), List.of(line)), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("supplierId"));
        send(buyer, post(ORDERS), order(partner("NCC_01"), warehouse("KHO_HCM"), List.of(line)), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("warehouseId"));
        send(buyer, post(ORDERS), order(partner("NCC_01"), warehouse("KHO_HN"), List.of(line))
                .replace("\"lines\"", "\"currencyCode\":\"USD\",\"lines\""), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("currencyCode"));
        send(buyer, post(ORDERS), order(partner("NCC_01"), warehouse("KHO_HN"), List.of(line))
                .replace("\"branchId\":1", "\"branchId\":2"), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
    }

    private ResultActions submit(long order, String key) throws Exception {
        return mockMvc.perform(as(buyer, post(ORDERS + "/" + order + "/submit"))
                .header(IdempotentCommand.HEADER, key));
    }

    private void assertRequisition(long requisition, String status, String ordered) {
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_requisitions WHERE id = ?", String.class,
                requisition)).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM purchase_requisition_lines WHERE "
                + "requisition_id = ?", BigDecimal.class, requisition)).isEqualByComparingTo(ordered);
    }
}
