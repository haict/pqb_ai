package com.company.erp.common.application;

import com.company.erp.common.domain.DocumentSequence;
import com.company.erp.common.infrastructure.persistence.DocumentSequenceRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp số chứng từ duy nhất theo (công ty, chi nhánh, loại, năm), an toàn khi nhiều người tạo cùng lúc
 * (BR-SYS-10). Định dạng {@code <LOẠI>-<CHI NHÁNH>-<YYYY>-<6 số>} (D-03).
 *
 * <p>Phải gọi trong transaction tạo/ghi sổ chứng từ: dòng bộ đếm bị khóa đến khi transaction kết thúc,
 * nếu transaction rollback thì số không bị tiêu hao.
 */
@Service
public class DocumentNumberService {

    private static final int SEQUENCE_DIGITS = 6;

    private final DocumentSequenceRepository repository;
    private final DocumentSequenceInitializer initializer;
    private final Clock clock;

    public DocumentNumberService(DocumentSequenceRepository repository,
                                 DocumentSequenceInitializer initializer,
                                 Clock clock) {
        this.repository = repository;
        this.initializer = initializer;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(DocumentNumberRequest request) {
        DocumentSequence sequence = lockSequence(request);
        long value = sequence.allocate(Instant.now(clock));
        return format(request, value);
    }

    private DocumentSequence lockSequence(DocumentNumberRequest request) {
        // Kiểm tra bằng đọc không khóa: SELECT ... FOR UPDATE trên dòng chưa có sẽ giữ gap lock
        // và chặn chính transaction con chèn dòng đó.
        boolean exists = repository.existsByCompanyIdAndBranchIdAndDocumentTypeAndPeriodKey(
                request.companyId(), request.branchId(), request.documentType(), request.periodKey());
        if (!exists) {
            initializer.ensureExists(request.companyId(), request.branchId(), request.documentType(),
                    request.periodKey());
        }
        return repository.findForUpdate(request.companyId(), request.branchId(), request.documentType(),
                        request.periodKey())
                .orElseThrow(() -> new IllegalStateException("Không tạo được bộ đếm số chứng từ"));
    }

    static String format(DocumentNumberRequest request, long value) {
        String sequencePart = String.format("%0" + SEQUENCE_DIGITS + "d", value);
        if (sequencePart.length() > SEQUENCE_DIGITS) {
            throw new IllegalStateException("Số chứng từ vượt " + SEQUENCE_DIGITS + " chữ số: "
                    + request.documentType() + "/" + request.branchCode() + "/" + request.year());
        }
        return request.documentType() + "-" + request.branchCode() + "-" + request.year() + "-" + sequencePart;
    }
}
