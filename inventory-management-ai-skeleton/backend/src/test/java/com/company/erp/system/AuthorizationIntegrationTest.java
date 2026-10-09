package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** AT-10: không có quyền thì không thao tác được; không truy cập dữ liệu ngoài phạm vi kể cả gọi API theo ID. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    @Autowired
    AuthorizationIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("AT-10: người dùng chi nhánh HN chỉ thấy chi nhánh HN; gọi chi nhánh HCM theo ID trả 404")
    void branchScopedUserCannotSeeOtherBranch() throws Exception {
        Session sales = login(mockMvc, "kinhdoanh.hn");

        MvcResult list = mockMvc.perform(as(sales, get("/api/v1/branches")))
                .andExpect(status().isOk())
                .andReturn();
        List<String> codes = json(list, "$.data.items[*].code");
        assertThat(codes).containsExactly("HN");

        mockMvc.perform(as(sales, get("/api/v1/branches/2")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(as(sales, get("/api/v1/departments/3")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(sales, get("/api/v1/branches/1")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AT-10: thiếu quyền trả 403 cho cả thao tác ghi và đọc dữ liệu quản trị")
    void missingPermissionIsForbidden() throws Exception {
        Session sales = login(mockMvc, "kinhdoanh.hn");

        mockMvc.perform(as(sales, post("/api/v1/branches"))
                        .header(IdempotentCommand.HEADER, newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":1,\"code\":\"XX\",\"name\":\"Không được tạo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(as(sales, get("/api/v1/users")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(sales, get("/api/v1/login-logs")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BR-GEN-50: tạo chi nhánh gửi lại cùng Idempotency-Key chỉ tạo một bản ghi; ghi created_by và audit")
    void createBranchIsIdempotentAndAudited() throws Exception {
        Session admin = login(mockMvc, "admin");
        String code = unique("B");
        String key = newKey();
        String body = "{\"companyId\":1,\"code\":\"" + code + "\",\"name\":\"Chi nhánh test\"}";

        MvcResult first = mockMvc.perform(as(admin, post("/api/v1/branches"))
                        .header(IdempotentCommand.HEADER, key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult replay = mockMvc.perform(as(admin, post("/api/v1/branches"))
                        .header(IdempotentCommand.HEADER, key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Integer firstId = json(first, "$.data.id");
        Integer replayId = json(replay, "$.data.id");
        assertThat(replayId).isEqualTo(firstId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM branches WHERE code = ?", Integer.class, code))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT created_by FROM branches WHERE code = ?", Long.class, code))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_logs WHERE entity_type = 'BRANCH' AND entity_id = ? AND action = 'CREATE'",
                Integer.class, firstId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Trùng mã trả 409 DUPLICATE_CODE; thiếu Idempotency-Key trả 400")
    void duplicateCodeAndMissingKey() throws Exception {
        Session admin = login(mockMvc, "admin");

        mockMvc.perform(as(admin, post("/api/v1/branches"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":1,\"code\":\"HN\",\"name\":\"Trùng mã\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        mockMvc.perform(as(admin, post("/api/v1/branches")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":1,\"code\":\"NOKEY\",\"name\":\"Thiếu key\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BR-GEN-53: sửa với version cũ trả 409 CONCURRENT_MODIFICATION")
    void staleVersionIsConflict() throws Exception {
        Session admin = login(mockMvc, "admin");
        MvcResult created = mockMvc.perform(as(admin, post("/api/v1/departments"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":1,\"code\":\"" + unique("D") + "\",\"name\":\"Phòng test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = json(created, "$.data.id");

        mockMvc.perform(as(admin, put("/api/v1/departments/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Đổi tên lần 1\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(admin, put("/api/v1/departments/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Đổi tên dựa trên bản cũ\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("Chống leo thang quyền: không gán được vai trò có quyền vượt quyền của người thao tác")
    void cannotGrantRoleBeyondOwnPermissions() throws Exception {
        Session admin = login(mockMvc, "admin");
        String roleCode = unique("UM_");
        MvcResult role = mockMvc.perform(as(admin, post("/api/v1/roles"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + roleCode + "\",\"name\":\"Quản lý người dùng\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Integer roleId = json(role, "$.data.id");
        mockMvc.perform(as(admin, put("/api/v1/roles/" + roleId + "/permissions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"user:view\",\"user:assign-roles\"]}"))
                .andExpect(status().isOk());

        String managerName = unique("um.").toLowerCase();
        createUser(jdbc, managerName, roleCode, "COMPANY", 1, 1, false);
        Session manager = login(mockMvc, managerName);
        Long adminRoleId = jdbc.queryForObject("SELECT id FROM sys_roles WHERE code = 'SYSTEM_ADMIN'", Long.class);

        mockMvc.perform(as(manager, put("/api/v1/users/3/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[" + adminRoleId + "]}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_roles WHERE user_id = 3 AND role_id = ?", Integer.class, adminRoleId))
                .isZero();
    }

    @Test
    @DisplayName("Không tự đổi vai trò, trạng thái của chính mình")
    void cannotModifyOwnAccess() throws Exception {
        Session admin = login(mockMvc, "admin");

        mockMvc.perform(as(admin, put("/api/v1/users/1/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(admin, patch("/api/v1/users/1/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ngừng hoạt động người dùng: access token đang có hiệu lực bị từ chối ngay")
    void deactivatedUserLosesAccessImmediately() throws Exception {
        Session admin = login(mockMvc, "admin");
        String username = unique("off.").toLowerCase();
        long userId = createUser(jdbc, username, "VIEWER", "BRANCH", 1, 1, false);
        Session victim = login(mockMvc, username);
        mockMvc.perform(as(victim, get("/api/v1/branches"))).andExpect(status().isOk());

        mockMvc.perform(as(admin, patch("/api/v1/users/" + userId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":0,\"reason\":\"Nghỉ việc\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(as(victim, get("/api/v1/branches"))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(victim.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sắp xếp theo trường không cho phép trả 400 (chống truy vấn động tùy ý)")
    void unknownSortFieldIsRejected() throws Exception {
        Session admin = login(mockMvc, "admin");

        mockMvc.perform(as(admin, get("/api/v1/branches").param("sort", "passwordHash,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
