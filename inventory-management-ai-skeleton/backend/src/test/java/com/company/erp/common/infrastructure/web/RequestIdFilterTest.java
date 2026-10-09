package com.company.erp.common.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestIdFilterTest {

    @Test
    @DisplayName("Giữ request ID hợp lệ do client gửi")
    void keepsSafeRequestId() {
        assertThat(RequestIdFilter.resolveRequestId("abc-12345678")).isEqualTo("abc-12345678");
    }

    @Test
    @DisplayName("Sinh request ID mới khi thiếu hoặc chứa ký tự không an toàn (chống log injection)")
    void replacesMissingOrUnsafeRequestId() {
        assertThat(RequestIdFilter.resolveRequestId(null)).hasSize(36);
        assertThat(RequestIdFilter.resolveRequestId("bad\nINFO fake log line")).doesNotContain("\n").hasSize(36);
        assertThat(RequestIdFilter.resolveRequestId("short")).hasSize(36);
    }
}
