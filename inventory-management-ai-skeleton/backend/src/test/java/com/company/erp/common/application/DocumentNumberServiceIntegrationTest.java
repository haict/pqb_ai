package com.company.erp.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.erp.support.MySqlIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@MySqlIntegrationTest
class DocumentNumberServiceIntegrationTest {

    private final DocumentNumberService service;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    DocumentNumberServiceIntegrationTest(DocumentNumberService service, TransactionTemplate transactionTemplate) {
        this.service = service;
        this.transactionTemplate = transactionTemplate;
    }

    @Test
    @DisplayName("BR-SYS-10: cấp số tăng dần, đặt lại theo năm và tách theo chi nhánh")
    void allocatesSequentialNumbersPerScope() {
        assertThat(next(new DocumentNumberRequest(1L, 1L, "HN", "PNK", LocalDate.of(2026, 10, 15)))).isEqualTo("PNK-HN-2026-000001");
        assertThat(next(new DocumentNumberRequest(1L, 1L, "HN", "PNK", LocalDate.of(2026, 10, 15)))).isEqualTo("PNK-HN-2026-000002");
        assertThat(next(new DocumentNumberRequest(1L, 1L, "HN", "PNK", LocalDate.of(2027, 10, 15)))).isEqualTo("PNK-HN-2027-000001");
        assertThat(next(new DocumentNumberRequest(1L, 2L, "HCM", "PNK", LocalDate.of(2026, 10, 15)))).isEqualTo("PNK-HCM-2026-000001");
    }

    @Test
    @DisplayName("Transaction rollback không tiêu hao số")
    void rollbackDoesNotConsumeNumber() {
        DocumentNumberRequest request = new DocumentNumberRequest(1L, 1L, "HN", "RBK", LocalDate.of(2026, 10, 15));
        assertThat(next(request)).isEqualTo("RBK-HN-2026-000001");

        transactionTemplate.executeWithoutResult(status -> {
            service.nextNumber(request);
            status.setRollbackOnly();
        });

        assertThat(next(request)).isEqualTo("RBK-HN-2026-000002");
    }

    @Test
    @DisplayName("Bắt buộc gọi trong transaction nghiệp vụ")
    void requiresTransaction() {
        assertThatThrownBy(() -> service.nextNumber(new DocumentNumberRequest(1L, 1L, "HN", "PNK", LocalDate.of(2026, 10, 15))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("BR-GEN-52: nhiều người tạo đồng thời không trùng số, không nhảy số (kể cả lần cấp đầu tiên)")
    void concurrentAllocationIsUniqueAndGapless() throws Exception {
        int threads = 8;
        int perThread = 10;
        DocumentNumberRequest request = new DocumentNumberRequest(1L, 1L, "HN", "CONC", LocalDate.of(2026, 10, 15));
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        numbers.add(next(request));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        int total = threads * perThread;
        assertThat(numbers).hasSize(total);
        for (int i = 1; i <= total; i++) {
            assertThat(numbers).contains(String.format("CONC-HN-2026-%06d", i));
        }
    }

    private String next(DocumentNumberRequest request) {
        return transactionTemplate.execute(status -> service.nextNumber(request));
    }
}
