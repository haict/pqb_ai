package com.company.erp.common.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.common.domain.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotentCommandTest {

    @Test
    @DisplayName("Chấp nhận key dạng UUID")
    void acceptsUuidKey() {
        assertThatCode(() -> new IdempotentCommand(1L, "3f2b8c4e-1d2a-4b7f-9e1c-0a1b2c3d4e5f", "POST /x", "{}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Từ chối key thiếu hoặc sai định dạng với lỗi VALIDATION_ERROR")
    void rejectsMissingOrInvalidKey() {
        assertThatThrownBy(() -> new IdempotentCommand(1L, null, "POST /x", "{}"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new IdempotentCommand(1L, "bad key!", "POST /x", "{}"))
                .isInstanceOf(BusinessException.class);
    }
}
