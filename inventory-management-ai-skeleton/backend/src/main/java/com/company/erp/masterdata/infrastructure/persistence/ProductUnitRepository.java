package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.ProductUnit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, Long> {

    List<ProductUnit> findByProductIdOrderByUnitIdAscEffectiveFromDesc(Long productId);

    boolean existsByProductIdAndUnitIdAndEffectiveFrom(Long productId, Long unitId, LocalDate effectiveFrom);

    /** Hệ số đang hiệu lực tại ngày: dòng hoạt động có ngày hiệu lực gần nhất không sau ngày đó. */
    Optional<ProductUnit> findFirstByProductIdAndUnitIdAndEffectiveFromLessThanEqualAndStatusOrderByEffectiveFromDesc(
            Long productId, Long unitId, LocalDate date, RecordStatus status);
}
