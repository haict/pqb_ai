package com.company.erp.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Tiện ích cho test tích hợp API: đăng nhập, tạo người dùng test, đọc JSON. */
public final class ApiTestSupport {

    /** Mật khẩu dev/test của mọi người dùng mẫu (R__dev_organization_and_users.sql). */
    public static final String PASSWORD = "Erp@2026";
    public static final String REFRESH_COOKIE = "ERP_REFRESH";
    private static final String PASSWORD_HASH =
            "{bcrypt}$2a$10$omnYk6mu3pLIWHfa4yY0xuF10bSENe1PXXwInYPN5tSDXbFEtDrtO";

    private ApiTestSupport() {
    }

    public record Session(String accessToken, Cookie refreshCookie) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public static MvcResult loginResult(MockMvc mockMvc, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    public static Session login(MockMvc mockMvc, String username) throws Exception {
        MvcResult result = loginResult(mockMvc, username, PASSWORD);
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Đăng nhập " + username + " thất bại: "
                    + result.getResponse().getContentAsString());
        }
        return sessionOf(result);
    }

    public static Session sessionOf(MvcResult result) throws Exception {
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        return new Session(token, result.getResponse().getCookie(REFRESH_COOKIE));
    }

    public static <T> T json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    public static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /**
     * Tạo người dùng test (mật khẩu {@link #PASSWORD}) trực tiếp trong DB.
     *
     * @param scopeType COMPANY hoặc BRANCH
     */
    public static long createUser(JdbcTemplate jdbc, String username, String roleCode, String scopeType,
                                  long scopeId, long defaultBranchId, boolean mustChangePassword) {
        jdbc.update("""
                INSERT INTO sys_users (company_id, default_branch_id, username, full_name, password_hash, status,
                    own_data_only, must_change_password, credentials_changed_at, created_at, updated_at)
                VALUES (1, ?, ?, ?, ?, 'ACTIVE', FALSE, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, defaultBranchId, username, "Test " + username, PASSWORD_HASH, mustChangePassword);
        Long id = jdbc.queryForObject("SELECT id FROM sys_users WHERE username = ?", Long.class, username);
        jdbc.update("INSERT INTO sys_user_roles (user_id, role_id) SELECT ?, id FROM sys_roles WHERE code = ?",
                id, roleCode);
        jdbc.update("INSERT INTO sys_user_data_scopes (user_id, scope_type, scope_id) VALUES (?, ?, ?)",
                id, scopeType, scopeId);
        return id;
    }
}
