package com.company.erp.finance.application;

import com.company.erp.finance.application.DebtPostingService.InvoiceSide;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtItemStatus;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai đọc mức công nợ của một đối tượng cho module khác: kiểm tra hạn mức tín dụng khi gửi duyệt đơn bán
 * (BR-SAL-14, D-22). Tính từ sổ công nợ (khoản chưa đảo), toàn công ty.
 */
@Service
public class DebtExposureService {

    /** Số còn nợ và phần đã quá hạn. */
    public record Exposure(BigDecimal outstanding, BigDecimal overdue) {
    }

    private final DebtItemRepository itemRepository;

    public DebtExposureService(DebtItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public Exposure exposure(Long companyId, InvoiceSide side, Long partnerId, LocalDate asOf) {
        List<Object[]> rows = itemRepository.exposure(companyId, DebtType.valueOf(side.name()), partnerId, asOf);
        Object[] row = rows.isEmpty() ? new Object[] {BigDecimal.ZERO, BigDecimal.ZERO} : rows.get(0);
        return new Exposure(toDecimal(row[0]), toDecimal(row[1]));
    }

    /** Số đã thanh toán / đối trừ theo chứng từ nguồn (khoản chưa đảo), theo id chứng từ. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> settledBySource(Long companyId, String sourceType, Collection<Long> sourceIds) {
        Map<Long, BigDecimal> settled = new HashMap<>();
        if (sourceIds.isEmpty()) {
            return settled;
        }
        for (DebtItem item : itemRepository.findBySources(companyId, sourceType, sourceIds)) {
            if (item.getStatus() != DebtItemStatus.REVERSED) {
                settled.merge(item.getSourceId(), item.getSettledAmount(), BigDecimal::add);
            }
        }
        return settled;
    }

    private static BigDecimal toDecimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
