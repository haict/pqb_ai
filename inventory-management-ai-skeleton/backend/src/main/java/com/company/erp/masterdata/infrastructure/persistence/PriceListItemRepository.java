package com.company.erp.masterdata.infrastructure.persistence;

import com.company.erp.masterdata.domain.PriceListItem;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    Page<PriceListItem> findByPriceListId(Long priceListId, Pageable pageable);

    Optional<PriceListItem> findByPriceListIdAndProductIdAndUnitIdAndMinQuantity(Long priceListId, Long productId,
                                                                                Long unitId, BigDecimal minQuantity);
}
