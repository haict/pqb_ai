package com.company.erp.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.support.MySqlIntegrationTest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@MySqlIntegrationTest
class IdempotencyServiceIntegrationTest {

    private static final String ENDPOINT = "POST /api/v1/test-documents";

    private final IdempotencyService service;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    IdempotencyServiceIntegrationTest(IdempotencyService service, TransactionTemplate transactionTemplate) {
        this.service = service;
        this.transactionTemplate = transactionTemplate;
    }

    record CreateRequest(String code, int quantity) {
    }

    record CreateResult(long id, String documentNo) {
    }

    @Test
    @DisplayName("AT-13: gửi lại cùng key trả kết quả lần đầu, không chạy lại nghiệp vụ")
    void replayReturnsFirstResultWithoutRerunning() {
        AtomicInteger executions = new AtomicInteger();
        IdempotentCommand command = command(1L, new CreateRequest("A", 5));

        CreateResult first = run(command, () -> new CreateResult(executions.incrementAndGet(), "PNK-HN-2026-000001"));
        CreateResult second = run(command, () -> new CreateResult(executions.incrementAndGet(), "PNK-HN-2026-000002"));

        assertThat(second).isEqualTo(first);
        assertThat(executions).hasValue(1);
    }

    @Test
    @DisplayName("Dùng lại key cho nội dung khác trả IDEMPOTENCY_KEY_REUSED")
    void reusingKeyWithDifferentPayloadIsRejected() {
        String key = UUID.randomUUID().toString();
        run(new IdempotentCommand(1L, key, ENDPOINT, new CreateRequest("A", 5)), () -> new CreateResult(1, "X"));

        assertThatThrownBy(() -> run(new IdempotentCommand(1L, key, ENDPOINT, new CreateRequest("A", 6)),
                () -> new CreateResult(2, "Y")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    @DisplayName("Cùng key của hai người dùng khác nhau là hai yêu cầu độc lập")
    void keysAreScopedPerUser() {
        String key = UUID.randomUUID().toString();
        CreateRequest payload = new CreateRequest("A", 5);

        CreateResult user1 = run(new IdempotentCommand(1L, key, ENDPOINT, payload), () -> new CreateResult(1, "X"));
        CreateResult user2 = run(new IdempotentCommand(2L, key, ENDPOINT, payload), () -> new CreateResult(2, "Y"));

        assertThat(user1).isNotEqualTo(user2);
    }

    @Test
    @DisplayName("Nghiệp vụ lỗi thì key cũng rollback, client được gửi lại")
    void failedBusinessReleasesKey() {
        IdempotentCommand command = command(1L, new CreateRequest("A", 5));

        assertThatThrownBy(() -> run(command, () -> {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        })).isInstanceOf(BusinessException.class);

        CreateResult retried = run(command, () -> new CreateResult(7, "PNK-HN-2026-000007"));
        assertThat(retried.id()).isEqualTo(7);
    }

    private IdempotentCommand command(Long userId, Object payload) {
        return new IdempotentCommand(userId, UUID.randomUUID().toString(), ENDPOINT, payload);
    }

    private CreateResult run(IdempotentCommand command, Supplier<CreateResult> action) {
        return transactionTemplate.execute(status -> service.execute(command, CreateResult.class, action));
    }
}
