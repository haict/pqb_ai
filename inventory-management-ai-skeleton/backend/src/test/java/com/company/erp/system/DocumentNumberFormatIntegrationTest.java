package com.company.erp.system;

import static com.company.erp.support.ApiTestSupport.json;
import static com.company.erp.support.ApiTestSupport.login;
import static com.company.erp.support.ApiTestSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.DocumentNumberRequest;
import com.company.erp.common.application.DocumentNumberService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.DocumentNumberPattern;
import com.company.erp.common.domain.NumberResetCycle;
import com.company.erp.support.ApiTestSupport.Session;
import com.company.erp.support.MySqlIntegrationTest;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/** BR-SYS-10, D-03: cấu hình số chứng từ; thay đổi khi đã cấp số chỉ hiệu lực từ chu kỳ sau. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class DocumentNumberFormatIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final DocumentNumberService numberService;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    DocumentNumberFormatIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, DocumentNumberService numberService,
                                        BusinessCalendar calendar, TransactionTemplate transactionTemplate) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.numberService = numberService;
        this.calendar = calendar;
        this.transactionTemplate = transactionTemplate;
    }

    @Test
    @DisplayName("Loại chứng từ chưa cấp số: cấu hình hiệu lực ngay và được dùng khi cấp số")
    void unusedTypeAppliesImmediately() throws Exception {
        Session admin = login(mockMvc, "admin");
        LocalDate today = calendar.today();

        schedule(admin, "TCFGA", "XK", 4, NumberResetCycle.MONTHLY).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CURRENT"))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2000-01-01"));

        assertThat(next("TCFGA", today)).isEqualTo(
                String.format("XK-HN-%04d%02d-0001", today.getYear(), today.getMonthValue()));
    }

    @Test
    @DisplayName("Loại chứng từ đã cấp số: thay đổi hiệu lực từ chu kỳ sau, chu kỳ hiện tại giữ định dạng cũ")
    void usedTypeAppliesFromNextCycle() throws Exception {
        Session admin = login(mockMvc, "admin");
        LocalDate today = calendar.today();
        String year = String.format("%04d", today.getYear());
        assertThat(next("TCFGB", today)).isEqualTo("TCFGB-HN-" + year + "-000001");

        LocalDate effectiveFrom = DocumentNumberPattern.nextCycleStart(NumberResetCycle.YEARLY,
                NumberResetCycle.MONTHLY, today);
        MvcResult scheduled = schedule(admin, "TCFGB", "NB", 5, NumberResetCycle.MONTHLY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.effectiveFrom").value(effectiveFrom.toString()))
                .andReturn();
        schedule(admin, "TCFGB", "NC", 5, NumberResetCycle.MONTHLY).andExpect(status().isOk());

        assertThat(next("TCFGB", today)).isEqualTo("TCFGB-HN-" + year + "-000002");
        assertThat(next("TCFGB", effectiveFrom)).isEqualTo(String.format("NC-HN-%04d%02d-00001",
                effectiveFrom.getYear(), effectiveFrom.getMonthValue()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_number_formats WHERE document_type = 'TCFGB'",
                Integer.class)).isEqualTo(1);

        MvcResult list = mockMvc.perform(as(admin, get("/api/v1/document-number-formats"))
                        .param("companyId", "1").param("documentType", "TCFGB"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> prefixes = json(list, "$.data.items[*].prefix");
        assertThat(prefixes).containsExactly("NC");

        Integer id = json(scheduled, "$.data.id");
        mockMvc.perform(as(admin, delete("/api/v1/document-number-formats/" + id))).andExpect(status().isOk());
        assertThat(next("TCFGB", effectiveFrom)).startsWith("TCFGB-HN-" + effectiveFrom.getYear() + "-");
    }

    @Test
    @DisplayName("Cấu hình đã có hiệu lực không xóa được (409)")
    void cannotDeleteEffectiveFormat() throws Exception {
        Session admin = login(mockMvc, "admin");
        MvcResult created = schedule(admin, "TCFGC", "XC", 6, NumberResetCycle.YEARLY)
                .andExpect(status().isOk())
                .andReturn();
        Integer id = json(created, "$.data.id");

        mockMvc.perform(as(admin, delete("/api/v1/document-number-formats/" + id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_IN_EFFECT"));
    }

    @Test
    @DisplayName("Validation tiền tố, độ dài; AT-10: chỉ xem thì không sửa được, không có quyền thì không xem")
    void validationAndPermissions() throws Exception {
        Session admin = login(mockMvc, "admin");
        schedule(admin, "TCFGD", "X-1", 6, NumberResetCycle.YEARLY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("prefix"));
        schedule(admin, "TCFGD", "XD", 11, NumberResetCycle.YEARLY).andExpect(status().isBadRequest());

        Session director = login(mockMvc, "giamdoc");
        schedule(director, "TCFGD", "XD", 6, NumberResetCycle.YEARLY).andExpect(status().isForbidden());
        Session sales = login(mockMvc, "kinhdoanh.hn");
        mockMvc.perform(as(sales, get("/api/v1/document-number-formats")).param("companyId", "1"))
                .andExpect(status().isForbidden());
    }

    private String next(String documentType, LocalDate date) {
        return transactionTemplate.execute(status -> numberService.nextNumber(
                new DocumentNumberRequest(1L, 1L, "HN", documentType, date)));
    }

    private ResultActions schedule(Session session, String documentType, String prefix, int length,
                                   NumberResetCycle cycle) throws Exception {
        return mockMvc.perform(as(session, post("/api/v1/document-number-formats"))
                .header(IdempotentCommand.HEADER, newKey()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyId\":1,\"documentType\":\"" + documentType + "\",\"prefix\":\"" + prefix
                        + "\",\"sequenceLength\":" + length + ",\"resetCycle\":\"" + cycle + "\"}"));
    }

    private static MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
