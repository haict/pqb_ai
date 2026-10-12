package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
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
 * Giai đoạn 5B trên MySQL thật: phiếu nhận hàng nhiều đợt theo đơn mua (AT-01), lô / hạn dùng / serial (AT-02), hàng
 * lỗi cách ly / trả lại (BR-PUR-07, 08), nhận và nhập trong một thao tác (D-36), nhận vượt dung sai phải duyệt, đảo
 * (AT-09). Sau mỗi bước: hàng đang về = số còn thiếu của đơn mua, tồn = sổ kho.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseReceiptIntegrationTest extends PurchasingIntegrationSupport {

    private static final String RECEIPTS = "/api/v1/purchase-receipts";

    private Session keeper;

    @Autowired
    PurchaseReceiptIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @BeforeEach
    void loginKeeper() throws Exception {
        keeper = login(mockMvc, "thukho.hn");
    }

    @Test
    @DisplayName("AT-01, AT-02, D-36: nhận nhiều đợt theo lô / serial, cách ly, trả lại; hàng đang về, tồn, tiến độ đúng sau mỗi đợt; đảo")
    void receiveInBatches() throws Exception {
        long tube = product("VT_ONG_01");
        long machine = product("MAY_XN_01");
        long box = unit("HOP");
        long piece = unit("CAI");
        long warehouse = warehouse("KHO_HN");
        BigDecimal tubeIncoming = incoming(tube, warehouse);
        BigDecimal machineIncoming = incoming(machine, warehouse);
        BigDecimal tubeOnHand = onHand(tube, warehouse);
        long order = approvedOrder(List.of(orderLine(tube, box, "3", "500000", null, null, null),
                orderLine(machine, piece, "2", "10000000", null, null, null)));
        long tubeLine = orderLineId(order, 1);
        long machineLine = orderLineId(order, 2);
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming.add(new BigDecimal("300")));

        String serial = unique("SN");
        String lot1 = unique("L");
        long first = jsonId(send(keeper, post(RECEIPTS), receipt(order, List.of(
                stockLine(tubeLine, "2", "ACCEPTED", lot1, null),
                "{\"orderLineId\":" + machineLine + ",\"quantity\":1,\"inspectionResult\":\"ACCEPTED\","
                        + "\"serialNumbers\":[\"" + serial + "\"]}")), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, RECEIPTS, first, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(keeper, RECEIPTS, first, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertOrderLine(tubeLine, "200", "0", "0");
        assertThat(orderStatus(order)).isEqualTo("IN_PROGRESS");
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming.add(new BigDecimal("300")));
        MvcResult stocked = act(keeper, RECEIPTS, first, "stock-in", null).andExpect(status().isOk()).andReturn();
        long inventoryId = ((Number) json(stocked, "$.data.inventoryDocumentId")).longValue();
        assertThat((String) json(stocked, "$.data.inventoryDocumentNo")).isNotBlank();
        assertOrderLine(tubeLine, "200", "200", "0");
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming.add(new BigDecimal("100")));
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(machineIncoming.add(BigDecimal.ONE));
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(tubeOnHand.add(new BigDecimal("200")));
        Map<String, Object> source = jdbc.queryForMap("SELECT document_type, status, source_type, source_id FROM "
                + "inventory_documents WHERE id = ?", inventoryId);
        assertThat(source).containsEntry("document_type", "PNK").containsEntry("status", "POSTED")
                .containsEntry("source_type", "PNH");
        assertThat(((Number) source.get("source_id")).longValue()).isEqualTo(first);
        assertThat(id("SELECT COUNT(*) FROM inventory_serials WHERE serial_no = '" + serial + "'")).isEqualTo(1);
        mockMvc.perform(as(keeper, post("/api/v1/inventory-documents/" + inventoryId + "/reverse"))
                        .header("Idempotency-Key", newKey()).contentType("application/json")
                        .content("{\"reason\":\"Thử\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));

        String lot2 = unique("L");
        long second = jsonId(send(keeper, post(RECEIPTS + "/receive-and-stock"), receipt(order, List.of(
                stockLine(tubeLine, "0.8", "ACCEPTED", lot2, null),
                stockLine(tubeLine, "0.2", "QUARANTINE", lot2, "Ống nứt"),
                "{\"orderLineId\":" + machineLine + ",\"quantity\":1,\"inspectionResult\":\"RETURN\","
                        + "\"rejectReason\":\"Máy móp\"}")), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.stockedAt").isNotEmpty()).andReturn());
        assertOrderLine(tubeLine, "300", "300", "20");
        assertOrderLine(machineLine, "2", "1", "1");
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(machineIncoming.add(BigDecimal.ONE));
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(tubeOnHand.add(new BigDecimal("300")));
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(b.on_hand_quantity), 0) FROM inventory_balances b "
                + "JOIN inventory_lots l ON l.id = b.lot_id WHERE l.lot_no = ? AND b.quality_status = 'QUARANTINE'",
                BigDecimal.class, lot2)).isEqualByComparingTo("20");
        assertThat(orderStatus(order)).isEqualTo("IN_PROGRESS");
        assertLedgerConsistent(tube, warehouse);

        act(buyer, RECEIPTS, second, "reverse", "{\"reason\":\"Nhập nhầm\"}").andExpect(status().isForbidden());
        act(keeper, RECEIPTS, second, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertOrderLine(tubeLine, "200", "200", "0");
        assertOrderLine(machineLine, "1", "1", "0");
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming.add(new BigDecimal("100")));
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(tubeOnHand.add(new BigDecimal("200")));
        assertLedgerConsistent(tube, warehouse);

        act(buyer, ORDERS, order, "close", "{\"reason\":\"NCC không giao tiếp\"}")
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(machineIncoming);
    }

    @Test
    @DisplayName("BR-PUR-07: nhận vượt dung sai phải duyệt; nhận đủ thì đơn mua hoàn thành; kiểm tra lô, lý do lỗi")
    void overToleranceAndValidation() throws Exception {
        long tube = product("VT_ONG_01");
        long warehouse = warehouse("KHO_HN");
        BigDecimal tubeIncoming = incoming(tube, warehouse);
        long order = approvedOrder(List.of(orderLine(tube, unit("HOP"), "1", "500000", null, null, null)));
        long tubeLine = orderLineId(order, 1);

        send(keeper, post(RECEIPTS), receipt(order, List.of("{\"orderLineId\":" + tubeLine
                + ",\"quantity\":1,\"inspectionResult\":\"ACCEPTED\"}")), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        send(keeper, post(RECEIPTS), receipt(order, List.of("{\"orderLineId\":" + tubeLine
                + ",\"quantity\":1,\"inspectionResult\":\"RETURN\"}")), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].rejectReason"));

        long receipt = jsonId(send(keeper, post(RECEIPTS + "/receive-and-stock"), receipt(order, List.of(
                stockLine(tubeLine, "1.2", "ACCEPTED", unique("L"), null))), newKey())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.overTolerance").value(true)).andReturn());
        act(keeper, RECEIPTS, receipt, "post", null).andExpect(status().isConflict());
        act(branchDirector, RECEIPTS, receipt, "approve", "{}").andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(keeper, RECEIPTS, receipt, "post", null).andExpect(status().isOk());
        act(keeper, RECEIPTS, receipt, "stock-in", null).andExpect(status().isOk());
        assertOrderLine(tubeLine, "120", "120", "0");
        assertThat(orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        send(keeper, post(RECEIPTS), receipt(order, List.of(stockLine(tubeLine, "1", "ACCEPTED", unique("L"),
                null))), newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("purchaseOrderId"));
    }

    private long approvedOrder(List<String> lines) throws Exception {
        long order = jsonId(send(buyer, post(ORDERS), order(partner("NCC_01"), warehouse("KHO_HN"), lines), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(buyer, ORDERS, order, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        return order;
    }

    private long orderLineId(long order, int lineNo) {
        return id("SELECT id FROM purchase_order_lines WHERE order_id = " + order + " AND line_no = " + lineNo);
    }

    private static String receipt(long order, List<String> lines) {
        return "{\"purchaseOrderId\":" + order + ",\"documentDate\":\"" + DATE + "\",\"deliveryNoteNo\":\"GH-01\","
                + "\"lines\":[" + String.join(",", lines) + "]}";
    }

    private static String stockLine(long orderLine, String quantity, String result, String lot, String reason) {
        return "{\"orderLineId\":" + orderLine + ",\"quantity\":" + quantity + ",\"inspectionResult\":\"" + result
                + "\",\"lotNo\":\"" + lot + "\",\"expiryDate\":\"2028-01-31\""
                + (reason == null ? "" : ",\"rejectReason\":\"" + reason + "\"") + "}";
    }

    private String orderStatus(long order) {
        return jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, order);
    }

    private void assertOrderLine(long line, String received, String stocked, String rejected) {
        Map<String, Object> row = jdbc.queryForMap("SELECT received_quantity, stocked_quantity, rejected_quantity "
                + "FROM purchase_order_lines WHERE id = ?", line);
        assertThat((BigDecimal) row.get("received_quantity")).as("đã nhận").isEqualByComparingTo(received);
        assertThat((BigDecimal) row.get("stocked_quantity")).as("đã nhập").isEqualByComparingTo(stocked);
        assertThat((BigDecimal) row.get("rejected_quantity")).as("lỗi").isEqualByComparingTo(rejected);
    }

    private BigDecimal onHand(long productId, long warehouseId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_stock_summaries "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, productId, warehouseId);
    }

    /** Tồn chi tiết = tổng tồn = sổ nhật ký kho (BR mục 5.4). */
    private void assertLedgerConsistent(long productId, long warehouseId) {
        BigDecimal balances = jdbc.queryForObject("SELECT COALESCE(SUM(on_hand_quantity), 0) FROM inventory_balances "
                + "WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class, productId, warehouseId);
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(IF(direction = 'IN', quantity, -quantity)), 0) "
                + "FROM inventory_transactions WHERE product_id = ? AND warehouse_id = ?", BigDecimal.class,
                productId, warehouseId);
        assertThat(balances).isEqualByComparingTo(onHand(productId, warehouseId));
        assertThat(ledger).isEqualByComparingTo(balances);
    }
}
