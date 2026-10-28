package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.Partner;
import com.company.erp.masterdata.domain.PartnerRole;
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

    /** Đối tác theo trạng thái có vai trò {@code role}, nạp sẵn vai trò (tránh N+1). */
    @EntityGraph(attributePaths = {"roles"})
    @Query("select distinct p from Partner p join p.roles r "
            + "where p.companyId = :companyId and p.status = :status and r = :role")
    List<Partner> findWithRole(@Param("companyId") Long companyId, @Param("status") RecordStatus status,
                               @Param("role") PartnerRole role);
}
