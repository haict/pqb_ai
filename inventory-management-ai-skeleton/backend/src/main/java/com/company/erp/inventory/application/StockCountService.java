package com.company.erp.inventory.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.dto.CommentRequest;
import com.company.erp.inventory.application.dto.CountedLineRequest;
import com.company.erp.inventory.application.dto.CountedLinesRequest;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.application.dto.StockCountLineResponse;
import com.company.erp.inventory.application.dto.StockCountRequest;
import com.company.erp.inventory.application.dto.StockCountResponse;
import com.company.erp.inventory.domain.InventoryBalance;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentLine;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.domain.StockCount;
import com.company.erp.inventory.domain.StockCountLine;
import com.company.erp.inventory.domain.StockCountStatus;
import com.company.erp.inventory.domain.StockKey;
import com.company.erp.inventory.infrastructure.persistence.InventoryBalanceRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.inventory.infrastructure.persistence.StockCountRepository;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.LocationInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.ApprovalSubmission;
import com.company.erp.system.application.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm kê kho (BR-INV-31, D-11): bắt đầu đếm chụp tồn và khóa nhập xuất của kho; nhập số đếm; gửi duyệt; ghi sổ sinh
 * phiếu điều chỉnh (PDC, loại COUNT) cho phần chênh lệch rồi mở khóa kho; hủy cũng mở khóa.
 */
@Service
public class StockCountService {

    static final String DOCUMENT_TYPE = "PKK";
    private static final String PERMISSION_PREFIX = "stock-count:";
    private static final Set<String> SORT_FIELDS = Set.of("countDate", "documentNo", "createdAt");

    private final StockCountRepository countRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryDocumentRepository documentRepository;
    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final StockLedgerJdbc ledger;
    private final InventoryDocumentPostingService documentPosting;
    private final InventoryDocumentMapper mapper;
    private final InventoryAccess access;
    private final InventoryNumbering numbering;
    private final MasterDataQueryService masterData;
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public StockCountService(StockCountRepository countRepository, InventoryBalanceRepository balanceRepository,
                             InventoryDocumentRepository documentRepository, InventoryLotRepository lotRepository,
                             InventorySerialRepository serialRepository, StockLedgerJdbc ledger,
                             InventoryDocumentPostingService documentPosting, InventoryDocumentMapper mapper,
                             InventoryAccess access, InventoryNumbering numbering, MasterDataQueryService masterData,
                             ApprovalService approvalService, IdempotencyService idempotency, Clock clock) {
        this.countRepository = countRepository;
        this.balanceRepository = balanceRepository;
        this.documentRepository = documentRepository;
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.ledger = ledger;
        this.documentPosting = documentPosting;
        this.mapper = mapper;
        this.access = access;
        this.numbering = numbering;
        this.masterData = masterData;
        this.approvalService = approvalService;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<StockCountResponse> search(CurrentUser actor, Long companyId, Long warehouseId,
                                           StockCountStatus status, Pageable pageable) {
        Set<Long> warehouses = access.scopeWarehouseIds(actor, companyId);
        Specification<StockCount> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (warehouses != null) {
                predicates.add(root.get("warehouseId").in(warehouses));
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Pageable page = PageRequests.checked(pageable, SORT_FIELDS,
                Sort.by(Sort.Order.desc("countDate"), Sort.Order.desc("id")));
        return countRepository.findAll(spec, page).map(count -> toResponse(count, false));
    }

    @Transactional(readOnly = true)
    public StockCountResponse get(CurrentUser actor, Long id) {
        StockCount count = countRepository.findById(id).orElseThrow(BusinessException::notFound);
        requireVisible(actor, count);
        return toResponse(count, true);
    }

    @Transactional
    public StockCountResponse create(CurrentUser actor, String idempotencyKey, StockCountRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/stock-counts",
                request);
        return idempotency.execute(command, StockCountResponse.class, () -> {
            access.requirePermission(actor, PERMISSION_PREFIX + "create");
            WarehouseInfo warehouse = access.usableWarehouse(actor, request.warehouseId(), "warehouseId");
            StockCount count = countRepository.saveAndFlush(new StockCount(warehouse.companyId(),
                    warehouse.branchId(), warehouse.id(), numbering.next(warehouse.companyId(), warehouse.branchId(),
                    DOCUMENT_TYPE, request.countDate()), request.countDate(), trim(request.note())));
            return act(actor, count, "CREATE", null);
        });
    }

    /**
     * Bắt đầu đếm: khóa trạng thái kho (chờ các lần ghi sổ đang chạy kết thúc), chặn nhập xuất của kho, chụp tồn chi
     * tiết hiện tại (D-11).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockCountResponse start(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-counts/" + id + "/start", id);
        return idempotency.execute(command, StockCountResponse.class, () -> {
            StockCount count = lockVisible(actor, id);
            access.requirePermission(actor, PERMISSION_PREFIX + "create");
            Instant now = Instant.now(clock);
            ledger.ensureWarehouseState(count.getWarehouseId(), count.getCompanyId(), now);
            ledger.countingStockCountForUpdate(count.getWarehouseId()).ifPresent(other -> {
                throw new BusinessException(ErrorCode.STOCK_COUNT_IN_PROGRESS,
                        "Kho đang có phiếu kiểm kê khác chưa kết thúc.");
            });
            List<StockCountLine> snapshot = balanceRepository
                    .findByWarehouseIdAndOnHandQuantityGreaterThan(count.getWarehouseId(), BigDecimal.ZERO).stream()
                    .sorted(Comparator.comparing(InventoryBalance::key))
                    .map(balance -> new StockCountLine(balance.key(), balance.getOnHandQuantity()))
                    .toList();
            count.start(actor.id(), now, snapshot);
            ledger.setCountingStockCount(count.getWarehouseId(), count.getId(), now);
            return act(actor, count, "START", null);
        });
    }

    /** Nhập số đếm; dòng không có trong ảnh chụp (hàng tìm thấy ngoài sổ) thêm với tồn sổ sách bằng 0. */
    @Transactional
    public StockCountResponse count(CurrentUser actor, Long id, CountedLinesRequest request) {
        StockCount count = lockVisible(actor, id);
        access.requirePermission(actor, PERMISSION_PREFIX + "create");
        count.requireCounting();
        Instant now = Instant.now(clock);
        Map<Long, StockCountLine> lines = count.getLines().stream()
                .collect(Collectors.toMap(StockCountLine::getId, line -> line));
        for (int index = 0; index < request.lines().size(); index++) {
            CountedLineRequest counted = request.lines().get(index);
            String field = "lines[" + index + "]";
            StockCountLine line = counted.lineId() != null
                    ? existingLine(lines, counted.lineId(), field)
                    : newLine(count, counted, field);
            line.count(counted.countedQuantity(), actor.id(), now, trim(counted.note()));
        }
        countRepository.saveAndFlush(count);
        return toResponse(count, true);
    }

    @Transactional
    public StockCountResponse submit(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-counts/" + id + "/submit", id);
        return idempotency.execute(command, StockCountResponse.class, () -> {
            StockCount count = lockVisible(actor, id);
            access.requirePermission(actor, PERMISSION_PREFIX + "create");
            Instant now = Instant.now(clock);
            count.submit(actor.id(), now);
            boolean required = approvalService.submit(actor, new ApprovalSubmission(count.getCompanyId(),
                    count.getBranchId(), DOCUMENT_TYPE, count.getId(), count.getDocumentNo(), null, null))
                    .approvalRequired();
            if (!required) {
                count.approve(actor.id(), now);
            }
            return act(actor, count, "SUBMIT", null);
        });
    }

    @Transactional
    public StockCountResponse approve(CurrentUser actor, Long id, String idempotencyKey, CommentRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-counts/" + id + "/approve", request);
        return idempotency.execute(command, StockCountResponse.class, () -> {
            StockCount count = lockVisible(actor, id);
            access.requirePermission(actor, PERMISSION_PREFIX + "approve");
            requireStatus(count, StockCountStatus.SUBMITTED, "duyệt");
            String comment = request == null ? null : request.comment();
            if (approvalService.approve(actor, DOCUMENT_TYPE, id, comment).completed()) {
                count.approve(actor.id(), Instant.now(clock));
            }
            return act(actor, count, "APPROVE", comment);
        });
    }

    @Transactional
    public StockCountResponse reject(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-counts/" + id + "/reject", request);
        return idempotency.execute(command, StockCountResponse.class, () -> {
            StockCount count = lockVisible(actor, id);
            access.requirePermission(actor, PERMISSION_PREFIX + "approve");
            requireStatus(count, StockCountStatus.SUBMITTED, "từ chối");
            approvalService.reject(actor, DOCUMENT_TYPE, id, request.reason());
            count.reject();
            return act(actor, count, "REJECT", request.reason());
        });
    }

    /** Ghi sổ: sinh PDC (COUNT) đã duyệt theo chênh lệch, ghi sổ trong cùng transaction, mở khóa kho. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockCountResponse post(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-counts/" + id + "/post", id);
        return idempotency.execute(command, StockCountResponse.class, () -> {
            StockCount count = lockVisible(actor, id);
            access.requirePermission(actor, PERMISSION_PREFIX + "post");
            requireStatus(count, StockCountStatus.APPROVED, "ghi sổ");
            Instant now = Instant.now(clock);
            Long adjustmentId = postAdjustment(actor, count, now);
            count.markPosted(actor.id(), now, adjustmentId);
            releaseWarehouse(count, now);
            return act(actor, count, "POST", null);
        });
    }

    @Transactional
    public StockCountResponse cancel(CurrentUser actor, Long id, String idempotencyKey, ReasonRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/stock-counts/" + id + "/cancel", request);
        return idempotency.execute(command, StockCountResponse.class, () -> {
            StockCount count = lockVisible(actor, id);
            access.requirePermission(actor, PERMISSION_PREFIX + "create");
            if (count.getStatus() == StockCountStatus.SUBMITTED) {
                approvalService.cancelPending(actor, DOCUMENT_TYPE, id, request.reason());
            }
            boolean held = count.holdsWarehouse();
            Instant now = Instant.now(clock);
            count.cancel(actor.id(), now, request.reason());
            if (held) {
                releaseWarehouse(count, now);
            }
            return act(actor, count, "CANCEL", request.reason());
        });
    }

    private Long postAdjustment(CurrentUser actor, StockCount count, Instant now) {
        List<StockCountLine> differences = count.getLines().stream()
                .filter(line -> line.difference().signum() != 0)
                .toList();
        if (differences.isEmpty()) {
            return null;
        }
        Map<Long, ProductInfo> products = mapper.products(differences.stream().map(StockCountLine::getProductId)
                .collect(Collectors.toSet()));
        Map<Long, InventorySerial> serials = mapper.serials(differences.stream().map(StockCountLine::getSerialId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        List<InventoryDocumentLine> lines = new ArrayList<>();
        for (StockCountLine line : differences) {
            BigDecimal quantity = line.difference().abs();
            InventorySerial serial = line.getSerialId() == null ? null : serials.get(line.getSerialId());
            lines.add(new InventoryDocumentLine(lines.size() + 1, new InventoryDocumentLine.LineValues(
                    line.getProductId(), products.get(line.getProductId()).baseUnitId(), BigDecimal.ONE, quantity,
                    quantity, line.difference().signum() > 0 ? MovementDirection.IN : MovementDirection.OUT,
                    line.getLocationId(), null, line.getLotId(), null, null, null, line.getQualityStatus(), null,
                    null, null, line.getNote(), serial == null ? List.of() : List.of(serial.getSerialNo()))));
        }
        InventoryDocument adjustment = new InventoryDocument(count.getCompanyId(), count.getBranchId(),
                InventoryDocumentType.PDC, numbering.next(count.getCompanyId(), count.getBranchId(),
                InventoryDocumentType.PDC.name(), count.getCountDate()),
                new InventoryDocument.Header(DocumentPostingPlanner.COUNT, count.getCountDate(), count.getCountDate(),
                        count.getWarehouseId(), null, null, null, null, "Chênh lệch kiểm kê " + count.getDocumentNo(),
                        null, null, count.getId(), null, null));
        adjustment.replaceLines(lines);
        documentRepository.saveAndFlush(adjustment);
        // Phiếu kiểm kê đã được duyệt nên phiếu điều chỉnh sinh ra không qua luồng duyệt riêng (BR-INV-31).
        adjustment.submit(actor.id(), now);
        adjustment.approve(actor.id(), now);
        documentPosting.postApproved(adjustment, actor.id());
        documentRepository.saveAndFlush(adjustment);
        access.audit(actor, "POST", InventoryAccess.ENTITY_DOCUMENT, adjustment.getId(), adjustment.getDocumentNo(),
                "Sinh từ " + count.getDocumentNo(), null, null, adjustment.getCompanyId(), adjustment.getBranchId());
        return adjustment.getId();
    }

    private void releaseWarehouse(StockCount count, Instant now) {
        ledger.countingStockCountForUpdate(count.getWarehouseId())
                .filter(holder -> holder.equals(count.getId()))
                .ifPresent(holder -> ledger.setCountingStockCount(count.getWarehouseId(), null, now));
    }

    private static StockCountLine existingLine(Map<Long, StockCountLine> lines, Long lineId, String field) {
        StockCountLine line = lines.get(lineId);
        if (line == null) {
            throw BusinessException.invalidField(field + ".lineId", "Dòng kiểm kê không thuộc phiếu.");
        }
        return line;
    }

    private StockCountLine newLine(StockCount count, CountedLineRequest counted, String field) {
        if (counted.productId() == null) {
            throw BusinessException.invalidField(field + ".productId", "Phải chọn hàng cho dòng phát sinh.");
        }
        ProductInfo product = masterData.product(counted.productId())
                .filter(found -> found.companyId().equals(count.getCompanyId()) && found.stockTracked())
                .orElseThrow(() -> BusinessException.invalidField(field + ".productId", "Hàng hóa không hợp lệ."));
        Long locationId = counted.locationId() != null ? counted.locationId()
                : masterData.defaultLocation(count.getWarehouseId()).map(LocationInfo::id).orElse(null);
        if (locationId == null) {
            throw BusinessException.invalidField(field + ".locationId", "Phải chọn vị trí.");
        }
        masterData.location(locationId).filter(location -> location.warehouseId().equals(count.getWarehouseId()))
                .orElseThrow(() -> BusinessException.invalidField(field + ".locationId", "Vị trí không thuộc kho."));
        if (product.lotTracked() != (counted.lotId() != null)) {
            throw BusinessException.invalidField(field + ".lotId", "Hàng quản lý lô phải chọn lô (và ngược lại).");
        }
        if (counted.lotId() != null) {
            lotRepository.findById(counted.lotId()).filter(lot -> lot.getProductId().equals(product.id()))
                    .orElseThrow(() -> BusinessException.invalidField(field + ".lotId", "Lô không thuộc hàng."));
        }
        if (product.serialTracked() != (counted.serialId() != null)) {
            throw BusinessException.invalidField(field + ".serialId", "Hàng quản lý serial phải chọn serial.");
        }
        if (counted.serialId() != null) {
            serialRepository.findById(counted.serialId()).filter(serial -> serial.getProductId().equals(product.id()))
                    .orElseThrow(() -> BusinessException.invalidField(field + ".serialId", "Serial không thuộc hàng."));
        }
        QualityStatus status = counted.qualityStatus() != null ? counted.qualityStatus() : QualityStatus.PASSED;
        StockKey key = new StockKey(product.id(), count.getWarehouseId(), locationId, counted.lotId(),
                counted.serialId(), status);
        boolean duplicate = count.getLines().stream().anyMatch(line -> line.key(count.getWarehouseId()).equals(key));
        if (duplicate) {
            throw BusinessException.invalidField(field, "Dòng đã có trong phiếu; nhập số đếm theo lineId.");
        }
        StockCountLine line = new StockCountLine(key, BigDecimal.ZERO);
        count.addLine(line);
        return line;
    }

    private StockCountResponse act(CurrentUser actor, StockCount count, String action, String reason) {
        countRepository.saveAndFlush(count);
        access.audit(actor, action, InventoryAccess.ENTITY_STOCK_COUNT, count.getId(), count.getDocumentNo(), reason,
                null, count.getStatus(), count.getCompanyId(), count.getBranchId());
        return toResponse(count, true);
    }

    private StockCount lockVisible(CurrentUser actor, Long id) {
        StockCount count = countRepository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound);
        requireVisible(actor, count);
        return count;
    }

    private void requireVisible(CurrentUser actor, StockCount count) {
        if (!access.canAccessWarehouse(actor, count.getWarehouseId())) {
            throw BusinessException.notFound();
        }
        access.requirePermission(actor, PERMISSION_PREFIX + "view");
    }

    private static void requireStatus(StockCount count, StockCountStatus status, String action) {
        if (count.getStatus() != status) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu kiểm kê " + count.getDocumentNo()
                    + " ở trạng thái " + count.getStatus() + ", không " + action + " được.");
        }
    }

    private StockCountResponse toResponse(StockCount count, boolean detailed) {
        List<StockCountLineResponse> lines = detailed ? lineResponses(count) : List.of();
        return new StockCountResponse(count.getId(), count.getCompanyId(), count.getBranchId(),
                count.getWarehouseId(), count.getDocumentNo(), count.getCountDate(), count.getStatus(),
                count.getNote(), count.getStartedAt(), count.getPostedAt(), count.getAdjustmentDocumentId(),
                count.getCancelReason(), count.getVersion(), lines,
                detailed ? approvalService.history(DOCUMENT_TYPE, count.getId()) : List.of());
    }

    private List<StockCountLineResponse> lineResponses(StockCount count) {
        List<StockCountLine> lines = count.getLines();
        Map<Long, ProductInfo> products = mapper.products(lines.stream().map(StockCountLine::getProductId)
                .collect(Collectors.toSet()));
        Map<Long, InventoryLot> lots = mapper.lots(lines.stream().map(StockCountLine::getLotId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, InventorySerial> serials = mapper.serials(lines.stream().map(StockCountLine::getSerialId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return lines.stream().map(line -> {
            ProductInfo product = products.get(line.getProductId());
            InventoryLot lot = line.getLotId() == null ? null : lots.get(line.getLotId());
            InventorySerial serial = line.getSerialId() == null ? null : serials.get(line.getSerialId());
            return new StockCountLineResponse(line.getId(), line.getProductId(),
                    product == null ? null : product.code(), product == null ? null : product.name(),
                    line.getLocationId(), line.getLotId(), lot == null ? null : lot.getLotNo(),
                    lot == null ? null : lot.getExpiryDate(), line.getSerialId(),
                    serial == null ? null : serial.getSerialNo(), line.getQualityStatus(),
                    line.getSnapshotQuantity(), line.getCountedQuantity(),
                    line.getCountedQuantity() == null ? null : line.difference(), line.getNote());
        }).toList();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
