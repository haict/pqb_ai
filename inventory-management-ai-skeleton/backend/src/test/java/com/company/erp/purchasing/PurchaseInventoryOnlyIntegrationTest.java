package com.company.erp.purchasing;

import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** D-73: chế độ chỉ quản lý kho cho đơn mua; đơn giá không bắt buộc. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class PurchaseInventoryOnlyIntegrationTest extends PurchasingIntegrationSupport {

    @Autowired
    PurchaseInventoryOnlyIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @AfterEach
    void restoreMode() {
        jdbc.update("UPDATE sys_app_settings SET setting_value = 'FULL' WHERE setting_key = 'posting.mode'");
    }

    @Test
    @DisplayName("D-73: chỉ quản lý kho, đơn giá trống = 0, ghi sổ chỉ nhập kho (giá vốn 0), không công nợ, bút toán")
    void inventoryOnlyPosting() throws Exception {
        jdbc.update("UPDATE sys_app_settings SET setting_value = 'INVENTORY_ONLY' WHERE setting_key = 'posting.mode'");
        long tube = product("VT_ONG_01");
        long warehouse = warehouse("HH");
        String lot = unique("L");

        long order = jsonId(send(buyer, post(ORDERS), order(partner("NCC_01"), warehouse, List.of(
                lotLine(tube, unit("HOP"), "10", "null", null, lot))), newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(0))
                .andReturn());
        act(keeper, ORDERS, order, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));

        // 10 hộp = 1000 đơn vị cơ sở.
        assertThat(lotOnHand(lot)).isEqualByComparingTo("1000");
        assertLedgerConsistent(tube, warehouse);
        assertThat(jdbc.queryForObject("SELECT MAX(t.unit_cost) FROM inventory_transactions t JOIN inventory_lots l "
                + "ON l.id = t.lot_id WHERE l.lot_no = ?", BigDecimal.class, lot)).isEqualByComparingTo("0");
        assertThat(id("SELECT COUNT(*) FROM debt_items WHERE source_type = 'DMH' AND source_id = " + order)).isZero();
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DMH' AND source_id = " + order))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT financial_posted FROM purchase_orders WHERE id = ?", Boolean.class,
                order)).isFalse();

        act(keeper, ORDERS, order, "unpost", "{\"reason\":\"Sửa đơn\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("0");
        assertLedgerConsistent(tube, warehouse);
    }
}
