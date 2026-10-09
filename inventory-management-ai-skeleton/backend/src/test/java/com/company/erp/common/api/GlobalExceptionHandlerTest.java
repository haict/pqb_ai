package com.company.erp.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.infrastructure.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    @DisplayName("Lỗi nghiệp vụ trả đúng HTTP, mã lỗi, thông báo và requestId")
    void businessExceptionIsMapped() throws Exception {
        mockMvc.perform(get("/test/business").header(RequestIdFilter.HEADER, "req-12345678"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(RequestIdFilter.HEADER, "req-12345678"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Tồn khả dụng của lô L2401 không đủ (còn 5, cần 8)."))
                .andExpect(jsonPath("$.requestId").value("req-12345678"));
    }

    @Test
    @DisplayName("Validation trả 400 kèm danh sách lỗi theo trường")
    void validationErrorsListFields() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", Matchers.containsInAnyOrder("code", "quantity")));
    }

    @Test
    @DisplayName("JSON sai định dạng trả 400 MALFORMED_REQUEST")
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Sửa đồng thời trả 409 CONCURRENT_MODIFICATION")
    void optimisticLockIsConflict() throws Exception {
        mockMvc.perform(get("/test/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("Hết thời gian chờ khóa trả 423 LOCK_TIMEOUT")
    void lockTimeoutIsLocked() throws Exception {
        mockMvc.perform(get("/test/lock-timeout"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
    }

    @Test
    @DisplayName("Lỗi không mong đợi trả 500 với thông báo chung, không lộ chi tiết")
    void unexpectedErrorHidesDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", Matchers.not(Matchers.containsString("SELECT"))))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    record SampleRequest(@NotBlank String code, @Positive BigDecimal quantity) {
    }

    @RestController
    static class SampleController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "Tồn khả dụng của lô L2401 không đủ (còn 5, cần 8).");
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody SampleRequest request) {
            // Chỉ kiểm tra validation.
        }

        @GetMapping("/test/optimistic")
        void optimistic() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
        }

        @GetMapping("/test/lock-timeout")
        void lockTimeout() {
            throw new CannotAcquireLockException("Lock wait timeout exceeded");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("SELECT * FROM secret_table failed");
        }
    }
}
