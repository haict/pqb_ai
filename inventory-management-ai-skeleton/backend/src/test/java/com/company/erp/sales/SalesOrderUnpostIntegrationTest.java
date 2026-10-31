package com.company.erp.sales;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Bỏ ghi sổ đơn bán (D-62) trên MySQL thật: dòng ngược kho / công nợ / bút toán ngày hạch toán gốc, đơn về nháp giữ số,
 * sửa rồi ghi sổ lại; chặn khi đã chuyển đi giao hoặc đã có hóa đơn điện tử; phân quyền (AT-10), chống xử lý trùng
 * (AT-13), nhất quán sổ (AT-09).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesOrderUnpostIntegrationTest extends SalesIntegrationSupport {

    private static final String UNPOST = "{\"reason\":\"Sai số lượng\"}";

    @Autowired
    SalesOrderUnpostIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @Test
    @DisplayName("D-62, AT-09, AT-10, AT-13: bỏ ghi sổ đơn bán về nháp giữ số, sổ phụ ghi ngược, sửa rồi ghi sổ lại; D-65 không xóa được")
    void unpostEditRepost() throws Exception {
        long item = newProduct(true, false);
        long piece = unit("CAI");
        long store = warehouse("KHO_HN");
        String lot = unique("L");
        stockIn(item, "5", lot, "2028-06-30", null);
        long order = postedOrder(manager, order(partner("KH_BV01"), store, false, List.of(
                line(item, piece, "3", "100000", null))));
        String documentNo = jdbc.queryForObject("SELECT document_no FROM sales_orders WHERE id = ?", String.class,
                order);
        assertThat(lotOnHand(lot)).isEqualByComparingTo("2");

        act(seller, ORDERS, order, "unpost", UNPOST).andExpect(status().isForbidden());
        String key = newKey();
        send(chief, post(ORDERS + "/" + order + "/unpost"), UNPOST, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.documentNo").value(documentNo))
                .andExpect(jsonPath("$.data.inventoryDocumentId").doesNotExist());
        send(chief, post(ORDERS + "/" + order + "/unpost"), UNPOST, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        act(chief, ORDERS, order, "unpost", UNPOST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        // D-65: đơn đã từng ghi sổ còn sổ phụ tham chiếu, về nháp vẫn không xóa được.
        send(manager, delete(ORDERS + "/" + order), UNPOST, newKey()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));

        assertThat(lotOnHand(lot)).isEqualByComparingTo("5");
        assertThat(activeReceivables(order)).isEmpty();
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DBH' AND source_id = " + order
                + " AND reversal_of_id IS NOT NULL AND posting_date = '" + DATE + "'")).isEqualTo(1);
        journal("DBH", order);
        assertThat(id("SELECT COUNT(*) FROM sys_audit_logs WHERE action = 'UNPOST' AND entity_type = "
                + "'SALES_ORDER' AND entity_id = " + order
                + " AND reason = 'Sai số lượng'")).isEqualTo(1);

        long version = id("SELECT version FROM sales_orders WHERE id = " + order);
        String revised = order(partner("KH_BV01"), store, false, List.of(line(item, piece, "4", "100000", null)))
                .replaceFirst("}$", ",\"version\":" + version + "}");
        mockMvc.perform(as(manager, put(ORDERS + "/" + order)).contentType("application/json").content(revised))
                .andExpect(status().isOk());
        postWithAuthority(keeper, order).andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.documentNo").value(documentNo));

        assertThat(lotOnHand(lot)).isEqualByComparingTo("1");
        assertThat(activeReceivables(order)).hasSize(1);
        assertThat(activeReceivables(order).get(0)).isEqualByComparingTo(
                jdbc.queryForObject("SELECT total_amount FROM sales_orders WHERE id = ?", BigDecimal.class, order));
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DBH' AND source_id = " + order
                + " AND reversal_of_id IS NULL AND status = 'POSTED'")).isEqualTo(1);
        journal("DBH", order);
        assertThat(id("SELECT COUNT(*) FROM inventory_documents WHERE source_type = 'DBH' AND source_id = " + order
                + " AND document_type = 'PXK' AND status = 'POSTED'")).isEqualTo(1);

        act(chief, ORDERS, order, "reverse", "{\"reason\":\"Khách hủy\"}")
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("5");
        assertThat(activeReceivables(order)).isEmpty();
    }

    @Test
    @DisplayName("D-62: đơn đã chuyển đi giao hoặc đã có hóa đơn điện tử không bỏ ghi sổ được")
    void blockedAfterShipmentOrEinvoice() throws Exception {
        long item = newProduct(true, false);
        long piece = unit("CAI");
        long store = warehouse("KHO_HN");
        stockIn(item, "5", unique("L"), "2028-06-30", null);
        long shipped = postedOrder(manager, order(partner("KH_BV01"), store, false, List.of(
                line(item, piece, "1", "100000", null))));
        act(courier, ORDERS, shipped, "ship", "{\"shipperName\":\"Xe công ty\"}").andExpect(status().isOk());
        act(chief, ORDERS, shipped, "unpost", UNPOST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));

        long invoiced = postedOrder(manager, order(partner("KH_BV01"), store, false, List.of(
                line(item, piece, "1", "100000", null))));
        act(clerk, ORDERS, invoiced, "einvoice-reference", "{\"series\":\"" + unique("1C26T")
                + "\",\"number\":\"0000009\",\"date\":\"" + DATE + "\"}").andExpect(status().isOk());
        act(chief, ORDERS, invoiced, "unpost", UNPOST).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_STATE"));
        assertThat(jdbc.queryForObject("SELECT status FROM sales_orders WHERE id = ?", String.class, invoiced))
                .isEqualTo("POSTED");
    }

    private List<BigDecimal> activeReceivables(long order) {
        return jdbc.queryForList("SELECT original_amount FROM debt_items WHERE source_type = 'DBH' AND source_id = ? "
                + "AND status <> 'REVERSED'", BigDecimal.class, order);
    }
}
