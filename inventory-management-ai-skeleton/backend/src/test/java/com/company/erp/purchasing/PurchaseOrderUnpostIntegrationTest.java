package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Bỏ ghi sổ đơn mua (D-62) trên MySQL thật: dòng ngược kho / công nợ / bút toán ngày hạch toán gốc, đơn về nháp giữ số
 * và hóa đơn, sửa rồi ghi sổ lại; chặn khi hàng đã xuất (BR-GEN-04); phân quyền (AT-10), chống xử lý trùng (AT-13),
 * nhất quán sổ (AT-09).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseOrderUnpostIntegrationTest extends PurchasingIntegrationSupport {

    private static final String UNPOST = "{\"reason\":\"Sai đơn giá\"}";

    @Autowired
    PurchaseOrderUnpostIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("D-62, AT-09, AT-10, AT-13: bỏ ghi sổ đơn mua về nháp giữ số, sổ phụ ghi ngược, sửa rồi ghi sổ lại")
    void unpostEditRepost() throws Exception {
        long tube = product("VT_ONG_01");
        long box = unit("HOP");
        long warehouse = warehouse("KHO_HN");
        long supplier = partner("NCC_01");
        long vat = taxRate("VAT10");
        String series = unique("C26T");
        String lot = unique("L");
        BigDecimal onHandBefore = onHand(tube, warehouse);
        long order = postedOrder(orderWithInvoice(supplier, warehouse, series, "7", List.of(
                lotLine(tube, box, "10", "500000", vat, lot))));
        String documentNo = jdbc.queryForObject("SELECT document_no FROM purchase_orders WHERE id = ?", String.class,
                order);

        act(buyer, ORDERS, order, "unpost", UNPOST).andExpect(status().isForbidden());
        String key = newKey();
        send(keeper, post(ORDERS + "/" + order + "/unpost"), UNPOST, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.documentNo").value(documentNo))
                .andExpect(jsonPath("$.data.inventoryDocumentId").doesNotExist());
        send(keeper, post(ORDERS + "/" + order + "/unpost"), UNPOST, key).andExpect(status().isOk());
        act(keeper, ORDERS, order, "unpost", UNPOST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));

        assertThat(lotOnHand(lot)).isEqualByComparingTo("0");
        assertThat(onHand(tube, warehouse)).isEqualByComparingTo(onHandBefore);
        assertLedgerConsistent(tube, warehouse);
        assertThat(activePayables(order)).isEmpty();
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DMH' AND source_id = " + order
                + " AND reversal_of_id IS NOT NULL AND posting_date = '" + DATE + "'")).isEqualTo(1);
        journal("DMH", order);
        assertThat(id("SELECT COUNT(*) FROM sys_audit_logs WHERE action = 'UNPOST' AND entity_type = "
                + "'PURCHASE_ORDER' AND entity_id = " + order + " AND reason = 'Sai đơn giá'")).isEqualTo(1);

        long version = id("SELECT version FROM purchase_orders WHERE id = " + order);
        String revised = orderWithInvoice(supplier, warehouse, series, "7", List.of(
                lotLine(tube, box, "10", "450000", vat, lot))).replaceFirst("}$", ",\"version\":" + version + "}");
        send(buyer, put(ORDERS + "/" + order), revised, newKey()).andExpect(status().isOk());
        act(keeper, ORDERS, order, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.documentNo").value(documentNo));

        assertThat(lotOnHand(lot)).isGreaterThan(BigDecimal.ZERO);
        assertLedgerConsistent(tube, warehouse);
        assertThat(activePayables(order)).hasSize(1);
        assertThat(activePayables(order).get(0)).isEqualByComparingTo("4950000");
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DMH' AND source_id = " + order
                + " AND reversal_of_id IS NULL AND status = 'POSTED'")).isEqualTo(1);
        journal("DMH", order);

        act(keeper, ORDERS, order, "reverse", "{\"reason\":\"Nhập nhầm\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("0");
        assertThat(activePayables(order)).isEmpty();
        assertLedgerConsistent(tube, warehouse);
    }

    @Test
    @DisplayName("D-62, BR-GEN-04: hàng của đơn mua đã xuất thì không bỏ ghi sổ được")
    void blockedWhenGoodsIssued() throws Exception {
        String lot = unique("L");
        long order = postedTubeOrder("2", lot);
        String issue = "{\"documentType\":\"PXK\",\"documentDate\":\"" + DATE + "\",\"warehouseId\":"
                + warehouse("KHO_HN") + ",\"lines\":[{\"productId\":" + product("VT_ONG_01") + ",\"unitId\":"
                + unit("HOP") + ",\"quantity\":1,\"lotNo\":\"" + lot + "\"}]}";
        long document = jsonId(send(keeper, post("/api/v1/inventory-documents"), issue, newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, "/api/v1/inventory-documents", document, "post", null)
                .andExpect(jsonPath("$.data.status").value("POSTED"));

        act(keeper, ORDERS, order, "unpost", UNPOST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_DEPENDENT_DOCUMENTS"));
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, order))
                .isEqualTo("POSTED");
    }

    private List<BigDecimal> activePayables(long order) {
        return jdbc.queryForList("SELECT original_amount FROM debt_items WHERE source_type = 'DMH' AND source_id = ? "
                + "AND status <> 'REVERSED'", BigDecimal.class, order);
    }
}
