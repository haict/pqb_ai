package com.company.erp.inventory.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.dto.ReservationRequest;
import com.company.erp.inventory.domain.InventoryReservation;
import com.company.erp.inventory.domain.ReservationStatus;
import com.company.erp.inventory.infrastructure.persistence.InventoryReservationRepository;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc.LockedSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giữ chỗ tồn (BR mục 5.4): làm giảm tồn khả dụng, không đổi tồn thực tế. API công khai cho phân hệ bán hàng / hợp
 * đồng gọi trong transaction của chứng từ nguồn; kiểm tra khả dụng sau khi khóa tổng (hàng, kho). Mọi thao tác khóa
 * tổng (hàng, kho) theo thứ tự tăng dần trước khi đổi giữ chỗ (cùng thứ tự với ghi sổ kho).
 */
@Service
public class InventoryReservationService {

    /** Một dòng cần giữ chỗ của chứng từ nguồn. */
    public record ReservationLine(Long sourceLineId, Long productId, Long warehouseId, BigDecimal quantity) {
    }

    /** Phần giữ chỗ được dùng khi xuất kho: theo dòng chứng từ giữ chỗ. */
    public record ReservationUse(Long sourceLineId, Long productId, Long warehouseId, BigDecimal quantity) {
    }

    /** Giữ chỗ của chứng từ nguồn để hiển thị. */
    public record ReservationView(Long id, Long sourceLineId, Long productId, Long warehouseId, BigDecimal quantity,
                                  BigDecimal consumedQuantity, BigDecimal remainingQuantity, String status,
                                  Instant expiresAt) {
    }

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
        requireAvailable(request.productId(), request.warehouseId(), summary, request.quantity());
        ledger.addSummary(request.productId(), request.warehouseId(), BigDecimal.ZERO, request.quantity(), now);
        return reservationRepository.saveAndFlush(new InventoryReservation(request.companyId(), request.productId(),
                request.warehouseId(), request.sourceType(), request.sourceId(), request.sourceLineId(),
                request.quantity(), null)).getId();
    }

    /**
     * Giữ chỗ cho nhiều dòng của một chứng từ (BR-SAL-13, D-37): khóa tổng (hàng, kho) theo thứ tự, kiểm tra khả dụng
     * gộp theo (hàng, kho); thiếu thì 422 {@code INSUFFICIENT_STOCK} và không giữ dòng nào.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveAll(Long companyId, String sourceType, Long sourceId, List<ReservationLine> lines,
                           Instant expiresAt) {
        Instant now = Instant.now(clock);
        Map<Key, BigDecimal> totals = new TreeMap<>();
        lines.forEach(line -> totals.merge(new Key(line.productId(), line.warehouseId()), line.quantity(),
                BigDecimal::add));
        for (Map.Entry<Key, BigDecimal> entry : totals.entrySet()) {
            Key key = entry.getKey();
            requireAvailable(key.productId(), key.warehouseId(),
                    ledger.lockSummary(companyId, key.productId(), key.warehouseId(), now), entry.getValue());
            ledger.addSummary(key.productId(), key.warehouseId(), BigDecimal.ZERO, entry.getValue(), now);
        }
        reservationRepository.saveAllAndFlush(lines.stream().map(line -> new InventoryReservation(companyId,
                line.productId(), line.warehouseId(), sourceType, sourceId, line.sourceLineId(), line.quantity(),
                expiresAt)).toList());
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

    /** Nhả mọi giữ chỗ còn hiệu lực của chứng từ nguồn (hủy / đóng đơn, BR-SAL-13). Trả số dòng đã nhả. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int releaseAll(Long companyId, String sourceType, Long sourceId) {
        List<InventoryReservation> active = reservationRepository.findBySource(companyId, sourceType, sourceId)
                .stream().filter(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE).toList();
        if (active.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        lockKeys(companyId, active.stream().map(reservation -> new Key(reservation.getProductId(),
                reservation.getWarehouseId())).toList(), now);
        List<InventoryReservation> locked = reservationRepository.findAllForUpdate(active.stream()
                .map(InventoryReservation::getId).toList());
        for (InventoryReservation reservation : locked) {
            if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                ledger.addSummary(reservation.getProductId(), reservation.getWarehouseId(), BigDecimal.ZERO,
                        reservation.release().negate(), now);
            }
        }
        reservationRepository.saveAll(locked);
        return locked.size();
    }

    /**
     * Dùng giữ chỗ khi xuất kho theo chứng từ nguồn (gọi sau khi đã khóa tổng (hàng, kho) theo thứ tự): giảm tổng giữ
     * chỗ theo phần dùng được để kiểm tra tồn khả dụng không tính giữ chỗ của chính chứng từ đó.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(Long companyId, String sourceType, Long sourceId, List<ReservationUse> uses) {
        apply(companyId, sourceType, sourceId, uses, true);
    }

    /** Hoàn phần đã dùng khi đảo phiếu xuất (gọi sau khi đã khóa tổng theo thứ tự). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(Long companyId, String sourceType, Long sourceId, List<ReservationUse> uses) {
        apply(companyId, sourceType, sourceId, uses, false);
    }

    @Transactional(readOnly = true)
    public List<ReservationView> bySource(Long companyId, String sourceType, Long sourceId) {
        return reservationRepository.findBySource(companyId, sourceType, sourceId).stream()
                .map(reservation -> new ReservationView(reservation.getId(), reservation.getSourceLineId(),
                        reservation.getProductId(), reservation.getWarehouseId(), reservation.getQuantity(),
                        reservation.getConsumedQuantity(), reservation.getStatus() == ReservationStatus.ACTIVE
                        ? reservation.remaining() : BigDecimal.ZERO, reservation.getStatus().name(),
                        reservation.getExpiresAt()))
                .toList();
    }

    /** Nhả giữ chỗ quá hạn (D-37), khóa tổng (hàng, kho) rồi giữ chỗ từng dòng. Trả số giữ chỗ đã nhả. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int releaseExpired() {
        int count = 0;
        for (Long id : reservationRepository.findExpiredIds(ReservationStatus.ACTIVE, Instant.now(clock))) {
            InventoryReservation reservation = reservationRepository.findById(id).orElseThrow();
            Instant now = Instant.now(clock);
            ledger.lockSummary(reservation.getCompanyId(), reservation.getProductId(), reservation.getWarehouseId(),
                    now);
            InventoryReservation locked = reservationRepository.findByIdForUpdate(id).orElseThrow();
            if (locked.getStatus() == ReservationStatus.ACTIVE) {
                ledger.addSummary(locked.getProductId(), locked.getWarehouseId(), BigDecimal.ZERO,
                        locked.release().negate(), now);
                count++;
            }
        }
        return count;
    }

    private void apply(Long companyId, String sourceType, Long sourceId, List<ReservationUse> uses,
                       boolean consume) {
        if (uses.isEmpty()) {
            return;
        }
        List<InventoryReservation> reservations = reservationRepository.findAllForUpdate(reservationRepository
                .findBySource(companyId, sourceType, sourceId).stream().map(InventoryReservation::getId).toList());
        Instant now = Instant.now(clock);
        for (ReservationUse use : uses) {
            BigDecimal left = use.quantity();
            for (InventoryReservation reservation : reservations) {
                if (left.signum() <= 0) {
                    break;
                }
                if (!reservation.getProductId().equals(use.productId())
                        || !reservation.getWarehouseId().equals(use.warehouseId())
                        || (use.sourceLineId() != null && !use.sourceLineId().equals(reservation.getSourceLineId()))) {
                    continue;
                }
                BigDecimal before = reservation.getConsumedQuantity();
                BigDecimal reservedDelta = consume ? reservation.consume(left).negate() : reservation.restore(left);
                if (reservedDelta.signum() != 0) {
                    ledger.addSummary(use.productId(), use.warehouseId(), BigDecimal.ZERO, reservedDelta, now);
                }
                left = left.subtract(reservation.getConsumedQuantity().subtract(before).abs());
            }
        }
        reservationRepository.saveAll(reservations);
    }

    private void lockKeys(Long companyId, List<Key> keys, Instant now) {
        keys.stream().distinct().sorted(Comparator.naturalOrder())
                .forEach(key -> ledger.lockSummary(companyId, key.productId(), key.warehouseId(), now));
    }

    private void requireAvailable(Long productId, Long warehouseId, LockedSummary summary, BigDecimal quantity) {
        BigDecimal available = ledger.sellableQuantity(productId, warehouseId, calendar.today())
                .subtract(summary.reserved());
        if (quantity.compareTo(available) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Tồn khả dụng của hàng #" + productId
                    + " không đủ để giữ chỗ (khả dụng " + available.max(BigDecimal.ZERO).stripTrailingZeros()
                    .toPlainString() + ", cần " + quantity.stripTrailingZeros().toPlainString() + ").");
        }
    }

    private record Key(Long productId, Long warehouseId) implements Comparable<Key> {

        @Override
        public int compareTo(Key other) {
            int byProduct = productId.compareTo(other.productId);
            return byProduct != 0 ? byProduct : warehouseId.compareTo(other.warehouseId);
        }
    }
}
