package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.company.erp.support.TestLicenses;
import com.company.erp.system.application.LicenseService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@MySqlIntegrationTest
@AutoConfigureMockMvc
class LicenseIntegrationTest {

    private static final String LICENSE = "/api/v1/license";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final LicenseService licenseService;

    @Autowired
    LicenseIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, LicenseService licenseService) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.licenseService = licenseService;
    }

    /** Các test nghiệp vụ khác dùng chung context: luôn trả về license hợp lệ cho mọi công ty và mốc đồng hồ sạch. */
    @AfterEach
    void restoreLicense() throws Exception {
        jdbc.update("DELETE FROM sys_license_clock");
        install(login(mockMvc, "admin"), TestLicenses.sign(unique("RESTORE"), List.of("*"),
                LocalDate.of(2099, 12, 31), 15, Instant.now())).andExpect(status().isOk());
        licenseService.refresh();
    }

    @Test
    @DisplayName("D-59: công ty đang hoạt động ngoài license thì chỉ đọc; vẫn xem, đăng nhập, nạp license được")
    void companyNotLicensedIsReadOnly() throws Exception {
        Session admin = login(mockMvc, "admin");
        mockMvc.perform(as(admin, get(LICENSE))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("VALID"))
                .andExpect(jsonPath("$.data.writable").value(true));

        install(admin, TestLicenses.sign(unique("OTHER"), List.of("0000000000"), today().plusDays(30), 15,
                Instant.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("COMPANY_NOT_LICENSED"))
                .andExpect(jsonPath("$.data.writable").value(false))
                .andExpect(jsonPath("$.data.unlicensedCompanies").isNotEmpty());

        write(admin).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LICENSE_INACTIVE"));
        mockMvc.perform(as(admin, get("/api/v1/products")).param("companyId", "1")).andExpect(status().isOk());
        login(mockMvc, "admin");
    }

    @Test
    @DisplayName("D-59: từ chối license sai định dạng, khóa không tin cậy, nội dung bị sửa, đã hết ân hạn")
    void rejectsInvalidLicenses() throws Exception {
        Session admin = login(mockMvc, "admin");
        String valid = TestLicenses.sign(unique("OK"), List.of("*"), today().plusDays(30), 15, Instant.now());
        for (String bad : List.of("khong-phai-license",
                TestLicenses.signWithUntrustedKey(unique("FAKE"), today().plusYears(10)),
                TestLicenses.tamperValidTo(valid, today().plusYears(10)),
                TestLicenses.sign(unique("OLD"), List.of("*"), today().minusDays(30), 5, Instant.now()))) {
            install(admin, bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("license"));
        }
        mockMvc.perform(as(admin, get(LICENSE))).andExpect(jsonPath("$.data.state").value("VALID"));
    }

    @Test
    @DisplayName("D-59: quá ngày hết hạn nhưng trong ân hạn vẫn ghi được, báo số ngày còn lại")
    void gracePeriodStillWritable() throws Exception {
        Session admin = login(mockMvc, "admin");
        install(admin, TestLicenses.sign(unique("GRACE"), List.of("*"), today().minusDays(3), 10, Instant.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("GRACE"))
                .andExpect(jsonPath("$.data.writable").value(true))
                .andExpect(jsonPath("$.data.daysRemaining").value(7));
        write(admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("D-59: mốc thời gian bị sửa hoặc đồng hồ máy chủ bị lùi thì chỉ đọc")
    void clockTamperingIsReadOnly() throws Exception {
        Session admin = login(mockMvc, "admin");
        jdbc.update("UPDATE sys_license_clock SET max_seen_at = ?",
                Timestamp.from(Instant.now().plus(10, ChronoUnit.DAYS)));
        licenseService.refresh();
        mockMvc.perform(as(admin, get(LICENSE))).andExpect(jsonPath("$.data.state").value("CLOCK_TAMPERED"))
                .andExpect(jsonPath("$.data.writable").value(false));
        write(admin).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LICENSE_INACTIVE"));
    }

    @Test
    @DisplayName("D-59, AT-10: người không có quyền license:manage chỉ xem trạng thái, không nạp được license")
    void installRequiresPermission() throws Exception {
        String username = unique("viewer").toLowerCase();
        createUser(jdbc, username, "VIEWER", "COMPANY", 1, 1, false);
        Session viewer = login(mockMvc, username);
        mockMvc.perform(as(viewer, get(LICENSE))).andExpect(status().isOk());
        install(viewer, TestLicenses.sign(unique("X"), List.of("*"), today().plusDays(30), 15, Instant.now()))
                .andExpect(status().isForbidden());
    }

    private ResultActions install(Session session, String license) throws Exception {
        return mockMvc.perform(as(session, put(LICENSE)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"license\":\"" + license + "\"}"));
    }

    /** Một lệnh ghi bất kỳ: khi được ghi thì lỗi validation 400 (body trống), khi chỉ đọc thì 403. */
    private ResultActions write(Session session) throws Exception {
        return mockMvc.perform(as(session, post("/api/v1/products")).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", unique("k")).content("{}"));
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
