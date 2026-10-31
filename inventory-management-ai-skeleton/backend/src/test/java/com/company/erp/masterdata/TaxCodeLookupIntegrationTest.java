package com.company.erp.masterdata;

import static com.company.erp.support.ApiTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * D-66: tra cứu doanh nghiệp theo mã số thuế qua Xinvoice giả lập (cổng 18091 theo {@code application-test.yml}); ánh
 * xạ HTTP status sang lỗi API, gửi client-id / api-key, phân quyền, mã sai định dạng không gọi ra ngoài.
 */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class TaxCodeLookupIntegrationTest {

    private static final String URL = "/api/v1/partners/tax-code-lookup";
    private static HttpServer server;
    private static final List<String> paths = new CopyOnWriteArrayList<>();
    private static final List<String> credentials = new CopyOnWriteArrayList<>();
    private static volatile int replyStatus;
    private static volatile String replyBody;
    private static volatile long delayMillis;

    private final MockMvc mockMvc;

    @Autowired
    TaxCodeLookupIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18091), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/gdt-api/tax-payer", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            credentials.add(exchange.getRequestHeaders().getFirst("client-id") + "/"
                    + exchange.getRequestHeaders().getFirst("api-key"));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = replyBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(replyStatus, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    @BeforeEach
    void reset() {
        paths.clear();
        credentials.clear();
        delayMillis = 0;
        reply(200, "{}");
    }

    @Test
    @DisplayName("D-66: tra cứu thành công trả tên, địa chỉ, tình trạng; mã số thuế nối vào đường dẫn, gửi khóa API")
    void found() throws Exception {
        reply(200, """
                {"orgType":"Doanh nghiệp / Đơn vị sự nghiệp công lập","taxID":"0311315789",
                 "name":"CÔNG TY CỔ PHẦN TẬP ĐOÀN BAMBOO CAPITAL",
                 "address":"22A đường số 7, Phường Bình Trưng, TP Hồ Chí Minh",
                 "taxDepartment":"Thuế cơ sở 2 Thành phố Hồ Chí Minh","status":"NNT đang hoạt động",
                 "updatedAt":"2026-07-01T14:30:32.000Z"}""");
        lookup(login(mockMvc, "admin"), " 0311315789 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxCode").value("0311315789"))
                .andExpect(jsonPath("$.data.name").value("CÔNG TY CỔ PHẦN TẬP ĐOÀN BAMBOO CAPITAL"))
                .andExpect(jsonPath("$.data.address").value("22A đường số 7, Phường Bình Trưng, TP Hồ Chí Minh"))
                .andExpect(jsonPath("$.data.shortName").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("NNT đang hoạt động"));
        assertThat(paths).containsExactly("/gdt-api/tax-payer/0311315789");
        assertThat(credentials).containsExactly("test-client/test-api-key");
    }

    @Test
    @DisplayName("D-66: không tồn tại 404, vượt tần suất 429, sai khóa / lỗi nguồn 503, quá giờ 504")
    void errors() throws Exception {
        Session admin = login(mockMvc, "admin");
        reply(404, "{\"success\":false,\"message\":\"Tax not found - Mã số thuế không tìm thấy\"}");
        lookup(admin, "0399999901").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAX_CODE_NOT_FOUND"));
        reply(401, "{\"success\":false}");
        lookup(admin, "0399999901").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TAX_CODE_LOOKUP_UNAVAILABLE"));
        reply(429, "{}");
        lookup(admin, "0399999901").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        reply(500, "lỗi");
        lookup(admin, "0399999901").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TAX_CODE_LOOKUP_UNAVAILABLE"));
        reply(200, "không phải JSON");
        lookup(admin, "0399999901").andExpect(status().isServiceUnavailable());
        reply(200, "{\"taxID\":\"0399999901\"}");
        lookup(admin, "0399999901").andExpect(status().isServiceUnavailable());
        delayMillis = 1500;
        lookup(admin, "0399999901").andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("TAX_CODE_LOOKUP_TIMEOUT"));
    }

    @Test
    @DisplayName("D-66: mã sai định dạng 400 không gọi ra ngoài; thiếu quyền partner:manage 403; chưa đăng nhập 401")
    void validationAndSecurity() throws Exception {
        lookup(login(mockMvc, "admin"), "03113157").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("taxCode"));
        lookup(login(mockMvc, "kinhdoanh.hn"), "0311315789").andExpect(status().isForbidden());
        mockMvc.perform(get(URL).param("taxCode", "0311315789")).andExpect(status().isUnauthorized());
        assertThat(paths).isEmpty();
    }

    private ResultActions lookup(Session session, String taxCode) throws Exception {
        return mockMvc.perform(get(URL).param("taxCode", taxCode)
                .header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }

    private static void reply(int status, String body) {
        replyStatus = status;
        replyBody = body;
    }
}
