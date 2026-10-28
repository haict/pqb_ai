package com.company.erp.ai;

import static com.company.erp.support.ApiTestSupport.createUser;
import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.ai.application.AiAvailability;
import com.company.erp.ai.application.AssistantRetentionJob;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/** Trợ lý L1 (D-64, AI-3, docs/14 mục 11) với AI Service giả lập. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class AssistantIntegrationTest {

    private static final String CONVERSATIONS = "/api/v1/assistant/conversations";
    private static final String ANSWER = """
            {"answer":"1. Mở Mua hàng > Đơn mua hàng [1].","citations":["5. Mua hàng > 5.1 Đơn mua hàng"],
             "grounded":true,"durationMs":1200}""";

    private static AiServiceStub stub;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final AiAvailability availability;
    private final AssistantRetentionJob retentionJob;
    private Session owner;

    @Autowired
    AssistantIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, AiAvailability availability,
                             AssistantRetentionJob retentionJob) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.availability = availability;
        this.retentionJob = retentionJob;
    }

    @BeforeAll
    static void startStub() throws Exception {
        stub = new AiServiceStub();
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        stub.ready(true);
        stub.chatReply(200, ANSWER);
        stub.received().clear();
        availability.refresh();
        owner = login(mockMvc, "muahang.hn");
    }

    @Test
    @DisplayName("AI-3, AT-13: hỏi đáp có trích nguồn; gửi lại cùng key không tạo câu hỏi trùng, không gọi AI lại")
    void asksAndReplaysWithSameKey() throws Exception {
        String key = newKey();
        long conversationId = createConversation(owner, key);
        assertThat(createConversation(owner, key)).isEqualTo(conversationId);

        String messageKey = newKey();
        MvcResult first = send(owner, conversationId, messageKey, "Lập đơn mua hàng thế nào?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.role").value("USER"))
                .andExpect(jsonPath("$.data.question.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.answer.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.answer.content").value("1. Mở Mua hàng > Đơn mua hàng [1]."))
                .andExpect(jsonPath("$.data.answer.citations[0]").value("5. Mua hàng > 5.1 Đơn mua hàng"))
                .andExpect(jsonPath("$.data.answer.grounded").value(true))
                .andReturn();
        AiServiceStub.Received received = stub.received().get(0);
        assertThat(received.apiKey()).isEqualTo(AiServiceStub.API_KEY);
        assertThat(received.body()).contains("\"question\":\"Lập đơn mua hàng thế nào?\"").contains("\"history\":[]");

        send(owner, conversationId, messageKey, "Lập đơn mua hàng thế nào?").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.id").value((Integer) json(first, "$.data.answer.id")));
        assertThat(stub.received()).hasSize(1);
        assertThat(count("ai_messages WHERE conversation_id = " + conversationId)).isEqualTo(2);
        send(owner, conversationId, messageKey, "Câu khác").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(as(owner, get(CONVERSATIONS))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(conversationId))
                .andExpect(jsonPath("$.data.items[0].title").value("Lập đơn mua hàng thế nào?"));
    }

    @Test
    @DisplayName("Câu hỏi sau gửi kèm lịch sử hội thoại (câu hỏi, câu trả lời trước), cũ trước mới sau")
    void sendsHistory() throws Exception {
        long conversationId = createConversation(owner, newKey());
        send(owner, conversationId, newKey(), "Lập đơn mua hàng thế nào?").andExpect(status().isOk());
        send(owner, conversationId, newKey(), "Sau đó ghi sổ ra sao?").andExpect(status().isOk());

        assertThat(stub.received().get(1).body())
                .contains("\"history\":[{\"role\":\"USER\",\"content\":\"Lập đơn mua hàng thế nào?\"},"
                        + "{\"role\":\"ASSISTANT\",\"content\":\"1. Mở Mua hàng > Đơn mua hàng [1].\"}]");
        mockMvc.perform(as(owner, get(CONVERSATIONS + "/" + conversationId + "/messages")))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items[3].content").value("1. Mở Mua hàng > Đơn mua hàng [1]."));
    }

    @Test
    @DisplayName("AT-10: chưa đăng nhập 401, thiếu assistant:use 403, hội thoại của người khác 404")
    void onlyOwnerCanAccess() throws Exception {
        long conversationId = createConversation(owner, newKey());
        Session other = login(mockMvc, "truongkd.hn");
        String username = unique("noai").toLowerCase();
        createUser(jdbc, username, "NO_SUCH_ROLE", "BRANCH", 1, 1, false);
        Session noPermission = login(mockMvc, username);

        mockMvc.perform(get(CONVERSATIONS)).andExpect(status().isUnauthorized());
        mockMvc.perform(as(noPermission, get(CONVERSATIONS))).andExpect(status().isForbidden());
        mockMvc.perform(as(other, get(CONVERSATIONS + "/" + conversationId + "/messages")))
                .andExpect(status().isNotFound());
        send(other, conversationId, newKey(), "Xin chào").andExpect(status().isNotFound());
        mockMvc.perform(as(other, delete(CONVERSATIONS + "/" + conversationId))).andExpect(status().isNotFound());
        mockMvc.perform(as(other, get(CONVERSATIONS))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + conversationId + ")]").isEmpty());
        assertThat(stub.received()).isEmpty();
    }

    @Test
    @DisplayName("Câu hỏi quá dài 400; AI ngừng 503 rồi gửi lại cùng key khi AI hoạt động thì được trả lời")
    void validationAndRecovery() throws Exception {
        long conversationId = createConversation(owner, newKey());
        send(owner, conversationId, newKey(), "a".repeat(201)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSISTANT_MESSAGE_TOO_LONG"));

        stub.ready(false);
        availability.refresh();
        mockMvc.perform(as(owner, get("/api/v1/ai/health"))).andExpect(jsonPath("$.data.chatAvailable").value(false));
        String key = newKey();
        send(owner, conversationId, key, "Khóa kỳ kế toán?").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT status FROM ai_messages WHERE conversation_id = ?", String.class,
                conversationId)).isEqualTo("FAILED");

        stub.ready(true);
        availability.refresh();
        send(owner, conversationId, key, "Khóa kỳ kế toán?").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.status").value("ANSWERED"));
        assertThat(count("ai_messages WHERE conversation_id = " + conversationId)).isEqualTo(2);
    }

    @Test
    @DisplayName("Lỗi AI Service đổi sang mã lỗi ERP: quá thời gian 504, bận 429, lỗi khác 503")
    void mapsAiErrors() throws Exception {
        long conversationId = createConversation(owner, newKey());
        stub.delay(Duration.ofSeconds(3));
        send(owner, conversationId, newKey(), "Khóa kỳ?").andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_TIMEOUT"));
        stub.chatReply(429, "{\"success\":false,\"code\":\"AI_BUSY\"}");
        send(owner, conversationId, newKey(), "Khóa kỳ?").andExpect(status().isTooManyRequests());
        stub.chatReply(500, "{\"success\":false,\"code\":\"INTERNAL_ERROR\"}");
        send(owner, conversationId, newKey(), "Khóa kỳ?").andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Xóa hội thoại của mình; tác vụ nền xóa hội thoại quá ai.chat.retention-days (Q-05)")
    void deleteAndRetention() throws Exception {
        long deleted = createConversation(owner, newKey());
        send(owner, deleted, newKey(), "Lập đơn mua?").andExpect(status().isOk());
        mockMvc.perform(as(owner, delete(CONVERSATIONS + "/" + deleted))).andExpect(status().isOk());
        assertThat(count("ai_messages WHERE conversation_id = " + deleted)).isZero();

        long old = createConversation(owner, newKey());
        long recent = createConversation(owner, newKey());
        jdbc.update("UPDATE ai_conversations SET last_message_at = UTC_TIMESTAMP(3) - INTERVAL 91 DAY WHERE id = ?",
                old);
        assertThat(retentionJob.deleteExpired()).isGreaterThanOrEqualTo(1);
        assertThat(count("ai_conversations WHERE id = " + old)).isZero();
        assertThat(count("ai_conversations WHERE id = " + recent)).isEqualTo(1);
    }

    private long createConversation(Session session, String key) throws Exception {
        MvcResult result = mockMvc.perform(as(session, post(CONVERSATIONS)).header(IdempotentCommand.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) json(result, "$.data.id")).longValue();
    }

    private ResultActions send(Session session, long conversationId, String key, String content) throws Exception {
        return mockMvc.perform(as(session, post(CONVERSATIONS + "/" + conversationId + "/messages"))
                .header(IdempotentCommand.HEADER, key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"));
    }

    private long count(String from) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + from, Long.class);
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
