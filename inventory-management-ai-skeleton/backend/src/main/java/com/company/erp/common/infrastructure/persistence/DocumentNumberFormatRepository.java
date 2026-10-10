package com.company.erp.common.infrastructure.persistence;

import com.company.erp.common.domain.DocumentNumberFormat;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentNumberFormatRepository
        extends JpaRepository<DocumentNumberFormat, Long>, JpaSpecificationExecutor<DocumentNumberFormat> {

    /** Cấu hình có hiệu lực tại ngày chứng từ. */
    Optional<DocumentNumberFormat> findFirstByCompanyIdAndDocumentTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long companyId, String documentType, LocalDate date);

    Optional<DocumentNumberFormat> findByCompanyIdAndDocumentTypeAndEffectiveFrom(Long companyId, String documentType,
                                                                                LocalDate effectiveFrom);
}
