package com.company.erp.system.infrastructure.persistence;

import com.company.erp.system.domain.LicenseClock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseClockRepository extends JpaRepository<LicenseClock, Byte> {
}
