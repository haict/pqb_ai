package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Quản trị gán được vai trò nghiệp vụ có quyền mình không dùng (access:grant-any), nhưng không tự nâng quyền qua
 * việc sửa quyền của vai trò mình đang giữ (docs/07 mục 2).
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class AccessGrantIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    @Autowired
    AccessGrantIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("Quản trị gán vai trò Kế toán trưởng (có quyền mở lại kỳ quản trị không có) cho người khác được")
    void adminAssignsBusinessRole() throws Exception {
        long user = createUser(jdbc, "nhanvien.grant", "VIEWER", "COMPANY", 1, 1, false);
        long chief = jdbc.queryForObject("SELECT id FROM sys_roles WHERE code = 'CHIEF_ACCOUNTANT'", Long.class);
        Session admin = login(mockMvc, "admin");

        mockMvc.perform(as(admin, put("/api/v1/users/" + user + "/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[" + chief + "]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Không tự nâng quyền: sửa quyền của vai trò mình đang giữ trả 403")
    void cannotEditOwnRolePermissions() throws Exception {
        long adminRole = jdbc.queryForObject("SELECT id FROM sys_roles WHERE code = 'SYSTEM_ADMIN'", Long.class);
        Session admin = login(mockMvc, "admin");

        mockMvc.perform(as(admin, put("/api/v1/roles/" + adminRole + "/permissions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"period:unlock\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
