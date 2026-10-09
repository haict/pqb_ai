package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.PASSWORD;
import static com.company.erp.support.ApiTestSupport.REFRESH_COOKIE;
import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.loginResult;
import static com.company.erp.support.ApiTestSupport.sessionOf;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@MySqlIntegrationTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    @Autowired
    AuthIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("Đăng nhập trả access token, quyền, chi nhánh; refresh token ở cookie httpOnly SameSite=Strict")
    void loginReturnsTokensAndProfile() throws Exception {
        MvcResult result = loginResult(mockMvc, "admin", PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) json(result, "$.data.accessToken")).isNotBlank();
        List<String> permissions = json(result, "$.data.user.permissions");
        List<String> branchCodes = json(result, "$.data.user.branches[*].code");
        assertThat(permissions).contains("user:view", "branch:create");
        assertThat(branchCodes).containsExactly("HCM", "HN");
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(REFRESH_COOKIE + "=", "HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(
                result.getResponse().getCookie(REFRESH_COOKIE).getValue());
    }

    @Test
    @DisplayName("Sai mật khẩu và tên không tồn tại trả cùng một lỗi, không tiết lộ tài khoản tồn tại")
    void wrongCredentialsAreIndistinguishable() throws Exception {
        MvcResult wrongPassword = loginResult(mockMvc, "giamdoc", "Sai@2026");
        MvcResult unknownUser = loginResult(mockMvc, "khong.ton.tai", "Sai@2026");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) json(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat((String) json(unknownUser, "$.message")).isEqualTo(json(wrongPassword, "$.message"));
        Integer failures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_login_logs WHERE username = 'khong.ton.tai' AND event = 'LOGIN_FAILED'",
                Integer.class);
        assertThat(failures).isPositive();
    }

    @Test
    @DisplayName("BR-SYS-23: sai mật khẩu 5 lần thì tài khoản bị khóa tạm, mật khẩu đúng cũng không đăng nhập được")
    void accountLocksAfterFiveFailures() throws Exception {
        String username = unique("lock.").toLowerCase();
        createUser(jdbc, username, "VIEWER", "BRANCH", 1, 1, false);

        for (int i = 0; i < 5; i++) {
            assertThat(loginResult(mockMvc, username, "Sai@2026").getResponse().getStatus()).isEqualTo(401);
        }

        assertThat(loginResult(mockMvc, username, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT locked_until IS NOT NULL FROM sys_users WHERE username = ?",
                Boolean.class, username)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_login_logs WHERE username = ? AND event = 'ACCOUNT_LOCKED'",
                Integer.class, username)).isEqualTo(1);
    }

    @Test
    @DisplayName("Làm mới phiên xoay vòng token; gửi lại token cũ bị coi là đánh cắp và thu hồi cả phiên")
    void refreshRotatesAndDetectsReuse() throws Exception {
        Session first = login(mockMvc, "giamdoc");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(first.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn();
        Session second = sessionOf(refreshed);
        assertThat(second.refreshCookie().getValue()).isNotEqualTo(first.refreshCookie().getValue());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(second.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đăng xuất thu hồi phiên: refresh token không dùng được nữa")
    void logoutRevokesSession() throws Exception {
        Session session = login(mockMvc, "giamdoc");

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("Người dùng mới phải đổi mật khẩu trước khi dùng API khác; đổi xong token cũ mất hiệu lực")
    void mustChangePasswordFlow() throws Exception {
        String username = unique("new.").toLowerCase();
        createUser(jdbc, username, "VIEWER", "BRANCH", 1, 1, true);
        Session session = login(mockMvc, username);

        mockMvc.perform(get("/api/v1/branches").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        MvcResult changed = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Moi@2026x\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Session renewed = sessionOf(changed);

        mockMvc.perform(get("/api/v1/branches").header(HttpHeaders.AUTHORIZATION, renewed.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Mật khẩu mới không đạt chính sách trả 400 kèm lỗi theo trường")
    void weakNewPasswordIsRejected() throws Exception {
        String username = unique("weak.").toLowerCase();
        createUser(jdbc, username, "VIEWER", "BRANCH", 1, 1, true);
        Session session = login(mockMvc, username);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"12345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("Token giả mạo hoặc sai chữ ký trả 401")
    void forgedTokenIsRejected() throws Exception {
        Session session = login(mockMvc, "giamdoc");
        String forged = session.accessToken().substring(0, session.accessToken().length() - 4) + "abcd";

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }
}
