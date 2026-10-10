package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.Partner;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRepository extends CodeNameRepository<Partner> {

    /** Nạp đối tác kèm vai trò, liên hệ, địa chỉ (một lần truy vấn cho trang chi tiết). */
    @EntityGraph(attributePaths = {"roles"})
    @Query("select p from Partner p where p.id = :id")
    Optional<Partner> findDetailedById(@Param("id") Long id);

    List<Partner> findByCompanyIdAndCodeIn(Long companyId, Collection<String> codes);
}
