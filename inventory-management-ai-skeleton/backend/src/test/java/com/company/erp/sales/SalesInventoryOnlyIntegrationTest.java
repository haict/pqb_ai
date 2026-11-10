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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** D-73: chế độ chỉ quản lý kho cho đơn bán; đổi chế độ giữa chừng gỡ đúng phần đã ghi. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class SalesInventoryOnlyIntegrationTest extends SalesIntegrationSupport {

    private static final String UNPOST = "{\"reason\":\"Sửa đơn\"}";

    @Autowired
    SalesInventoryOnlyIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        super(mockMvc, jdbc);
    }

    @AfterEach
    void restoreMode() {
        mode("FULL");
    }

    @Test
    @DisplayName("D-73: chỉ quản lý kho, đơn giá trống = 0, ghi sổ chỉ xuất kho, không công nợ, bút toán, tín dụng")
    void inventoryOnlyPosting() throws Exception {
        mode("INVENTORY_ONLY");
        mockMvc.perform(as(seller, get("/api/v1/posting-mode"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("INVENTORY_ONLY"));
        long item = newProduct(true, false);
        String lot = unique("L");
        stockIn(item, "5", lot, "2028-06-30", null);

        long order = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), warehouse("HH"), false,
                List.of(line(item, unit("CAI"), "3", null, null))), newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andReturn());
        // Thủ kho không có quyền vượt tín dụng: ghi sổ được vì chế độ này không kiểm tra công nợ của khách.
        act(keeper, ORDERS, order, "post", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));

        assertThat(lotOnHand(lot)).isEqualByComparingTo("2");
        assertNoFinancialEntries(order);
        assertThat(financialPosted(order)).isFalse();

        act(chief, ORDERS, order, "unpost", UNPOST).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("5");
        assertNoFinancialEntries(order);
    }

    @Test
    @DisplayName("D-73, AT-09: ghi sổ ở chế độ chỉ kho, chuyển FULL rồi bỏ ghi sổ chỉ đảo kho; ghi sổ lại có công nợ")
    void switchModeKeepsPostedScope() throws Exception {
        mode("INVENTORY_ONLY");
        long item = newProduct(true, false);
        String lot = unique("L");
        stockIn(item, "5", lot, "2028-06-30", null);
        long order = jsonId(send(manager, post(ORDERS), order(partner("KH_BV01"), warehouse("HH"), false,
                List.of(line(item, unit("CAI"), "2", "100000", null))), newKey())
                .andExpect(status().isCreated()).andReturn());
        act(keeper, ORDERS, order, "post", null).andExpect(jsonPath("$.data.status").value("POSTED"));
        assertNoFinancialEntries(order);

        mode("FULL");
        act(chief, ORDERS, order, "unpost", UNPOST).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        assertThat(lotOnHand(lot)).isEqualByComparingTo("5");
        assertNoFinancialEntries(order);

        postWithAuthority(keeper, order).andExpect(jsonPath("$.data.status").value("POSTED"));
        BigDecimal total = jdbc.queryForObject("SELECT total_amount FROM sales_orders WHERE id = ?", BigDecimal.class,
                order);
        assertThat(receivable(order)).isEqualByComparingTo(total);
        assertThat(journal("DBH", order)).isNotEmpty();
        assertThat(financialPosted(order)).isTrue();
    }

    private void mode(String value) {
        jdbc.update("UPDATE sys_app_settings SET setting_value = ? WHERE setting_key = 'posting.mode'", value);
    }

    private boolean financialPosted(long order) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT financial_posted FROM sales_orders WHERE id = ?",
                Boolean.class, order));
    }

    private void assertNoFinancialEntries(long order) {
        assertThat(id("SELECT COUNT(*) FROM debt_items WHERE source_type = 'DBH' AND source_id = " + order))
                .isZero();
        assertThat(id("SELECT COUNT(*) FROM journal_entries WHERE source_type = 'DBH' AND source_id = " + order))
                .isZero();
    }
}
