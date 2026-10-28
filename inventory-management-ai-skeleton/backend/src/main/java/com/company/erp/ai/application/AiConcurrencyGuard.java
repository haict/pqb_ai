package com.company.erp.ai.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Giới hạn số yêu cầu OCR / trợ lý chạy đồng thời ({@code ai.ocr.max-concurrent}, {@code ai.chat.max-concurrent}, docs/14
 * mục 9.2): chờ suất tối đa {@code acquire-timeout} rồi trả {@code AI_BUSY}, để luồng Tomcat không bị giữ lâu chờ AI.
 */
@Component
public class AiConcurrencyGuard {

    private final Semaphore ocrPermits;
    private final long ocrAcquireMillis;
    private final Semaphore chatPermits;
    private final long chatAcquireMillis;

    public AiConcurrencyGuard(AiProperties properties) {
        this.ocrPermits = new Semaphore(Math.max(1, properties.ocr().maxConcurrent()), true);
        this.ocrAcquireMillis = properties.ocr().acquireTimeout().toMillis();
        this.chatPermits = new Semaphore(Math.max(1, properties.chat().maxConcurrent()), true);
        this.chatAcquireMillis = properties.chat().acquireTimeout().toMillis();
    }

    public <T> T runOcr(Supplier<T> task) {
        return run(ocrPermits, ocrAcquireMillis, task);
    }

    public <T> T runChat(Supplier<T> task) {
        return run(chatPermits, chatAcquireMillis, task);
    }

    private static <T> T run(Semaphore permits, long acquireMillis, Supplier<T> task) {
        try {
            if (!permits.tryAcquire(acquireMillis, TimeUnit.MILLISECONDS)) {
                throw new BusinessException(ErrorCode.AI_BUSY);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_BUSY);
        }
        try {
            return task.get();
        } finally {
            permits.release();
        }
    }
}
