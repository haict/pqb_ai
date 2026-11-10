package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** D-72: cấu hình ẩn / đổi tên menu, nút và tên, logo theo khách hàng; sửa bằng SQL, ứng dụng chỉ đọc. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class UiCustomizationIntegrationTest {

    private static final String SALES_ORDERS = "menu:/sales/orders";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    @Autowired
    UiCustomizationIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("D-72: migration bật sẵn mọi menu, nút (mặc định bật hết, không đổi tên)")
    void everythingEnabledByDefault() throws Exception {
        Session sales = login(mockMvc, "kinhdoanh.hn");

        MvcResult result = mockMvc.perform(get("/api/v1/ui-features")
                        .header(HttpHeaders.AUTHORIZATION, sales.bearer()))
                .andExpect(status().isOk())
                .andReturn();

        List<String> keys = json(result, "$.data[*].key");
        assertThat(keys).contains("group:purchasing", SALES_ORDERS, "action:excel-import", "action:ai-assistant");
        List<Boolean> enabled = json(result, "$.data[*].enabled");
        assertThat(enabled).containsOnly(true);
        List<Object> labels = json(result, "$.data[?(@.customLabel != null)].key");
        assertThat(labels).isEmpty();
    }

    @Test
    @DisplayName("D-72: sửa bằng SQL (ẩn, đổi tên) có hiệu lực ngay ở lần đọc sau; tên trống coi như không đổi")
    void reflectsDirectDatabaseChanges() throws Exception {
        Session admin = login(mockMvc, "admin");
        try {
            jdbc.update("UPDATE sys_ui_features SET enabled = FALSE WHERE feature_key = 'group:finance'");
            jdbc.update("UPDATE sys_ui_features SET custom_label = '  Phiếu đặt hàng ' WHERE feature_key = ?",
                    SALES_ORDERS);
            jdbc.update("UPDATE sys_ui_features SET custom_label = '   ' WHERE feature_key = 'menu:/sales/returns'");

            MvcResult result = mockMvc.perform(get("/api/v1/ui-features")
                            .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                    .andExpect(status().isOk())
                    .andReturn();

            List<Boolean> finance = json(result, "$.data[?(@.key == 'group:finance')].enabled");
            assertThat(finance).containsExactly(false);
            List<String> salesOrders = json(result, "$.data[?(@.key == '" + SALES_ORDERS + "')].customLabel");
            assertThat(salesOrders).containsExactly("Phiếu đặt hàng");
            List<Map<String, Object>> returns = json(result, "$.data[?(@.key == 'menu:/sales/returns')]");
            assertThat(returns).hasSize(1);
            assertThat(returns.get(0).get("customLabel")).isNull();
        } finally {
            jdbc.update("UPDATE sys_ui_features SET enabled = TRUE, custom_label = NULL");
        }
    }

    @Test
    @DisplayName("AT-10: cấu hình menu cần đăng nhập; tên, logo công khai cho trang đăng nhập")
    void authentication() throws Exception {
        mockMvc.perform(get("/api/v1/ui-features")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/public/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appName").doesNotExist())
                .andExpect(jsonPath("$.data.logoVersion").doesNotExist());
        mockMvc.perform(get("/api/v1/public/branding/logo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("D-72: tên ứng dụng đặt bằng SQL hiện ở API công khai")
    void appNameFromDatabase() throws Exception {
        try {
            jdbc.update("UPDATE sys_app_settings SET setting_value = 'Công ty ABC' "
                    + "WHERE setting_key = 'branding.app-name'");
            mockMvc.perform(get("/api/v1/public/branding"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.appName").value("Công ty ABC"));
        } finally {
            jdbc.update("UPDATE sys_app_settings SET setting_value = NULL WHERE setting_key = 'branding.app-name'");
        }
    }
}
