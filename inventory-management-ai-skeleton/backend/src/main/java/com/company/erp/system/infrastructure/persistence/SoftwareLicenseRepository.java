package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.SoftwareLicense;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, Long> {

    /** License đang dùng: dòng nạp mới nhất. */
    Optional<SoftwareLicense> findTopByOrderByIdDesc();
}
