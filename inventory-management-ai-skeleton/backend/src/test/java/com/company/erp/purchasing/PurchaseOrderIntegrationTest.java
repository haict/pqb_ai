package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Đơn mua gộp (D-54) trên MySQL thật: ghi sổ = nhập kho theo lô / serial + công nợ phải trả + bút toán (BR-PUR-04, 08,
 * 09), hàng đang về (BR mục 5.4), hóa đơn trùng (BR-PUR-09), duyệt theo ngưỡng (D-04), phân quyền (AT-10), chống xử lý
 * trùng (AT-13), đảo (AT-09).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseOrderIntegrationTest extends PurchasingIntegrationSupport {

    @Autowired
    PurchaseOrderIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("AT-01, AT-02, AT-13, BR-PUR-08, 09: ghi sổ đơn mua nhập kho theo lô / serial, công nợ, bút toán; gửi lại cùng key; hóa đơn trùng; đảo")
    void postAndReverse() throws Exception {
        long tube = product("VT_ONG_01");
        long machine = product("MAY_XN_01");
        long service = product("DV_VC");
        long warehouse = warehouse("KHO_HN");
        long supplier = partner("NCC_01");
        long vat = taxRate("VAT10");
        BigDecimal tubeIncoming = incoming(tube, warehouse);
        BigDecimal machineIncoming = incoming(machine, warehouse);
        BigDecimal tubeOnHand = onHand(tube, warehouse);
        String lot = unique("L");
        String serial = unique("SN");
        String series = unique("C26T");
        String body = orderWithInvoice(supplier, warehouse, series, "123", List.of(
                lotLine(tube, unit("HOP"), "2", "500000", vat, lot),
                orderLine(machine, unit("CAI"), "1", "10000000", null, vat).replaceFirst("}$",
                        ",\"serialNumbers\":[\"" + serial + "\"]}"),
                orderLine(service, unit("CAI"), "1", "300000", null, vat)));

        long order = jsonId(send(buyer, post(ORDERS), body, newKey()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(12430000))
                .andExpect(jsonPath("$.data.invoiceSeries").value(series))
                .andExpect(jsonPath("$.data.supplierTaxCode").value("0300000001"))
                .andExpect(jsonPath("$.data.supplierAddress").isNotEmpty())
                .andExpect(jsonPath("$.data.dueDate").value("2026-11-04")).andReturn());
        act(buyer, ORDERS, order, "submit", null).andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming.add(new BigDecimal("200")));
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(machineIncoming.add(BigDecimal.ONE));

        act(buyer, ORDERS, order, "post", null).andExpect(status().isForbidden());
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order + "/stock-in-slip"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        String key = newKey();
        MvcResult posted = postOrder(order, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.inventoryDocumentNo").isNotEmpty()).andReturn();
        postOrder(order, key).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("POSTED"));
        act(keeper, ORDERS, order, "post", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        long inventoryId = ((Number) json(posted, "$.data.inventoryDocumentId")).longValue();
        Map<String, Object> source = jdbc.queryForMap("SELECT document_type, status, source_type, source_id FROM "
                + "inventory_documents WHERE id = ?", inventoryId);
        assertThat(source).containsEntry("document_type", "PNK").containsEntry("status", "POSTED")
                .containsEntry("source_type", "DMH");
        assertThat(((Number) source.get("source_id")).longValue()).isEqualTo(order);
        assertThat(id("SELECT COUNT(*) FROM inventory_documents WHERE source_type = 'DMH' AND source_id = " + order))
                .isEqualTo(1);
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(machineIncoming);
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(tubeOnHand.add(new BigDecimal("200")));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("200");
        assertThat(id("SELECT COUNT(*) FROM inventory_serials WHERE serial_no = '" + serial + "'")).isEqualTo(1);
        assertThat(debtRemaining(order)).isEqualByComparingTo("12430000");
        assertThat(journal("DMH", order)).containsExactly("N:1561:1000000", "C:331:1000000", "N:1561:10000000",
                "C:331:10000000", "N:6422:300000", "C:331:300000", "N:1331:1130000", "C:331:1130000");
        assertLedgerConsistent(tube, warehouse);

        // Phiếu nhập kho in từ đơn: chỉ dòng hàng hóa, giá sau chiết khấu trước thuế, tài khoản phần hàng hóa
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order + "/stock-in-slip"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slipNo").value((String) json(posted, "$.data.inventoryDocumentNo")))
                .andExpect(jsonPath("$.data.companyName").isNotEmpty())
                .andExpect(jsonPath("$.data.supplierTaxCode").value("0300000001"))
                .andExpect(jsonPath("$.data.debitAccounts.length()").value(1))
                .andExpect(jsonPath("$.data.debitAccounts[0]").value("1561"))
                .andExpect(jsonPath("$.data.creditAccounts.length()").value(1))
                .andExpect(jsonPath("$.data.creditAccounts[0]").value("331"))
                .andExpect(jsonPath("$.data.description").value("Mua hàng của Nhà phân phối thiết bị mẫu theo hóa đơn 123"))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].lotNo").value(lot))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(500000))
                .andExpect(jsonPath("$.data.lines[1].lineNo").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(11000000))
                .andExpect(jsonPath("$.data.taxRates.length()").value(1))
                .andExpect(jsonPath("$.data.taxRates[0]").value(10))
                .andExpect(jsonPath("$.data.taxAmount").value(1100000))
                .andExpect(jsonPath("$.data.paymentAmount").value(12100000));

        send(buyer, post(ORDERS), orderWithInvoice(supplier, warehouse, series.toLowerCase(), " 123 ", List.of(
                orderLine(service, unit("CAI"), "1", "1000", null, null))), newKey())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));

        act(keeper, ORDERS, order, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        mockMvc.perform(as(buyer, get(ORDERS + "/" + order + "/stock-in-slip"))).andExpect(status().isConflict());
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(tubeOnHand);
        assertThat(lotOnHand(lot)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT status FROM debt_items WHERE source_type = 'DMH' AND source_id = ?",
                String.class, order)).isEqualTo("REVERSED");
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DMH' AND source_id = " + order
                + " AND reversal_of_id IS NOT NULL")).isEqualTo(1);
        assertThat(incoming(tube, warehouse)).isEqualByComparingTo(tubeIncoming);
        assertLedgerConsistent(tube, warehouse);
        send(buyer, post(ORDERS), orderWithInvoice(supplier, warehouse, series, "123", List.of(
                orderLine(service, unit("CAI"), "1", "1000", null, null))), newKey())
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("D-04, AT-10, AT-13: đơn mua từ ngưỡng phải duyệt; từ chối rồi sửa gửi lại; gửi lại cùng key không xử lý hai lần; hủy đơn đã duyệt trừ hàng đang về")
    void thresholdApproval() throws Exception {
        long machine = product("MAY_XN_01");
        long piece = unit("CAI");
        long warehouse = warehouse("KHO_HN");
        String body = order(partner("NCC_01"), warehouse, List.of(orderLine(machine, piece, "1", "60000000", null,
                null).replaceFirst("}$", ",\"serialNumbers\":[\"" + unique("SN") + "\"]}")));
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

        act(buyer, ORDERS, order, "cancel", "{\"reason\":\"Đổi nhà cung cấp\"}")
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(incoming(machine, warehouse)).isEqualByComparingTo(incomingBefore);
    }

    @Test
    @DisplayName("BR-PUR-04, 08, 09: nhà cung cấp, kho cùng chi nhánh, VND, phạm vi chi nhánh, lô / hạn dùng, serial, hóa đơn đủ thông tin")
    void validation() throws Exception {
        long tube = product("VT_ONG_01");
        long piece = unit("CAI");
        long supplier = partner("NCC_01");
        long warehouse = warehouse("KHO_HN");
        String line = lotLine(tube, piece, "1", "1000", null, unique("L"));
        send(buyer, post(ORDERS), order(partner("KH_BV01"), warehouse, List.of(line)), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("supplierId"));
        send(buyer, post(ORDERS), order(supplier, warehouse("KHO_HCM"), List.of(line)), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("warehouseId"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line))
                .replace("\"lines\"", "\"currencyCode\":\"USD\",\"lines\""), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("currencyCode"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line)).replace("\"branchId\":1", "\"branchId\":2"),
                newKey()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("branchId"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(orderLine(tube, piece, "1", "1000", null, null))),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(orderLine(product("MAY_XN_01"), piece, "2",
                "1000", null, null).replaceFirst("}$", ",\"serialNumbers\":[\"" + unique("SN") + "\"]}"))), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].serialNumbers"));
        // Hàng không quản lý lô: chặn số lô ngay khi lưu đơn, không để đến lúc ghi sổ mới lỗi
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(lotLine(product("MAY_XN_01"), piece, "1",
                "1000", null, unique("L")).replaceFirst("}$", ",\"serialNumbers\":[\"" + unique("SN") + "\"]}"))),
                newKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lotNo"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line.replaceFirst("}$",
                ",\"serialNumbers\":[\"" + unique("SN") + "\"]}"))), newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].serialNumbers"));
        send(buyer, post(ORDERS), order(supplier, warehouse, List.of(line))
                .replace("\"lines\"", "\"invoiceSeries\":\"C26TAA\",\"lines\""), newKey())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("invoiceNo"));
    }

    private ResultActions submit(long order, String key) throws Exception {
        return mockMvc.perform(as(buyer, post(ORDERS + "/" + order + "/submit"))
                .header(IdempotentCommand.HEADER, key));
    }

    private ResultActions postOrder(long order, String key) throws Exception {
        return mockMvc.perform(as(keeper, post(ORDERS + "/" + order + "/post")).header(IdempotentCommand.HEADER, key));
    }
}
