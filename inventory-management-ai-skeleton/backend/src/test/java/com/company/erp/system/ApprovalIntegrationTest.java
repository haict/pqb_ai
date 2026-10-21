package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
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
 * D-61: hệ thống không còn bước duyệt. Sau migration mọi loại chứng từ không qua duyệt, luồng duyệt ngừng hoạt động,
 * không còn quyền duyệt / cấu hình luồng duyệt; API việc chờ duyệt, luồng duyệt, ủy quyền, sửa chế độ duyệt đã gỡ.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class ApprovalIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    @Autowired
    ApprovalIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("D-61: mọi loại chứng từ không qua duyệt, luồng duyệt ngừng hoạt động, không còn quyền duyệt")
    void noApprovalConfiguration() {
        assertThat(count("SELECT COUNT(*) FROM sys_document_types WHERE approval_mode <> 'NONE'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM approval_workflows WHERE status = 'ACTIVE'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM sys_permissions WHERE code LIKE '%:approve' "
                + "OR code LIKE 'approval-workflow:%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM sys_permissions WHERE code IN ('cash-document:post-over-threshold', "
                + "'purchase-order:post-over-threshold', 'sales-order:override-price', 'sales-order:override-credit', "
                + "'recall:manage', 'stock-policy:activate')")).isEqualTo(6);
    }

    @Test
    @DisplayName("D-61, AT-10: API việc chờ duyệt, luồng duyệt, ủy quyền, sửa chế độ duyệt đã gỡ; danh mục loại chứng từ chỉ đọc")
    void approvalEndpointsRemoved() throws Exception {
        Session admin = login(mockMvc, "giamdoc");
        mockMvc.perform(as(admin, get("/api/v1/approvals/pending"))).andExpect(status().isNotFound());
        mockMvc.perform(as(admin, get("/api/v1/approval-workflows"))).andExpect(status().isNotFound());
        mockMvc.perform(as(admin, get("/api/v1/approval-delegations"))).andExpect(status().isNotFound());
        mockMvc.perform(as(admin, put("/api/v1/document-types/DMH")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalMode\":\"REQUIRED\",\"selfApprovalAllowed\":false,\"version\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(admin, post("/api/v1/purchase-orders/1/approve"))
                .header(IdempotentCommand.HEADER, newKey())).andExpect(status().isNotFound());
        mockMvc.perform(as(admin, get("/api/v1/document-types"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'DMH')].name").isNotEmpty())
                .andExpect(jsonPath("$.data[0].approvalMode").doesNotExist());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken());
    }
}
