package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.PostingCommand.ProductWarehouse;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.domain.SerialStatus;
import com.company.erp.inventory.domain.StockIssuePolicy;
import com.company.erp.inventory.domain.StockKey;
import com.company.erp.inventory.domain.TransactionType;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.inventory.infrastructure.persistence.StockIssuePolicyRepository;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc.LockedBalance;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc.LockedSummary;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.PeriodGuard;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cửa duy nhất làm thay đổi tồn kho (CLAUDE.md mục 5, business-integrity.md mục 4). Trình tự ghi sổ (CLAUDE.md mục 9):
 * <ol>
 *   <li>Kỳ kho của mọi chi nhánh liên quan chưa khóa (BR-GEN-10); kho không đang kiểm kê (D-11).</li>
 *   <li>Khóa tổng (hàng, kho) theo thứ tự tăng dần, rồi khóa các dòng tồn chi tiết được chọn (FEFO).</li>
 *   <li>Kiểm tra tồn thực tế không âm, lô / serial bán được, tồn khả dụng tại thời điểm ghi sổ (BR-INV-20); xuất vượt
 *       khả dụng chỉ khi có chính sách đang hiệu lực (BR-GEN-31).</li>
 *   <li>Ghi sổ nhật ký (chỉ INSERT), cập nhật tồn chi tiết, tổng (hàng, kho), vòng đời serial.</li>
 * </ol>
 * Phải gọi trong transaction READ COMMITTED của chứng từ: lỗi ở bất kỳ bước nào làm rollback toàn bộ.
 */
@Service
public class InventoryPostingService {

    private static final Set<QualityStatus> DISPOSABLE_STATUSES = EnumSet.of(QualityStatus.QUARANTINE,
            QualityStatus.FAILED, QualityStatus.EXPIRED, QualityStatus.RECALLED);

    /** Phần tồn chi tiết được chọn cho một biến động xuất. */
    private record Allocation(StockMovement movement, LockedBalance balance, BigDecimal quantity) {
    }

    private final StockLedgerJdbc ledger;
    private final InventoryTransactionRepository transactionRepository;
    private final InventorySerialRepository serialRepository;
    private final StockIssuePolicyRepository policyRepository;
    private final MasterDataQueryService masterData;
    private final PeriodGuard periodGuard;
    private final Clock clock;

    public InventoryPostingService(StockLedgerJdbc ledger, InventoryTransactionRepository transactionRepository,
                                   InventorySerialRepository serialRepository,
                                   StockIssuePolicyRepository policyRepository, MasterDataQueryService masterData,
                                   PeriodGuard periodGuard, Clock clock) {
        this.ledger = ledger;
        this.transactionRepository = transactionRepository;
        this.serialRepository = serialRepository;
        this.policyRepository = policyRepository;
        this.masterData = masterData;
        this.periodGuard = periodGuard;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PostedMovement> post(PostingCommand command) {
        if (command.movements().isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        Map<Long, WarehouseInfo> warehouses = warehouses(command);
        requireOpenPeriods(command, warehouses);
        requireNotCounting(command, warehouses, now);
        Map<ProductWarehouse, LockedSummary> summaries = lockSummaries(command, now);

        List<Allocation> outs = new ArrayList<>();
        for (StockMovement movement : command.movements()) {
            if (movement.direction() == MovementDirection.OUT) {
                outs.addAll(allocate(movement, command));
            }
        }
        checkAvailability(outs, summaries, command);
        lockSerials(command, outs);

        List<PostedMovement> posted = new ArrayList<>();
        Map<ProductWarehouse, BigDecimal> deltas = new TreeMap<>();
        outs.sort(Comparator.comparing(allocation -> allocation.balance().key()));
        for (Allocation allocation : outs) {
            posted.add(applyOut(allocation, command, warehouses, now));
            deltas.merge(pw(allocation.balance().key()), allocation.quantity().negate(), BigDecimal::add);
        }
        List<StockMovement> ins = command.movements().stream()
                .filter(movement -> movement.direction() == MovementDirection.IN)
                .sorted(Comparator.comparing(InventoryPostingService::inKey))
                .toList();
        for (StockMovement movement : ins) {
            posted.add(applyIn(movement, command, warehouses, now));
            deltas.merge(new ProductWarehouse(movement.productId(), movement.warehouseId()), movement.quantity(),
                    BigDecimal::add);
        }
        deltas.forEach((key, delta) -> ledger.addSummary(key.productId(), key.warehouseId(), delta, BigDecimal.ZERO,
                now));
        return posted;
    }

    private Map<Long, WarehouseInfo> warehouses(PostingCommand command) {
        Set<Long> ids = command.movements().stream().map(StockMovement::warehouseId).collect(Collectors.toSet());
        command.extraLockKeys().forEach(key -> ids.add(key.warehouseId()));
        Map<Long, WarehouseInfo> warehouses = masterData.warehouses(ids).stream()
                .collect(Collectors.toMap(WarehouseInfo::id, Function.identity()));
        for (Long id : ids) {
            WarehouseInfo warehouse = warehouses.get(id);
            if (warehouse == null || !warehouse.companyId().equals(command.companyId())) {
                throw BusinessException.invalidField("warehouseId", "Kho #" + id + " không thuộc công ty.");
            }
            if (!warehouse.active()) {
                throw BusinessException.invalidField("warehouseId", "Kho " + warehouse.code() + " đã ngừng hoạt động.");
            }
        }
        return warehouses;
    }

    private void requireOpenPeriods(PostingCommand command, Map<Long, WarehouseInfo> warehouses) {
        warehouses.values().stream().map(WarehouseInfo::branchId).distinct().sorted()
                .forEach(branchId -> periodGuard.assertOpen(command.companyId(), branchId, PeriodModule.INVENTORY,
                        command.postingDate()));
    }

    private void requireNotCounting(PostingCommand command, Map<Long, WarehouseInfo> warehouses, Instant now) {
        for (Long warehouseId : new TreeSet<>(warehouses.keySet())) {
            ledger.ensureWarehouseState(warehouseId, command.companyId(), now);
            ledger.countingStockCountForShare(warehouseId)
                    .filter(countId -> !countId.equals(command.exemptStockCountId()))
                    .ifPresent(countId -> {
                        throw new BusinessException(ErrorCode.STOCK_COUNT_IN_PROGRESS, "Kho "
                                + warehouses.get(warehouseId).code() + " đang kiểm kê, chưa nhập / xuất được (D-11).");
                    });
        }
    }

    private Map<ProductWarehouse, LockedSummary> lockSummaries(PostingCommand command, Instant now) {
        TreeSet<ProductWarehouse> keys = new TreeSet<>(command.extraLockKeys());
        command.movements().forEach(movement ->
                keys.add(new ProductWarehouse(movement.productId(), movement.warehouseId())));
        Map<ProductWarehouse, LockedSummary> summaries = new HashMap<>();
        for (ProductWarehouse key : keys) {
            summaries.put(key, ledger.lockSummary(command.companyId(), key.productId(), key.warehouseId(), now));
        }
        return summaries;
    }

    private List<Allocation> allocate(StockMovement movement, PostingCommand command) {
        requireFefoOrReason(movement, command.postingDate());
        Set<QualityStatus> statuses = switch (movement.outRule()) {
            case SELLABLE -> Set.of(QualityStatus.PASSED);
            case DISPOSABLE -> Set.of();
            case EXACT_STATUS -> Set.of(Objects.requireNonNull(movement.qualityStatus(), "qualityStatus"));
        };
        List<LockedBalance> candidates = ledger.lockCandidates(movement.productId(), movement.warehouseId(),
                movement.locationId(), movement.lotId(), movement.serialId(), statuses).stream()
                .filter(balance -> matchesRule(balance, movement.outRule(), command.postingDate()))
                .toList();
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal remaining = movement.quantity();
        for (LockedBalance balance : candidates) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = remaining.min(balance.onHand());
            allocations.add(new Allocation(movement, balance, take));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw shortage(movement, command, movement.quantity().subtract(remaining));
        }
        return allocations;
    }

    /** Chỉ định lô khác lô hết hạn sớm nhất có thể xuất thì phải có lý do (BR-INV-21). */
    private void requireFefoOrReason(StockMovement movement, LocalDate postingDate) {
        if (movement.outRule() != StockMovement.OutRule.SELLABLE || movement.lotId() == null
                || movement.lotChoiceJustified()) {
            return;
        }
        Long firstLot = ledger.lockCandidates(movement.productId(), movement.warehouseId(), null, null, null,
                        Set.of(QualityStatus.PASSED)).stream()
                .filter(balance -> isSellable(balance, postingDate))
                .map(balance -> balance.key().lotId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (firstLot != null && !firstLot.equals(movement.lotId())) {
            throw BusinessException.invalidField("lotOverrideReason",
                    "Lô đã chọn khác lô hết hạn sớm nhất (FEFO); phải ghi lý do chọn lô khác (BR-INV-21).");
        }
    }

    private boolean matchesRule(LockedBalance balance, StockMovement.OutRule rule, LocalDate postingDate) {
        return switch (rule) {
            case SELLABLE -> isSellable(balance, postingDate);
            case DISPOSABLE -> DISPOSABLE_STATUSES.contains(balance.key().qualityStatus())
                    || (balance.expiryDate() != null && balance.expiryDate().isBefore(postingDate));
            case EXACT_STATUS -> true;
        };
    }

    private static boolean isSellable(LockedBalance balance, LocalDate postingDate) {
        return balance.key().qualityStatus() == QualityStatus.PASSED && !balance.lotBlocked()
                && !balance.serialBlocked()
                && (balance.expiryDate() == null || !balance.expiryDate().isBefore(postingDate));
    }

    /** Lỗi thiếu tồn rõ nguyên nhân: lô hết hạn, lô / serial không bán được, chứng từ phụ thuộc khi đảo, hay thiếu số. */
    private BusinessException shortage(StockMovement movement, PostingCommand command, BigDecimal found) {
        if (movement.type() == TransactionType.REVERSAL && movement.reversalOfId() != null) {
            InventoryTransaction original = transactionRepository.findById(movement.reversalOfId())
                    .orElseThrow(() -> new IllegalStateException("Thiếu dòng sổ gốc " + movement.reversalOfId()));
            List<String> dependents = transactionRepository.findDependentReferences(original.getWarehouseId(),
                    original.getProductId(), original.getLotId(), original.getSerialId(), original.getPostedAt(),
                    original.getReferenceId());
            return new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Hàng của chứng từ gốc đã được xuất "
                    + "bởi chứng từ khác; phải đảo các chứng từ này trước: " + String.join(", ", dependents));
        }
        if (movement.outRule() == StockMovement.OutRule.SELLABLE && (movement.lotId() != null
                || movement.serialId() != null)) {
            List<LockedBalance> any = ledger.lockCandidates(movement.productId(), movement.warehouseId(),
                    movement.locationId(), movement.lotId(), movement.serialId(), Set.of());
            if (any.stream().anyMatch(balance -> balance.expiryDate() != null
                    && balance.expiryDate().isBefore(command.postingDate()))) {
                return new BusinessException(ErrorCode.LOT_EXPIRED, "Lô đã hết hạn, không xuất bán / sử dụng (BR-INV-04).");
            }
            if (!any.isEmpty() && found.signum() == 0) {
                return new BusinessException(ErrorCode.LOT_NOT_SELLABLE,
                        "Lô / serial không ở trạng thái ĐẠT hoặc đang bị khóa (BR-INV-23).");
            }
        }
        return new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Tồn của hàng #" + movement.productId()
                + " tại kho #" + movement.warehouseId() + " không đủ (còn " + plain(found) + ", cần "
                + plain(movement.quantity()) + ").");
    }

    /**
     * Tồn khả dụng = tồn bán được − giữ chỗ (BR mục 5.4), kiểm tra sau khi đã khóa (BR-INV-20). Phần vượt chỉ được
     * xuất khi chứng từ dùng chính sách đang hiệu lực còn hạn mức (BR-GEN-31, D-10).
     */
    private void checkAvailability(List<Allocation> outs, Map<ProductWarehouse, LockedSummary> summaries,
                                   PostingCommand command) {
        Map<ProductWarehouse, BigDecimal> sellableOut = new TreeMap<>();
        outs.stream()
                .filter(allocation -> allocation.movement().outRule() == StockMovement.OutRule.SELLABLE)
                .forEach(allocation -> sellableOut.merge(pw(allocation.balance().key()), allocation.quantity(),
                        BigDecimal::add));
        for (Map.Entry<ProductWarehouse, BigDecimal> entry : sellableOut.entrySet()) {
            ProductWarehouse key = entry.getKey();
            BigDecimal available = ledger.sellableQuantity(key.productId(), key.warehouseId(), command.postingDate())
                    .subtract(summaries.get(key).reserved());
            BigDecimal excess = entry.getValue().subtract(available.max(BigDecimal.ZERO));
            if (excess.signum() <= 0) {
                continue;
            }
            if (command.issuePolicyId() == null) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Tồn khả dụng của hàng #"
                        + key.productId() + " không đủ (khả dụng " + plain(available.max(BigDecimal.ZERO)) + ", cần "
                        + plain(entry.getValue()) + "); phần còn lại đang giữ chỗ cho chứng từ khác.");
            }
            StockIssuePolicy policy = policyRepository.findByIdForUpdate(command.issuePolicyId())
                    .filter(found -> found.getCompanyId().equals(command.companyId()))
                    .orElseThrow(() -> BusinessException.invalidField("issuePolicyId", "Chính sách không tồn tại."));
            policy.consume(command.referenceId(), key.warehouseId(), key.productId(), command.postingDate(), excess);
        }
    }

    private void lockSerials(PostingCommand command, List<Allocation> outs) {
        Set<Long> serialIds = new TreeSet<>();
        outs.forEach(allocation -> {
            if (allocation.balance().key().serialId() != null) {
                serialIds.add(allocation.balance().key().serialId());
            }
        });
        command.movements().stream().map(StockMovement::serialId).filter(Objects::nonNull).forEach(serialIds::add);
        ledger.lockSerials(serialIds);
    }

    private PostedMovement applyOut(Allocation allocation, PostingCommand command,
                                    Map<Long, WarehouseInfo> warehouses, Instant now) {
        StockKey key = allocation.balance().key();
        if (!ledger.decrement(allocation.balance().id(), allocation.quantity(), now)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "Tồn thực tế không đủ để xuất.");
        }
        InventoryTransaction transaction = record(command, warehouses, now, key, allocation.movement(),
                MovementDirection.OUT, allocation.quantity(), null);
        if (key.serialId() != null) {
            serial(key.serialId()).moveTo(allocation.movement().type() == TransactionType.SCRAP
                    ? SerialStatus.SCRAPPED : SerialStatus.ISSUED, null);
        }
        return new PostedMovement(transaction.getId(), allocation.movement().lineId(), MovementDirection.OUT, key,
                allocation.quantity());
    }

    private PostedMovement applyIn(StockMovement movement, PostingCommand command,
                                   Map<Long, WarehouseInfo> warehouses, Instant now) {
        StockKey key = inKey(movement);
        if (key.serialId() != null) {
            if (movement.quantity().compareTo(BigDecimal.ONE) != 0 || ledger.serialOnHand(key.serialId()).signum() > 0) {
                throw new BusinessException(ErrorCode.SERIAL_UNAVAILABLE, "Serial "
                        + serial(key.serialId()).getSerialNo() + " đang tồn ở nơi khác hoặc số lượng khác 1 (BR-INV-02).");
            }
        }
        ledger.increment(command.companyId(), key, movement.quantity(), now);
        InventoryTransaction transaction = record(command, warehouses, now, key, movement, MovementDirection.IN,
                movement.quantity(), movement.unitCost());
        if (key.serialId() != null) {
            serial(key.serialId()).moveTo(SerialStatus.IN_STOCK, key.warehouseId());
        }
        return new PostedMovement(transaction.getId(), movement.lineId(), MovementDirection.IN, key,
                movement.quantity());
    }

    private InventoryTransaction record(PostingCommand command, Map<Long, WarehouseInfo> warehouses, Instant now,
                                        StockKey key, StockMovement movement, MovementDirection direction,
                                        BigDecimal quantity, BigDecimal unitCost) {
        return transactionRepository.save(new InventoryTransaction(command.companyId(),
                warehouses.get(key.warehouseId()).branchId(), command.postingDate(), now, key, movement.type(),
                direction, quantity, unitCost, command.partnerId(),
                new InventoryTransaction.Reference(command.referenceType(), command.referenceId(), movement.lineId(),
                        command.referenceNo()),
                movement.reversalOfId(), command.actorId()));
    }

    private InventorySerial serial(Long serialId) {
        return serialRepository.findById(serialId)
                .orElseThrow(() -> new IllegalStateException("Thiếu serial " + serialId));
    }

    private static StockKey inKey(StockMovement movement) {
        return new StockKey(movement.productId(), movement.warehouseId(), movement.locationId(), movement.lotId(),
                movement.serialId(), movement.qualityStatus());
    }

    private static ProductWarehouse pw(StockKey key) {
        return new ProductWarehouse(key.productId(), key.warehouseId());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
