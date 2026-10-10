package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.ApprovalStepResult;
import com.company.erp.system.application.ApprovalSubmission;
import com.company.erp.system.application.ApprovalSubmitResult;
import com.company.erp.system.application.CurrentUserLoader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * BR-SYS-11, BR-SYS-14, BR-GEN-05, D-04: luồng duyệt nhiều bước theo vai trò/ngưỡng, tách biệt nhiệm vụ, ủy quyền,
 * từ chối, thông báo; AT-10: không có quyền thì không duyệt. Dùng loại chứng từ test riêng của từng test.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class ApprovalIntegrationTest {

    private static final AtomicLong DOCUMENT_IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final long DIRECTOR = 2L;
    private static final long SALES_HN = 3L;
    private static final long WAREHOUSE_HCM = 4L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ApprovalService approvalService;
    private final CurrentUserLoader userLoader;
    private final TransactionTemplate tx;
    private long chiefAccountant;

    @Autowired
    ApprovalIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, ApprovalService approvalService,
                            CurrentUserLoader userLoader, TransactionTemplate tx) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.approvalService = approvalService;
        this.userLoader = userLoader;
        this.tx = tx;
    }

    @BeforeEach
    void setUp() {
        chiefAccountant = jdbc.queryForObject("SELECT id FROM sys_users WHERE username = 'ketoantruong'", Long.class);
    }

    @Test
    @DisplayName("D-04: loại bắt buộc duyệt mà chưa có luồng thì không gửi duyệt được; loại theo cấu hình thì không cần duyệt")
    void submitWithoutWorkflow() {
        String required = documentType("REQUIRED", false);
        String configured = documentType("CONFIGURED", false);

        assertError(() -> submit(SALES_HN, required, 1L, null), ErrorCode.APPROVAL_NOT_CONFIGURED);
        ApprovalSubmitResult result = submit(SALES_HN, configured, 1L, null);
        assertThat(result.approvalRequired()).isFalse();
    }

    @Test
    @DisplayName("BR-SYS-11: duyệt hai bước theo vai trò; thông báo người duyệt kế tiếp và người gửi; người ngoài không duyệt được")
    void twoStepApproval() throws Exception {
        String type = documentType("REQUIRED", false);
        createWorkflow(type, null, null, 0, roleStep("CHIEF_ACCOUNTANT"), roleStep("DIRECTOR"));
        long documentId = DOCUMENT_IDS.incrementAndGet();

        ApprovalSubmitResult submitted = submit(SALES_HN, type, 1L, null, documentId);
        assertThat(submitted.approvalRequired()).isTrue();
        assertThat(notificationCount(chiefAccountant, "APPROVAL:" + submitted.requestId() + ":STEP:1")).isEqualTo(1);

        assertError(() -> approve(WAREHOUSE_HCM, type, documentId), ErrorCode.FORBIDDEN);
        assertError(() -> approve(DIRECTOR, type, documentId), ErrorCode.FORBIDDEN);
        ApprovalStepResult first = approve(chiefAccountant, type, documentId);
        assertThat(first.completed()).isFalse();
        assertThat(notificationCount(DIRECTOR, "APPROVAL:" + submitted.requestId() + ":STEP:2")).isEqualTo(1);

        Session director = login(mockMvc, "giamdoc");
        MvcResult inbox = mockMvc.perform(as(director, get("/api/v1/approvals/pending")).param("size", "200"))
                .andExpect(status().isOk()).andReturn();
        List<Integer> pendingIds = json(inbox, "$.data.items[*].id");
        assertThat(pendingIds).contains(submitted.requestId().intValue());

        ApprovalStepResult second = approve(DIRECTOR, type, documentId);
        assertThat(second.completed()).isTrue();
        assertThat(notificationCount(SALES_HN, "APPROVAL:" + submitted.requestId() + ":APPROVED")).isEqualTo(1);

        mockMvc.perform(as(director, get("/api/v1/approvals/" + submitted.requestId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.actions.length()").value(3));
        Session warehouse = login(mockMvc, "kho.hcm");
        mockMvc.perform(as(warehouse, get("/api/v1/approvals/" + submitted.requestId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SYS-14: người lập không tự duyệt khi loại chứng từ không cho phép; được phép thì duyệt được")
    void separationOfDuties() throws Exception {
        String strict = documentType("REQUIRED", false);
        String relaxed = documentType("REQUIRED", true);
        createWorkflow(strict, null, null, 0, roleStep("CHIEF_ACCOUNTANT"));
        createWorkflow(relaxed, null, null, 0, roleStep("CHIEF_ACCOUNTANT"));
        long strictId = DOCUMENT_IDS.incrementAndGet();
        long relaxedId = DOCUMENT_IDS.incrementAndGet();

        submit(chiefAccountant, strict, 1L, null, strictId);
        submit(chiefAccountant, relaxed, 1L, null, relaxedId);

        assertError(() -> approve(chiefAccountant, strict, strictId), ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        assertThat(approve(chiefAccountant, relaxed, relaxedId).completed()).isTrue();
    }

    @Test
    @DisplayName("BR-SYS-11: chọn luồng theo ngưỡng giá trị và chi nhánh; luồng cụ thể hơn được ưu tiên khi cùng mức ưu tiên")
    void workflowSelectionByThreshold() throws Exception {
        String type = documentType("CONFIGURED", false);
        createWorkflow(type, null, null, 0, roleStep("CHIEF_ACCOUNTANT"));
        createWorkflow(type, null, new BigDecimal("100000000"), 0, roleStep("CHIEF_ACCOUNTANT"), roleStep("DIRECTOR"));
        createWorkflow(type, 2L, null, 0, roleStep("DIRECTOR"));

        long small = DOCUMENT_IDS.incrementAndGet();
        long large = DOCUMENT_IDS.incrementAndGet();
        long otherBranch = DOCUMENT_IDS.incrementAndGet();
        submit(SALES_HN, type, 1L, new BigDecimal("50000000"), small);
        submit(SALES_HN, type, 1L, new BigDecimal("150000000"), large);
        submit(WAREHOUSE_HCM, type, 2L, new BigDecimal("150000000"), otherBranch);

        assertThat(totalSteps(type, small)).isEqualTo(1);
        assertThat(totalSteps(type, large)).isEqualTo(2);
        assertThat(totalSteps(type, otherBranch)).isEqualTo(1);
        assertThat(approve(DIRECTOR, type, otherBranch).completed()).isTrue();
    }

    @Test
    @DisplayName("BR-SYS-11: người được ủy quyền duyệt thay trong thời hạn; thu hồi thì hết quyền")
    void delegation() throws Exception {
        String type = documentType("REQUIRED", false);
        createWorkflow(type, null, null, 0, roleStep("DIRECTOR"));
        long deputy = createUser(jdbc, "phogiamdoc.1d", "VIEWER", "COMPANY", 1, 1, false);
        long first = DOCUMENT_IDS.incrementAndGet();
        long second = DOCUMENT_IDS.incrementAndGet();
        submit(SALES_HN, type, 1L, null, first);
        submit(SALES_HN, type, 1L, null, second);
        assertError(() -> approve(deputy, type, first), ErrorCode.FORBIDDEN);

        Session director = login(mockMvc, "giamdoc");
        MvcResult created = mockMvc.perform(as(director, post("/api/v1/approval-delegations"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delegateUserId\":" + deputy + ",\"validTo\":\""
                                + Instant.now().plus(1, ChronoUnit.DAYS) + "\",\"reason\":\"Đi công tác\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.effective").value(true))
                .andReturn();
        Integer delegationId = json(created, "$.data.id");

        assertThat(approve(deputy, type, first).completed()).isTrue();
        assertThat(jdbc.queryForObject("SELECT on_behalf_of_user_id FROM approval_actions a JOIN approval_requests r "
                + "ON r.id = a.request_id WHERE r.document_type = ? AND r.document_id = ? AND a.action = 'APPROVE'",
                Long.class, type, first)).isEqualTo(DIRECTOR);

        Session sales = login(mockMvc, "kinhdoanh.hn");
        mockMvc.perform(as(sales, post("/api/v1/approval-delegations/" + delegationId + "/revoke")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(director, post("/api/v1/approval-delegations/" + delegationId + "/revoke")))
                .andExpect(status().isOk());
        assertError(() -> approve(deputy, type, second), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Từ chối bắt buộc lý do, kết thúc yêu cầu, báo người gửi; gửi lại tạo yêu cầu mới; không gửi trùng khi đang chờ")
    void rejectAndResubmit() throws Exception {
        String type = documentType("REQUIRED", false);
        createWorkflow(type, null, null, 0, roleStep("CHIEF_ACCOUNTANT"));
        long documentId = DOCUMENT_IDS.incrementAndGet();
        ApprovalSubmitResult first = submit(SALES_HN, type, 1L, null, documentId);
        assertError(() -> submit(SALES_HN, type, 1L, null, documentId), ErrorCode.INVALID_DOCUMENT_STATE);

        assertError(() -> inTx(() -> {
            approvalService.reject(userLoader.load(chiefAccountant), type, documentId, " ");
            return null;
        }), ErrorCode.VALIDATION_ERROR);
        inTx(() -> {
            approvalService.reject(userLoader.load(chiefAccountant), type, documentId, "Thiếu chứng từ kèm theo");
            return null;
        });
        assertThat(notificationCount(SALES_HN, "APPROVAL:" + first.requestId() + ":REJECTED")).isEqualTo(1);
        assertError(() -> approve(chiefAccountant, type, documentId), ErrorCode.INVALID_DOCUMENT_STATE);

        ApprovalSubmitResult second = submit(SALES_HN, type, 1L, null, documentId);
        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(approvalService.history(type, documentId)).hasSize(2);
    }

    @Test
    @DisplayName("BR-GEN-52: hai người cùng duyệt một bước đồng thời: một người hoàn tất, người kia nhận lỗi trạng thái")
    void concurrentApproval() throws Exception {
        String type = documentType("REQUIRED", false);
        createWorkflow(type, null, null, 0, roleStep("CHIEF_ACCOUNTANT"));
        long secondChief = createUser(jdbc, "ketoantruong2.1d", "CHIEF_ACCOUNTANT", "COMPANY", 1, 1, false);
        long documentId = DOCUMENT_IDS.incrementAndGet();
        submit(SALES_HN, type, 1L, null, documentId);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (long approver : List.of(chiefAccountant, secondChief)) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        return approve(approver, type, documentId).completed() ? "COMPLETED" : "PARTIAL";
                    } catch (BusinessException ex) {
                        return ex.errorCode().name();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("COMPLETED", "INVALID_DOCUMENT_STATE");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("AT-10: cấu hình luồng duyệt cần quyền; bước duyệt sai trả 400 trên đúng trường; cấu hình loại chứng từ có kiểm tra version")
    void workflowAdministration() throws Exception {
        String type = documentType("CONFIGURED", false);
        Session sales = login(mockMvc, "kinhdoanh.hn");
        workflowRequest(sales, type, "{\"approverType\":\"ROLE\",\"roleId\":" + roleId("DIRECTOR") + "}")
                .andExpect(status().isForbidden());

        Session admin = login(mockMvc, "admin");
        workflowRequest(admin, type, "{\"approverType\":\"ROLE\",\"roleId\":999999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("steps[0]"));
        workflowRequest(admin, type, "{\"approverType\":\"USER\",\"userId\":" + DIRECTOR + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.steps[0].userName").value("Giám đốc (mẫu)"));

        mockMvc.perform(as(admin, put("/api/v1/document-types/" + type)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalMode\":\"REQUIRED\",\"selfApprovalAllowed\":true,\"version\":5}"))
                .andExpect(status().isConflict());
        mockMvc.perform(as(admin, put("/api/v1/document-types/" + type)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalMode\":\"REQUIRED\",\"selfApprovalAllowed\":true,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalMode").value("REQUIRED"));
    }

    @Test
    @DisplayName("BR-SYS-25: xem, đếm, đánh dấu đã đọc thông báo của mình; thông báo của người khác trả 404")
    void notifications() throws Exception {
        String type = documentType("REQUIRED", false);
        createWorkflow(type, null, null, 0, roleStep("CHIEF_ACCOUNTANT"));
        ApprovalSubmitResult submitted = submit(SALES_HN, type, 1L, null, DOCUMENT_IDS.incrementAndGet());
        Long notificationId = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ? AND event_key = ?",
                Long.class, chiefAccountant, "APPROVAL:" + submitted.requestId() + ":STEP:1");

        Session chief = login(mockMvc, "ketoantruong");
        mockMvc.perform(as(chief, get("/api/v1/notifications")).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].linkType").value(type));
        Session sales = login(mockMvc, "kinhdoanh.hn");
        mockMvc.perform(as(sales, post("/api/v1/notifications/" + notificationId + "/read")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(chief, post("/api/v1/notifications/" + notificationId + "/read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        mockMvc.perform(as(chief, post("/api/v1/notifications/read-all"))).andExpect(status().isOk());
        mockMvc.perform(as(chief, get("/api/v1/notifications/unread-count")))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    private String documentType(String mode, boolean selfApprovalAllowed) {
        String code = "T" + DOCUMENT_IDS.incrementAndGet();
        jdbc.update("INSERT INTO sys_document_types (code, module, name, approval_mode, self_approval_allowed) "
                + "VALUES (?, 'TEST', ?, ?, ?)", code, "Chứng từ test " + code, mode, selfApprovalAllowed);
        return code;
    }

    private void createWorkflow(String type, Long branchId, BigDecimal minAmount, int priority, String... steps)
            throws Exception {
        Session admin = login(mockMvc, "admin");
        mockMvc.perform(as(admin, post("/api/v1/approval-workflows"))
                        .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":1,\"documentType\":\"" + type + "\",\"name\":\"Luồng " + type
                                + "\",\"branchId\":" + branchId + ",\"minAmount\":" + minAmount + ",\"priority\":"
                                + priority + ",\"steps\":[" + String.join(",", steps) + "]}"))
                .andExpect(status().isCreated());
    }

    private ResultActions workflowRequest(Session session, String type, String step) throws Exception {
        return mockMvc.perform(as(session, post("/api/v1/approval-workflows"))
                .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyId\":1,\"documentType\":\"" + type + "\",\"name\":\"Luồng\",\"priority\":0,"
                        + "\"steps\":[" + step + "]}"));
    }

    private String roleStep(String roleCode) {
        return "{\"approverType\":\"ROLE\",\"roleId\":" + roleId(roleCode) + "}";
    }

    private long roleId(String code) {
        return jdbc.queryForObject("SELECT id FROM sys_roles WHERE code = ?", Long.class, code);
    }

    private ApprovalSubmitResult submit(long userId, String type, long branchId, BigDecimal amount) {
        return submit(userId, type, branchId, amount, DOCUMENT_IDS.incrementAndGet());
    }

    private ApprovalSubmitResult submit(long userId, String type, long branchId, BigDecimal amount, long documentId) {
        return inTx(() -> approvalService.submit(userLoader.load(userId),
                new ApprovalSubmission(1L, branchId, type, documentId, "TEST-" + documentId, amount, null)));
    }

    private ApprovalStepResult approve(long userId, String type, long documentId) {
        return inTx(() -> approvalService.approve(userLoader.load(userId), type, documentId, null));
    }

    private int totalSteps(String type, long documentId) {
        return jdbc.queryForObject("SELECT total_steps FROM approval_requests WHERE document_type = ? "
                + "AND document_id = ?", Integer.class, type, documentId);
    }

    private int notificationCount(long userId, String eventKey) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND event_key = ?",
                Integer.class, userId, eventKey);
    }

    private <T> T inTx(Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(expected));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
