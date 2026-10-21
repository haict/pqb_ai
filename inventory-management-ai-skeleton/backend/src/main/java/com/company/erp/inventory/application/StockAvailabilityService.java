package com.company.erp.inventory.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.inventory.domain.StockSummary;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import com.company.erp.inventory.infrastructure.persistence.StockSummaryRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai đọc tồn theo (hàng, kho) cho module khác (kiểm tra tồn khả dụng khi lập / ghi sổ đơn bán, BR-SAL-12):
 * tồn thực tế, bán được (ĐẠT, còn hạn, không khóa), giữ chỗ, khả dụng = bán được − giữ chỗ, hàng đang về. Chỉ đọc,
 * không khóa; kiểm tra chặn thực hiện lại khi ghi sổ xuất kho.
 */
@Service
public class StockAvailabilityService {

    public record Availability(Long productId, Long warehouseId, BigDecimal onHand, BigDecimal sellable,
                               BigDecimal reserved, BigDecimal available, BigDecimal incoming) {
    }

    private final StockSummaryRepository summaryRepository;
    private final StockLedgerJdbc ledger;
    private final BusinessCalendar calendar;

    public StockAvailabilityService(StockSummaryRepository summaryRepository, StockLedgerJdbc ledger,
                                    BusinessCalendar calendar) {
        this.summaryRepository = summaryRepository;
        this.ledger = ledger;
        this.calendar = calendar;
    }

    /** Tồn của các hàng tại một kho; hàng chưa có dòng tổng trả 0. */
    @Transactional(readOnly = true)
    public Map<Long, Availability> availability(Long warehouseId, Collection<Long> productIds) {
        Map<Long, StockSummary> summaries = new HashMap<>();
        if (!productIds.isEmpty()) {
            Specification<StockSummary> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("warehouseId"), warehouseId), root.get("productId").in(productIds));
            summaryRepository.findAll(spec).forEach(summary -> summaries.put(summary.getProductId(), summary));
        }
        Map<Long, Availability> result = new HashMap<>();
        for (Long productId : productIds) {
            StockSummary summary = summaries.get(productId);
            BigDecimal sellable = ledger.sellableQuantity(productId, warehouseId, calendar.today());
            BigDecimal reserved = summary == null ? BigDecimal.ZERO : summary.getReservedQuantity();
            result.put(productId, new Availability(productId, warehouseId,
                    summary == null ? BigDecimal.ZERO : summary.getOnHandQuantity(), sellable, reserved,
                    sellable.subtract(reserved).max(BigDecimal.ZERO),
                    summary == null ? BigDecimal.ZERO : summary.getIncomingQuantity()));
        }
        return result;
    }
}
