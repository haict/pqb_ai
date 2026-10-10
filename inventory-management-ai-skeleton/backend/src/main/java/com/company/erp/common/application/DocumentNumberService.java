package com.company.erp.common.application;

import com.company.erp.common.domain.DocumentNumberFormat;
import com.company.erp.common.domain.DocumentNumberPattern;
import com.company.erp.common.domain.DocumentSequence;
import com.company.erp.common.infrastructure.persistence.DocumentNumberFormatRepository;
import com.company.erp.common.infrastructure.persistence.DocumentSequenceRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp số chứng từ duy nhất theo (công ty, chi nhánh, loại, kỳ), an toàn khi nhiều người tạo cùng lúc
 * (BR-SYS-10). Định dạng theo cấu hình có hiệu lực tại ngày chứng từ; chưa cấu hình thì theo D-03
 * {@code <LOẠI>-<CHI NHÁNH>-<YYYY>-<6 số>}.
 *
 * <p>Phải gọi trong transaction tạo/ghi sổ chứng từ: dòng bộ đếm bị khóa đến khi transaction kết thúc,
 * nếu transaction rollback thì số không bị tiêu hao.
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository repository;
    private final DocumentNumberFormatRepository formatRepository;
    private final DocumentSequenceInitializer initializer;
    private final Clock clock;

    public DocumentNumberService(DocumentSequenceRepository repository,
                                 DocumentNumberFormatRepository formatRepository,
                                 DocumentSequenceInitializer initializer,
                                 Clock clock) {
        this.repository = repository;
        this.formatRepository = formatRepository;
        this.initializer = initializer;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(DocumentNumberRequest request) {
        DocumentNumberPattern pattern = effectivePattern(request);
        DocumentSequence sequence = lockSequence(request, pattern.periodKey(request.documentDate()));
        long value = sequence.allocate(Instant.now(clock));
        return pattern.format(request.branchCode(), request.documentDate(), value);
    }

    private DocumentNumberPattern effectivePattern(DocumentNumberRequest request) {
        return formatRepository
                .findFirstByCompanyIdAndDocumentTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        request.companyId(), request.documentType(), request.documentDate())
                .map(DocumentNumberFormat::pattern)
                .orElseGet(() -> DocumentNumberPattern.defaultFor(request.documentType()));
    }

    private DocumentSequence lockSequence(DocumentNumberRequest request, String periodKey) {
        // Kiểm tra bằng đọc không khóa: SELECT ... FOR UPDATE trên dòng chưa có sẽ giữ gap lock
        // và chặn chính transaction con chèn dòng đó.
        boolean exists = repository.existsByCompanyIdAndBranchIdAndDocumentTypeAndPeriodKey(
                request.companyId(), request.branchId(), request.documentType(), periodKey);
        if (!exists) {
            initializer.ensureExists(request.companyId(), request.branchId(), request.documentType(), periodKey);
        }
        return repository.findForUpdate(request.companyId(), request.branchId(), request.documentType(), periodKey)
                .orElseThrow(() -> new IllegalStateException("Không tạo được bộ đếm số chứng từ"));
    }
}
