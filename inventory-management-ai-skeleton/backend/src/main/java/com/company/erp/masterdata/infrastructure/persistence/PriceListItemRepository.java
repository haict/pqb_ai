package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.PriceListItem;
import com.company.erp.masterdata.domain.PriceTargetType;
import com.company.erp.masterdata.domain.PriceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    Page<PriceListItem> findByPriceListId(Long priceListId, Pageable pageable);

    Optional<PriceListItem> findByPriceListIdAndProductIdAndUnitIdAndMinQuantity(Long priceListId, Long productId,
                                                                                Long unitId, BigDecimal minQuantity);

    /** Dòng giá có thể áp dụng: bảng giá đang hoạt động, còn hiệu lực, bậc số lượng không vượt số lượng. */
    interface PriceCandidate {
        Long getPriceListId();

        String getPriceListCode();

        PriceType getPriceType();

        PriceTargetType getTargetType();

        Long getPartnerId();

        Long getPartnerGroupId();

        LocalDate getValidFrom();

        BigDecimal getMinQuantity();

        BigDecimal getUnitPrice();
    }

    @Query("""
            select l.id as priceListId, l.code as priceListCode, l.priceType as priceType,
                   l.targetType as targetType, l.partnerId as partnerId, l.partnerGroupId as partnerGroupId,
                   l.validFrom as validFrom, i.minQuantity as minQuantity, i.unitPrice as unitPrice
            from PriceListItem i, PriceList l
            where i.priceListId = l.id and l.companyId = :companyId and l.status = :active
              and i.productId = :productId and i.unitId = :unitId and i.minQuantity <= :quantity
              and l.validFrom <= :date and (l.validTo is null or l.validTo >= :date)
            """)
    List<PriceCandidate> findCandidates(@Param("companyId") Long companyId, @Param("productId") Long productId,
                                        @Param("unitId") Long unitId, @Param("quantity") BigDecimal quantity,
                                        @Param("date") LocalDate date, @Param("active") RecordStatus active);
}
