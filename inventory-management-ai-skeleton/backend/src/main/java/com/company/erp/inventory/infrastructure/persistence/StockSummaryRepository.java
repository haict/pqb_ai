package com.company.erp.inventory.infrastructure.persistence;

import com.company.erp.inventory.domain.StockSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockSummaryRepository
        extends JpaRepository<StockSummary, Long>, JpaSpecificationExecutor<StockSummary> {
}
