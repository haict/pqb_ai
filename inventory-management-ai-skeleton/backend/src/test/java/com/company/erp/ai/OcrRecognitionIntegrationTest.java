package com.company.erp.ai;

import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.ai.application.AiAvailability;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import com.company.erp.support.TestLicenses;
import com.company.erp.system.application.LicenseService;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Nhận dạng chứng từ điền form đơn mua (D-64, docs/14 mục 11) với AI Service giả lập. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class OcrRecognitionIntegrationTest {

    private static final String RECOGNITIONS = "/api/v1/ocr/recognitions";
    private static final byte[] PDF = "%PDF-1.7 test".getBytes();

    private static AiServiceStub stub;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final AiAvailability availability;
    private final LicenseService licenseService;
    private Session buyer;

    @Autowired
    OcrRecognitionIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, AiAvailability availability,
                                  LicenseService licenseService) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.availability = availability;
        this.licenseService = licenseService;
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
        stub.reply(200, invoiceJson());
        stub.received().clear();
        availability.refresh();
        buyer = login(mockMvc, "muahang.hn");
    }

    @Test
    @DisplayName("BR-AI-04, BR-AI-06: hóa đơn NCC -> gợi ý form đơn mua; không tạo chứng từ; chỉ ghi metadata")
    void recognizesSupplierInvoice() throws Exception {
        long orders = count("purchase_orders");
        long logs = count("ai_ocr_requests");

        recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentType").value("PURCHASE_INVOICE"))
                .andExpect(jsonPath("$.data.header.supplier.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.header.supplier.matchedBy").value("TAX_CODE"))
                .andExpect(jsonPath("$.data.header.supplier.code").value("NCC_01"))
                .andExpect(jsonPath("$.data.header.invoiceSeries.value").value("1C26TAA"))
                .andExpect(jsonPath("$.data.header.invoiceNo.value").value("0000131"))
                .andExpect(jsonPath("$.data.header.invoiceDate.value").value("2026-10-18"))
                .andExpect(jsonPath("$.data.header.description.value").doesNotExist())
                // Dòng 1: khớp mã, ĐVT cơ sở, thuế 10%.
                .andExpect(jsonPath("$.data.lines[0].product.code").value("TT_GLU_01"))
                .andExpect(jsonPath("$.data.lines[0].product.reviewRequired").value(false))
                .andExpect(jsonPath("$.data.lines[0].unit.code").value("HOP"))
                .andExpect(jsonPath("$.data.lines[0].taxRate.code").value("VAT10"))
                .andExpect(jsonPath("$.data.lines[0].quantity.value").value("10"))
                .andExpect(jsonPath("$.data.lines[0].amountCheck.status").value("OK"))
                // Dòng 2: khớp tên (chữ có dấu bị rơi) -> gán nhưng phải xác nhận; ĐVT quy đổi; thuế 8% không có;
                // thành tiền lệch.
                .andExpect(jsonPath("$.data.lines[1].product.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.lines[1].product.matchedBy").value("NAME"))
                .andExpect(jsonPath("$.data.lines[1].product.code").value("VT_ONG_01"))
                .andExpect(jsonPath("$.data.lines[1].product.reviewRequired").value(true))
                .andExpect(jsonPath("$.data.lines[1].unit.code").value("HOP"))
                .andExpect(jsonPath("$.data.lines[1].taxRate.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.lines[1].amountCheck.status").value("MISMATCH"))
                // Dòng 3: tên gần giống -> chỉ ứng viên, không gán. Dòng 4: không có trong danh mục.
                .andExpect(jsonPath("$.data.lines[2].product.status").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.data.lines[2].product.id").doesNotExist())
                .andExpect(jsonPath("$.data.lines[2].product.candidates[0].code").value("MAY_XN_01"))
                .andExpect(jsonPath("$.data.lines[3].product.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.totals.check.status").value("OK"));

        assertThat(count("purchase_orders")).isEqualTo(orders);
        assertThat(count("ai_ocr_requests")).isEqualTo(logs + 1);
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM ai_ocr_requests ORDER BY id DESC LIMIT 1");
        assertThat(log.get("status")).isEqualTo("SUCCEEDED");
        assertThat(log.get("page_count")).isEqualTo(1);
        assertThat((String) log.get("file_sha256")).hasSize(64);
        assertThat(log.get("file_size")).isEqualTo((long) PDF.length);

        AiServiceStub.Received received = stub.received().get(0);
        assertThat(received.apiKey()).isEqualTo(AiServiceStub.API_KEY);
        assertThat(received.requestId()).isNotBlank();
        assertThat(received.contentType()).startsWith("multipart/form-data");
        assertThat(received.body()).contains("PURCHASE_INVOICE").contains("%PDF-1.7 test");
    }

    @Test
    @DisplayName("Đơn đặt hàng gửi NCC: số đơn vào diễn giải, ngày đơn; loại tiền khác VND thì cảnh báo (D-24)")
    void recognizesPurchaseOrder() throws Exception {
        stub.reply(200, invoiceJson().replace("\"VND\"", "\"USD\""));
        recognize(buyer, "PURCHASE_ORDER", 1, file(PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.description.value").value("Theo đơn đặt hàng số 0000131"))
                .andExpect(jsonPath("$.data.header.documentDate.value").value("2026-10-18"))
                .andExpect(jsonPath("$.data.header.invoiceNo.value").doesNotExist())
                .andExpect(jsonPath("$.data.header.currencyCode.value").value("USD"))
                .andExpect(jsonPath("$.data.warnings[0]").value(containsString("USD")));
    }

    @Test
    @DisplayName("AI-2, BR-AI-04: phiếu đặt hàng của khách -> gợi ý form đơn bán, khách theo MST; không tạo đơn bán")
    void recognizesCustomerOrder() throws Exception {
        long orders = count("sales_orders");
        stub.reply(200, invoiceJson().replace("0300000001", "0100000002")
                .replace("\"header\":{", "\"header\":{\"deliveryAddress\":" + text("S 1 ph mu, Hà Ni") + ","));
        Session sales = login(mockMvc, "truongkd.hn");

        recognize(sales, "SALES_ORDER", 1, file(PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentType").value("SALES_ORDER"))
                .andExpect(jsonPath("$.data.header.customer.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.header.customer.code").value("KH_PK01"))
                .andExpect(jsonPath("$.data.header.description.value")
                        .value("Theo đơn đặt hàng của khách số 0000131"))
                .andExpect(jsonPath("$.data.header.documentDate.value").value("2026-10-18"))
                .andExpect(jsonPath("$.data.header.deliveryAddress.value").value("S 1 ph mu, Hà Ni"))
                .andExpect(jsonPath("$.data.header.deliveryAddress.status").value("LOW_CONFIDENCE"))
                .andExpect(jsonPath("$.data.header.supplier").doesNotExist())
                .andExpect(jsonPath("$.data.lines[0].product.code").value("TT_GLU_01"))
                .andExpect(jsonPath("$.data.lines[1].amountCheck.status").value("MISMATCH"))
                .andExpect(jsonPath("$.data.totals.check.status").value("OK"));
        assertThat(count("sales_orders")).isEqualTo(orders);
        assertThat(stub.received().get(0).body()).contains("SALES_ORDER");

        // MST của nhà cung cấp: không phải khách hàng nên không gán (đúng vai trò đối tác, docs/14 mục 4.6).
        stub.reply(200, invoiceJson());
        recognize(sales, "SALES_ORDER", 1, file(PDF)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.customer.status").value("NOT_FOUND"));

        // Không in MST: chỉ trả ứng viên theo tên, không tự gán (docs/14 mục 4.6).
        stub.reply(200, invoiceJson().replace("\"partnerTaxCode\":" + ok("0300000001") + ",", "")
                .replace(text("Nhà phân phi thit b mu"), text("Phòng khám đa khoa mu")));
        recognize(sales, "SALES_ORDER", 1, file(PDF)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.customer.status").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.data.header.customer.id").doesNotExist())
                .andExpect(jsonPath("$.data.header.customer.candidates[0].code").value("KH_PK01"));
    }

    @Test
    @DisplayName("AT-10: OCR cần quyền lập form đích theo loại chứng từ (đơn mua / đơn bán)")
    void requiresTargetFormPermission() throws Exception {
        recognize(buyer, "SALES_ORDER", 1, file(PDF)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        recognize(login(mockMvc, "truongkd.hn"), "PURCHASE_INVOICE", 1, file(PDF))
                .andExpect(status().isForbidden());
        assertThat(stub.received()).isEmpty();
    }

    @Test
    @DisplayName("AT-10: chưa đăng nhập 401, thiếu quyền 403, chi nhánh ngoài phạm vi 404")
    void requiresPermissionAndScope() throws Exception {
        mockMvc.perform(multipart(RECOGNITIONS).file(file(PDF)).param("companyId", "1").param("branchId", "1")
                .param("documentType", "PURCHASE_INVOICE")).andExpect(status().isUnauthorized());
        recognize(login(mockMvc, "thukho.hn"), "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isForbidden());
        recognize(buyer, "PURCHASE_INVOICE", 2, file(PDF)).andExpect(status().isNotFound());
        assertThat(stub.received()).isEmpty();
    }

    @Test
    @DisplayName("BR-SEC-04: loại file thật, dung lượng, số điểm ảnh kiểm tra trước khi gửi AI Service")
    void validatesFileBeforeCallingAi() throws Exception {
        recognize(buyer, "PURCHASE_INVOICE", 1, file("MZ\u0090 exe".getBytes()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("OCR_UNSUPPORTED_TYPE"));
        recognize(buyer, "PURCHASE_INVOICE", 1, file(new byte[1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("OCR_FILE_TOO_LARGE"));
        recognize(buyer, "PURCHASE_INVOICE", 1, file(pngHeader(12000, 9000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OCR_FILE_INVALID"));
        assertThat(stub.received()).isEmpty();
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM ai_ocr_requests ORDER BY id DESC LIMIT 1");
        assertThat(log.get("status")).isEqualTo("FAILED");
        assertThat(log.get("error_code")).isEqualTo("OCR_FILE_INVALID");
    }

    @Test
    @DisplayName("Lỗi AI Service đổi sang mã lỗi ERP: không có chữ 422, bận 429, lỗi khác 503, quá thời gian 504")
    void mapsAiServiceErrors() throws Exception {
        stub.reply(422, "{\"success\":false,\"code\":\"OCR_NO_TEXT\",\"message\":\"x\"}");
        recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OCR_NO_TEXT"));
        stub.reply(429, "{\"success\":false,\"code\":\"AI_BUSY\"}");
        recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_BUSY"));
        stub.reply(400, "{\"success\":false,\"code\":\"OCR_FILE_INVALID\",\"message\":\"PDF vượt quá 5 trang.\"}");
        recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PDF vượt quá 5 trang."));
        stub.reply(200, invoiceJson());
        stub.delay(Duration.ofSeconds(3));
        recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_TIMEOUT"));
        stub.reply(500, "{\"success\":false,\"code\":\"INTERNAL_ERROR\"}");
        recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("BR-AI-09: AI Service ngừng -> 503 ngay, không gọi AI; health báo không khả dụng")
    void unavailableWhenAiServiceDown() throws Exception {
        stub.ready(false);
        availability.refresh();
        mockMvc.perform(as(buyer, get("/api/v1/ai/health"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocrAvailable").value(false));
        recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"));
        assertThat(stub.received()).isEmpty();

        stub.ready(true);
        availability.refresh();
        mockMvc.perform(as(buyer, get("/api/v1/ai/health"))).andExpect(jsonPath("$.data.ocrAvailable").value(true));
    }

    @Test
    @DisplayName("Q-11 (D-59): license không hiệu lực thì chặn OCR như lệnh ghi khác")
    void blockedWhenLicenseInactive() throws Exception {
        Session admin = login(mockMvc, "admin");
        try {
            install(admin, TestLicenses.sign(unique("OTHER"), List.of("0000000000"),
                    LocalDate.now().plusDays(30), 15, Instant.now())).andExpect(status().isOk());
            recognize(buyer, "PURCHASE_INVOICE", 1, file(PDF)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("LICENSE_INACTIVE"));
        } finally {
            jdbc.update("DELETE FROM sys_license_clock");
            install(admin, TestLicenses.sign(unique("RESTORE"), List.of("*"), LocalDate.of(2099, 12, 31), 15,
                    Instant.now())).andExpect(status().isOk());
            licenseService.refresh();
        }
        assertThat(stub.received()).isEmpty();
    }

    private ResultActions recognize(Session session, String type, long branchId, MockMultipartFile file)
            throws Exception {
        return mockMvc.perform(as(session, multipart(RECOGNITIONS).file(file))
                .param("companyId", "1").param("branchId", String.valueOf(branchId))
                .param("documentType", type).param("documentDate", "2026-10-20"));
    }

    private ResultActions install(Session session, String license) throws Exception {
        return mockMvc.perform(as(session, put("/api/v1/license")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"license\":\"" + license + "\"}"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "chung-tu.pdf", "application/pdf", content);
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    /** PNG chỉ có IHDR khai báo kích thước: phải bị chặn mà không giải mã ảnh. */
    private static byte[] pngHeader(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(17).put("IHDR".getBytes()).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(ByteBuffer.allocate(4).putInt(13).array());
        out.writeBytes(ihdr.array());
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return out.toByteArray();
    }

    /** Kết quả AI Service theo hợp đồng docs/14 mục 6.3, dựa trên danh mục mẫu dev/test. */
    private static String invoiceJson() {
        return """
                {"documentType":"PURCHASE_INVOICE","pageCount":1,"durationMs":900,"warnings":[],
                 "header":{
                   "documentNo":%s,"documentDate":%s,"invoiceSeries":%s,"currency":%s,
                   "partnerTaxCode":%s,"partnerName":%s},
                 "lines":[
                   {"code":%s,"name":%s,"unit":%s,"quantity":%s,"unitPrice":%s,"taxRate":%s,"amount":%s},
                   {"name":%s,"unit":%s,"quantity":%s,"unitPrice":%s,"taxRate":%s,"amount":%s},
                   {"name":%s,"quantity":%s},
                   {"name":%s,"quantity":%s}],
                 "totals":{"goods":%s,"vat":%s,"total":%s}}
                """.formatted(ok("0000131"), ok("2026-10-18"), ok("1C26TAA"), ok("VND"), ok("0300000001"),
                text("Nhà phân phi thit b mu"),
                ok("TT_GLU_01"), text("Thuốc thử Glucose (mẫu)"), text("Hp"), ok("10"), ok("100000"), ok("10"),
                ok("1000000"),
                text("ng ly máu chân không (mu)"), text("Hp"), ok("2"), ok("50000"), ok("8"), ok("150000"),
                text("Máy xét nghim huyt hc"), ok("1"),
                text("Hàng không có trong danh mục"), ok("3"),
                ok("1150000"), ok("90000"), ok("1240000"));
    }

    private static String ok(String value) {
        return "{\"value\":\"" + value + "\",\"rawText\":\"" + value + "\",\"source\":\"OCR\",\"confidence\":0.98,"
                + "\"status\":\"OK\",\"page\":1,\"box\":[1,2,3,4],\"warnings\":[]}";
    }

    private static String text(String value) {
        return "{\"value\":\"" + value + "\",\"rawText\":\"" + value + "\",\"source\":\"OCR\",\"confidence\":0.97,"
                + "\"status\":\"LOW_CONFIDENCE\",\"page\":1,\"warnings\":[\"TEXT_DIACRITICS_UNRELIABLE\"]}";
    }
}
