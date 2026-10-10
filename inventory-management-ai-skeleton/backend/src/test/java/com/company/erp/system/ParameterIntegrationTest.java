package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** BR-SYS-13: tham số hệ thống theo công ty, kiểm tra giá trị, sửa đồng thời, audit, phân quyền. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class ParameterIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    @Autowired
    ParameterIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("Danh sách tham số trả giá trị mặc định D-05 khi công ty chưa đặt giá trị riêng")
    void listsDefaults() throws Exception {
        Session admin = login(mockMvc, "admin");

        MvcResult result = mockMvc.perform(as(admin, get("/api/v1/parameters")).param("companyId", "1"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> keys = json(result, "$.data[*].key");
        assertThat(keys).contains("decimal.quantity-scale", "decimal.unit-price-scale", "decimal.amount-scale");
        List<String> unitPriceScale = json(result, "$.data[?(@.key == 'decimal.unit-price-scale')].defaultValue");
        assertThat(unitPriceScale).containsExactly("4");
    }

    @Test
    @DisplayName("BR-SYS-13, BR-GEN-53: sửa tham số ghi audit trước/sau; version cũ trả 409")
    void updateIsVersionedAndAudited() throws Exception {
        Session admin = login(mockMvc, "admin");
        // Tham số không khóa sau ghi sổ: test không phụ thuộc việc công ty mẫu đã có số liệu kho hay chưa.
        String key = "inventory.expiry-warning-days";

        update(admin, key, "60", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("60"))
                .andExpect(jsonPath("$.data.customized").value(true))
                .andExpect(jsonPath("$.data.version").value(1));
        update(admin, key, "30", 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        update(admin, key, "30", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        assertThat(jdbc.queryForObject("SELECT param_value FROM sys_parameters WHERE company_id = 1 AND param_key = ?",
                String.class, key)).isEqualTo("30");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_logs WHERE entity_type = 'PARAMETER' AND action = 'UPDATE' "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(after_value, '$.key')) = ?", Integer.class, key))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Giá trị sai kiểu hoặc ngoài giới hạn trả 400 trên trường value; tham số không tồn tại trả 404")
    void rejectsInvalidValues() throws Exception {
        Session admin = login(mockMvc, "admin");

        String key = "inventory.expiry-warning-days";
        update(admin, key, "0", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("value"));
        update(admin, key, "1.5", 0).andExpect(status().isBadRequest());
        update(admin, key, "abc", 0).andExpect(status().isBadRequest());
        update(admin, "khong.ton.tai", "1", 0).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AT-10: không có quyền thì không xem, không sửa tham số")
    void requiresPermission() throws Exception {
        Session sales = login(mockMvc, "kinhdoanh.hn");
        mockMvc.perform(as(sales, get("/api/v1/parameters")).param("companyId", "1"))
                .andExpect(status().isForbidden());

        Session director = login(mockMvc, "giamdoc");
        update(director, "decimal.amount-scale", "1", 0).andExpect(status().isForbidden());
    }

    private ResultActions update(Session session, String key, String value, int version) throws Exception {
        return mockMvc.perform(as(session, put("/api/v1/parameters/" + key)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyId\":1,\"value\":\"" + value + "\",\"version\":" + version + "}"));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
