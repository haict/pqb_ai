package com.company.erp.common.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.IdempotencyRecord;
import com.company.erp.common.infrastructure.persistence.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chống xử lý trùng cho lệnh tạo và lệnh hành động (BR-GEN-50, .claude/rules/api.md mục 5).
 *
 * <ul>
 *   <li>Cùng người dùng + cùng key + cùng nội dung: trả lại kết quả lần đầu, không chạy lại nghiệp vụ.</li>
 *   <li>Cùng key nhưng khác nội dung hoặc endpoint: {@link ErrorCode#IDEMPOTENCY_KEY_REUSED}.</li>
 *   <li>Hai yêu cầu cùng key chạy đồng thời: unique key chặn bản thứ hai ({@link ErrorCode#REQUEST_IN_PROGRESS}).</li>
 * </ul>
 * Phải gọi trong transaction của nghiệp vụ để key và dữ liệu nghiệp vụ cùng commit hoặc cùng rollback.
 */
@Service
public class IdempotencyService {

    private static final int SUCCESS_STATUS = 200;

    private final IdempotencyRecordRepository repository;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              IdempotencyProperties properties,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(IdempotentCommand command, Class<T> resultType, Supplier<T> action) {
        Instant now = Instant.now(clock);
        String requestHash = hash(command.requestPayload());

        Optional<T> previous = findPreviousResult(command, requestHash, resultType, now);
        if (previous.isPresent()) {
            return previous.get();
        }

        IdempotencyRecord record = register(command, requestHash, now);
        T result = action.get();
        record.complete(SUCCESS_STATUS, toJson(result));
        return result;
    }

    private <T> Optional<T> findPreviousResult(IdempotentCommand command, String requestHash,
                                               Class<T> resultType, Instant now) {
        Optional<IdempotencyRecord> existing =
                repository.findByUserIdAndIdempotencyKey(command.userId(), command.key());
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord record = existing.get();
        if (record.isExpired(now)) {
            repository.delete(record);
            repository.flush();
            return Optional.empty();
        }
        if (!record.matches(command.endpoint(), requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return Optional.of(fromJson(record.responseBody(), resultType));
    }

    private IdempotencyRecord register(IdempotentCommand command, String requestHash, Instant now) {
        IdempotencyRecord record = new IdempotencyRecord(command.userId(), command.key(), requestHash,
                command.endpoint(), now, now.plus(properties.ttl()));
        try {
            return repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);
        }
    }

    private String hash(Object payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(toJson(payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không chuyển được dữ liệu sang JSON", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không đọc được kết quả đã lưu của Idempotency-Key", ex);
        }
    }
}
