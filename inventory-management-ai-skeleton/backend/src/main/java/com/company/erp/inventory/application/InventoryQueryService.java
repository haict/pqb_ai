package com.company.erp.inventory.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.dto.FefoAllocationResponse;
import com.company.erp.inventory.application.dto.InventoryTransactionResponse;
import com.company.erp.inventory.application.dto.LotResponse;
import com.company.erp.inventory.application.dto.SerialResponse;
import com.company.erp.inventory.application.dto.StockAlertResponse;
import com.company.erp.inventory.application.dto.StockBalanceResponse;
import com.company.erp.inventory.application.dto.StockSummaryResponse;
import com.company.erp.inventory.domain.InventoryBalance;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.domain.SerialStatus;
import com.company.erp.inventory.domain.StockSummary;
import com.company.erp.inventory.infrastructure.persistence.InventoryBalanceRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc.LockedBalance;
import com.company.erp.inventory.infrastructure.persistence.StockQueryJdbc;
import com.company.erp.inventory.infrastructure.persistence.StockSummaryRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.StockLevelInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tra cứu tồn kho trong phạm vi dữ liệu: bốn chỉ tiêu tồn (BR mục 5.4), tồn chi tiết, sổ nhật ký (truy vết, BR-INV-06),
 * lô, serial, đề xuất FEFO (BR-INV-21), cảnh báo (BR-INV-40). Mọi số liệu đọc từ bảng số dư / sổ nhật ký.
 */
@Service
public class InventoryQueryService {

    static final String EXPIRY_WARNING_DAYS = "inventory.expiry-warning-days";
    private static final String VIEW = "inventory:view";

    private final StockSummaryRepository summaryRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final StockLedgerJdbc ledger;
    private final StockQueryJdbc stockQuery;
    private final InventoryDocumentMapper mapper;
    private final InventoryAccess access;
    private final MasterDataQueryService masterData;
    private final ParameterService parameters;
    private final BusinessCalendar calendar;

    @SuppressWarnings("java:S107")
    public InventoryQueryService(StockSummaryRepository summaryRepository, InventoryBalanceRepository balanceRepository,
                                 InventoryTransactionRepository transactionRepository,
                                 InventoryLotRepository lotRepository, InventorySerialRepository serialRepository,
                                 StockLedgerJdbc ledger, StockQueryJdbc stockQuery, InventoryDocumentMapper mapper,
                                 InventoryAccess access, MasterDataQueryService masterData,
                                 ParameterService parameters, BusinessCalendar calendar) {
        this.summaryRepository = summaryRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.ledger = ledger;
        this.stockQuery = stockQuery;
        this.mapper = mapper;
        this.access = access;
        this.masterData = masterData;
        this.parameters = parameters;
        this.calendar = calendar;
    }

    /** Bộ lọc chung của các danh sách tồn. */
    public record StockFilter(Long companyId, Long warehouseId, Long productId, Long lotId, Long serialId,
                              QualityStatus qualityStatus) {
    }

    /** Bộ lọc sổ nhật ký kho. */
    public record LedgerFilter(Long companyId, Long warehouseId, Long productId, Long lotId, Long serialId,
                               String referenceType, Long referenceId, LocalDate dateFrom, LocalDate dateTo) {
    }

    @Transactional(readOnly = true)
    public Page<StockSummaryResponse> summaries(CurrentUser actor, StockFilter filter, Pageable pageable) {
        Set<Long> warehouses = scope(actor, filter.companyId());
        Specification<StockSummary> spec = (root, query, cb) -> cb.and(common(root, cb, filter, warehouses)
                .toArray(Predicate[]::new));
        Pageable page = PageRequests.checked(pageable, Set.of("productId", "warehouseId", "onHandQuantity"),
                Sort.by("productId", "warehouseId"));
        Page<StockSummary> rows = summaryRepository.findAll(spec, page);
        Map<Long, ProductInfo> products = mapper.products(rows.map(StockSummary::getProductId).toSet());
        Map<String, BigDecimal> sellable = stockQuery.sellable(rows.map(StockSummary::getProductId).toSet(),
                rows.map(StockSummary::getWarehouseId).toSet(), calendar.today());
        return rows.map(row -> {
            ProductInfo product = products.get(row.getProductId());
            BigDecimal sellableQuantity = sellable.getOrDefault(
                    StockQueryJdbc.pairKey(row.getProductId(), row.getWarehouseId()), BigDecimal.ZERO);
            return new StockSummaryResponse(row.getProductId(), product == null ? null : product.code(),
                    product == null ? null : product.name(), product == null ? null : product.baseUnitId(),
                    row.getWarehouseId(), row.getOnHandQuantity(), sellableQuantity, row.getReservedQuantity(),
                    sellableQuantity.subtract(row.getReservedQuantity()).max(BigDecimal.ZERO),
                    row.getIncomingQuantity());
        });
    }

    @Transactional(readOnly = true)
    public Page<StockBalanceResponse> balances(CurrentUser actor, StockFilter filter, Pageable pageable) {
        Set<Long> warehouses = scope(actor, filter.companyId());
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            List<Predicate> predicates = common(root, cb, filter, warehouses);
            predicates.add(cb.greaterThan(root.get("onHandQuantity"), BigDecimal.ZERO));
            if (filter.lotId() != null) {
                predicates.add(cb.equal(root.get("lotId"), filter.lotId()));
            }
            if (filter.serialId() != null) {
                predicates.add(cb.equal(root.get("serialId"), filter.serialId()));
            }
            if (filter.qualityStatus() != null) {
                predicates.add(cb.equal(root.get("qualityStatus"), filter.qualityStatus()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, Set.of("productId", "warehouseId", "onHandQuantity"),
                Sort.by("productId", "warehouseId", "id"));
        Page<InventoryBalance> rows = balanceRepository.findAll(spec, page);
        Map<Long, ProductInfo> products = mapper.products(rows.map(InventoryBalance::getProductId).toSet());
        Map<Long, InventoryLot> lots = mapper.lots(ids(rows.map(InventoryBalance::getLotId).toList()));
        Map<Long, InventorySerial> serials = mapper.serials(ids(rows.map(InventoryBalance::getSerialId).toList()));
        return rows.map(row -> {
            ProductInfo product = products.get(row.getProductId());
            InventoryLot lot = row.getLotId() == null ? null : lots.get(row.getLotId());
            InventorySerial serial = row.getSerialId() == null ? null : serials.get(row.getSerialId());
            return new StockBalanceResponse(row.getId(), row.getProductId(), product == null ? null : product.code(),
                    product == null ? null : product.name(), row.getWarehouseId(), row.getLocationId(),
                    row.getLotId(), lot == null ? null : lot.getLotNo(), lot == null ? null : lot.getExpiryDate(),
                    lot != null && lot.isBlocked(), row.getSerialId(), serial == null ? null : serial.getSerialNo(),
                    row.getQualityStatus(), row.getOnHandQuantity());
        });
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> transactions(CurrentUser actor, LedgerFilter filter,
                                                           Pageable pageable) {
        Set<Long> warehouses = scope(actor, filter.companyId());
        Specification<InventoryTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (warehouses != null) {
                predicates.add(root.get("warehouseId").in(warehouses));
            }
            addEqual(predicates, cb, root.get("warehouseId"), filter.warehouseId());
            addEqual(predicates, cb, root.get("productId"), filter.productId());
            addEqual(predicates, cb, root.get("lotId"), filter.lotId());
            addEqual(predicates, cb, root.get("serialId"), filter.serialId());
            addEqual(predicates, cb, root.get("referenceType"), filter.referenceType());
            addEqual(predicates, cb, root.get("referenceId"), filter.referenceId());
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("postingDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("postingDate"), filter.dateTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, Set.of("postingDate", "postedAt", "id"),
                Sort.by(Sort.Order.desc("postingDate"), Sort.Order.desc("id")));
        Page<InventoryTransaction> rows = transactionRepository.findAll(spec, page);
        Map<Long, ProductInfo> products = mapper.products(rows.map(InventoryTransaction::getProductId).toSet());
        Map<Long, InventoryLot> lots = mapper.lots(ids(rows.map(InventoryTransaction::getLotId).toList()));
        Map<Long, InventorySerial> serials = mapper.serials(ids(rows.map(InventoryTransaction::getSerialId).toList()));
        return rows.map(row -> mapper.toTransaction(row, products, lots, serials));
    }

    @Transactional(readOnly = true)
    public Page<LotResponse> lots(CurrentUser actor, Long companyId, Long productId, String keyword,
                                  Pageable pageable) {
        Set<Long> warehouses = scope(actor, companyId);
        Specification<InventoryLot> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            addEqual(predicates, cb, root.get("productId"), productId);
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("lotNo")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, Set.of("lotNo", "expiryDate", "createdAt"),
                Sort.by(Sort.Order.asc("expiryDate"), Sort.Order.asc("id")));
        Page<InventoryLot> rows = lotRepository.findAll(spec, page);
        Map<Long, ProductInfo> products = mapper.products(rows.map(InventoryLot::getProductId).toSet());
        Map<Long, BigDecimal> onHand = stockQuery.onHandByLot(rows.map(InventoryLot::getId).toList(), warehouses);
        LocalDate today = calendar.today();
        return rows.map(lot -> {
            ProductInfo product = products.get(lot.getProductId());
            return new LotResponse(lot.getId(), lot.getProductId(), product == null ? null : product.code(),
                    product == null ? null : product.name(), lot.getLotNo(), lot.getManufactureDate(),
                    lot.getExpiryDate(), lot.getExpiryDate() == null ? null
                    : ChronoUnit.DAYS.between(today, lot.getExpiryDate()), lot.isBlocked(), lot.getBlockReason(),
                    onHand.getOrDefault(lot.getId(), BigDecimal.ZERO), lot.getVersion());
        });
    }

    @Transactional(readOnly = true)
    public Page<SerialResponse> serials(CurrentUser actor, Long companyId, Long productId, String keyword,
                                        SerialStatus status, Pageable pageable) {
        scope(actor, companyId);
        Specification<InventorySerial> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            addEqual(predicates, cb, root.get("productId"), productId);
            addEqual(predicates, cb, root.get("lifecycleStatus"), status);
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("serialNo")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, Set.of("serialNo", "createdAt"),
                Sort.by(Sort.Order.asc("serialNo")));
        Page<InventorySerial> rows = serialRepository.findAll(spec, page);
        Map<Long, ProductInfo> products = mapper.products(rows.map(InventorySerial::getProductId).toSet());
        return rows.map(serial -> {
            ProductInfo product = products.get(serial.getProductId());
            return new SerialResponse(serial.getId(), serial.getProductId(), product == null ? null : product.code(),
                    product == null ? null : product.name(), serial.getSerialNo(), serial.getLotId(),
                    serial.getLifecycleStatus(), serial.getCurrentWarehouseId(), serial.isBlocked(),
                    serial.getBlockReason(), serial.getVersion());
        });
    }

    /** Đề xuất lô xuất theo FEFO: lô bán được, hạn dùng sớm nhất trước, đủ số lượng cần (BR-INV-21). */
    @Transactional(readOnly = true)
    public List<FefoAllocationResponse> fefo(CurrentUser actor, Long warehouseId, Long productId,
                                             BigDecimal quantity) {
        access.requirePermission(actor, VIEW);
        access.visibleWarehouse(actor, warehouseId);
        LocalDate today = calendar.today();
        Map<Long, InventoryLot> lots = new HashMap<>();
        List<FefoAllocationResponse> result = new ArrayList<>();
        BigDecimal remaining = quantity;
        for (LockedBalance balance : ledger.readCandidates(productId, warehouseId, Set.of(QualityStatus.PASSED))) {
            boolean sellable = !balance.lotBlocked() && !balance.serialBlocked()
                    && (balance.expiryDate() == null || !balance.expiryDate().isBefore(today));
            if (!sellable || remaining.signum() <= 0) {
                continue;
            }
            BigDecimal take = remaining.min(balance.onHand());
            remaining = remaining.subtract(take);
            Long lotId = balance.key().lotId();
            InventoryLot lot = lotId == null ? null
                    : lots.computeIfAbsent(lotId, id -> lotRepository.findById(id).orElse(null));
            result.add(new FefoAllocationResponse(lotId, lot == null ? null : lot.getLotNo(), balance.expiryDate(),
                    balance.key().locationId(), balance.onHand(), take));
        }
        return result;
    }

    /** Cảnh báo hàng sắp hết hạn / đã hết hạn còn tồn và tồn dưới định mức tối thiểu (BR-INV-40). */
    @Transactional(readOnly = true)
    public List<StockAlertResponse> alerts(CurrentUser actor, Long companyId, Long warehouseId) {
        Set<Long> warehouses = scope(actor, companyId);
        Collection<Long> filter = warehouseId != null ? Set.of(access.visibleWarehouse(actor, warehouseId).id())
                : warehouses;
        LocalDate today = calendar.today();
        int warningDays = parameters.integerValue(companyId, EXPIRY_WARNING_DAYS);
        List<StockQueryJdbc.ExpiringStock> expiring = stockQuery.expiring(companyId, filter,
                today.plusDays(warningDays));
        List<SummaryLevel> belowMin = belowMinimum(companyId, filter);
        Set<Long> productIds = expiring.stream().map(StockQueryJdbc.ExpiringStock::productId)
                .collect(Collectors.toSet());
        belowMin.forEach(level -> productIds.add(level.summary().getProductId()));
        Map<Long, ProductInfo> products = mapper.products(productIds);
        List<StockAlertResponse> alerts = new ArrayList<>();
        for (StockQueryJdbc.ExpiringStock row : expiring) {
            ProductInfo product = products.get(row.productId());
            alerts.add(new StockAlertResponse("EXPIRING", row.productId(), product == null ? null : product.code(),
                    product == null ? null : product.name(), row.warehouseId(), row.lotId(), row.lotNo(),
                    row.expiryDate(), ChronoUnit.DAYS.between(today, row.expiryDate()), row.onHand(), null));
        }
        for (SummaryLevel row : belowMin) {
            ProductInfo product = products.get(row.summary().getProductId());
            alerts.add(new StockAlertResponse("BELOW_MIN", row.summary().getProductId(),
                    product == null ? null : product.code(), product == null ? null : product.name(),
                    row.summary().getWarehouseId(), null, null, null, null, row.summary().getOnHandQuantity(),
                    row.level().minQuantity()));
        }
        return alerts;
    }

    private record SummaryLevel(StockSummary summary, StockLevelInfo level) {
    }

    private List<SummaryLevel> belowMinimum(Long companyId, Collection<Long> warehouseFilter) {
        Collection<Long> warehouseIds = warehouseFilter != null ? warehouseFilter
                : summaryRepository.findAll((root, query, cb) -> cb.equal(root.get("companyId"), companyId)).stream()
                .map(StockSummary::getWarehouseId).collect(Collectors.toSet());
        List<StockLevelInfo> levels = masterData.stockLevels(warehouseIds);
        if (levels.isEmpty()) {
            return List.of();
        }
        Map<String, StockSummary> summaries = summaryRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("companyId"), companyId), root.get("warehouseId").in(warehouseIds)))
                .stream().collect(Collectors.toMap(row -> StockQueryJdbc.pairKey(row.getProductId(),
                        row.getWarehouseId()), Function.identity()));
        List<SummaryLevel> result = new ArrayList<>();
        for (StockLevelInfo level : levels) {
            StockSummary summary = summaries.get(StockQueryJdbc.pairKey(level.productId(), level.warehouseId()));
            BigDecimal onHand = summary == null ? BigDecimal.ZERO : summary.getOnHandQuantity();
            if (summary != null && onHand.compareTo(level.minQuantity()) < 0) {
                result.add(new SummaryLevel(summary, level));
            }
        }
        return result;
    }

    private Set<Long> scope(CurrentUser actor, Long companyId) {
        access.requirePermission(actor, VIEW);
        return access.scopeWarehouseIds(actor, companyId);
    }

    private static <T> List<Predicate> common(Root<T> root,
                                              CriteriaBuilder cb, StockFilter filter,
                                              Set<Long> warehouses) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
        if (warehouses != null) {
            predicates.add(root.get("warehouseId").in(warehouses));
        }
        addEqual(predicates, cb, root.get("warehouseId"), filter.warehouseId());
        addEqual(predicates, cb, root.get("productId"), filter.productId());
        return predicates;
    }

    private static void addEqual(List<Predicate> predicates, CriteriaBuilder cb,
                                 Path<?> path, Object value) {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
    }

    private static Set<Long> ids(List<Long> values) {
        return values.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
