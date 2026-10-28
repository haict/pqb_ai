package com.company.erp.ai.application;

import com.company.erp.ai.domain.OcrDocumentType;
import com.company.erp.ai.domain.OcrRequestLog;
import com.company.erp.ai.infrastructure.OcrRequestLogRepository;
import com.company.erp.common.application.RequestContext;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghi nhật ký dùng OCR (chỉ metadata) trong transaction ngắn riêng, sau khi đã có kết quả từ AI Service. */
@Service
public class OcrRequestLogService {

    /** Thông tin một lần nhận dạng; {@code errorCode} null nghĩa là thành công. */
    public record Entry(Long companyId, Long branchId, Long userId, OcrDocumentType documentType, String fileSha256,
                        long fileSize, Integer pageCount, String errorCode, int durationMs) {
    }

    private final OcrRequestLogRepository repository;
    private final Clock clock;

    public OcrRequestLogService(OcrRequestLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(Entry entry) {
        repository.save(new OcrRequestLog(entry.companyId(), entry.branchId(), entry.userId(), entry.documentType(),
                entry.fileSha256(), entry.fileSize(), entry.pageCount(), entry.errorCode(), entry.durationMs(),
                RequestContext.requestId(), Instant.now(clock)));
    }
}
