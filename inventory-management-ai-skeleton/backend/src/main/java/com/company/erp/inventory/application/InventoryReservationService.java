package com.company.erp.inventory.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.dto.ReservationRequest;
import com.company.erp.inventory.domain.InventoryReservation;
import com.company.erp.inventory.infrastructure.persistence.InventoryReservationRepository;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc.LockedSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giữ chỗ tồn (BR mục 5.4): làm giảm tồn khả dụng, không đổi tồn thực tế. API công khai cho phân hệ bán hàng / hợp
 * đồng (Giai đoạn sau) gọi trong transaction của chứng từ nguồn; kiểm tra khả dụng sau khi khóa tổng (hàng, kho).
 */
@Service
public class InventoryReservationService {

    private final InventoryReservationRepository reservationRepository;
    private final StockLedgerJdbc ledger;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public InventoryReservationService(InventoryReservationRepository reservationRepository, StockLedgerJdbc ledger,
                                       BusinessCalendar calendar, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.ledger = ledger;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long reserve(ReservationRequest request) {
        Instant now = Instant.now(clock);
        LockedSummary summary = ledger.lockSummary(request.companyId(), request.productId(), request.warehouseId(),
                now);
        BigDecimal available = ledger.sellableQuantity(request.productId(), request.warehouseId(), calendar.today())
                .subtract(summary.reserved());
        if (request.quantity().compareTo(available) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Tồn khả dụng không đủ để giữ chỗ (khả dụng "
                    + available.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + ", cần "
                    + request.quantity().stripTrailingZeros().toPlainString() + ").");
        }
        ledger.addSummary(request.productId(), request.warehouseId(), BigDecimal.ZERO, request.quantity(), now);
        return reservationRepository.saveAndFlush(new InventoryReservation(request.companyId(), request.productId(),
                request.warehouseId(), request.sourceType(), request.sourceId(), request.sourceLineId(),
                request.quantity(), null)).getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long reservationId) {
        InventoryReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(BusinessException::notFound);
        Instant now = Instant.now(clock);
        ledger.lockSummary(reservation.getCompanyId(), reservation.getProductId(), reservation.getWarehouseId(), now);
        BigDecimal released = reservation.release();
        ledger.addSummary(reservation.getProductId(), reservation.getWarehouseId(), BigDecimal.ZERO,
                released.negate(), now);
    }
}
