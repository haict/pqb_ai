package com.company.erp.purchasing.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.IncomingStockService;
import com.company.erp.inventory.application.IncomingStockService.IncomingChange;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.inventory.application.StockInService.StockInCommand;
import com.company.erp.inventory.application.StockInService.StockInLine;
import com.company.erp.inventory.application.StockInService.StockInResult;
import com.company.erp.purchasing.application.dto.PurchaseReceiptRequest;
import com.company.erp.purchasing.application.dto.PurchaseReceiptResponse;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.InspectionResult;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseInvoice;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import com.company.erp.purchasing.domain.PurchaseReceiptLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseCostEntryRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ phiếu nhận hàng (xác nhận số đã nhận / lỗi trên đơn mua), nhập kho theo phiếu (lập và ghi sổ phiếu nhập kho
 * PNK ở module kho, trừ hàng đang về), lệnh gộp nhận hàng và nhập kho (D-36), đảo. Thứ tự khóa: phiếu nhận hàng → đơn
 * mua → (kho: kỳ, trạng thái kho, tổng tồn, tồn chi tiết, serial) → tổng tồn cho hàng đang về; cùng thứ tự đơn mua →
 * tổng tồn với duyệt / đóng đơn mua. READ COMMITTED như ghi sổ kho (ADR-08).
 */
@Service
public class PurchaseReceiptPostingService {

    static final String POST = "purchase-receipt:post";
    static final String REVERSE = "purchase-receipt:reverse";
    static final String SOURCE_TYPE = PurchaseReceipt.TYPE_CODE;
    private static final Logger log = LoggerFactory.getLogger(PurchaseReceiptPostingService.class);

    private final PurchaseReceiptService receipts;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseCostEntryRepository costRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final StockInService stockIn;
    private final IncomingStockService incomingStock;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public PurchaseReceiptPostingService(PurchaseReceiptService receipts, PurchaseOrderRepository orderRepository,
                                         PurchaseCostEntryRepository costRepository, PurchaseDocumentFlow flow,
                                         PurchasingAccess access, StockInService stockIn,
                                         IncomingStockService incomingStock, BusinessCalendar calendar, Clock clock) {
        this.receipts = receipts;
        this.orderRepository = orderRepository;
        this.costRepository = costRepository;
        this.flow = flow;
        this.access = access;
        this.stockIn = stockIn;
        this.incomingStock = incomingStock;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Xác nhận nhận hàng: cộng số đã nhận / lỗi của dòng đơn mua; không đổi tồn (BR-PUR-07). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReceiptResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PurchaseReceiptService.PATH + "/" + id + "/post", id,
                PurchaseReceiptResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    PurchaseReceipt document = flow.lock(actor, id, receipts.spec());
                    postLocked(actor, document);
                    return flow.act(actor, document, "POST", null, receipts.spec());
                });
    }

    /** Nhập kho theo phiếu đã ghi sổ: phiếu nhập kho PNK cho dòng đạt / cách ly (BR-PUR-08). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReceiptResponse stockIn(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PurchaseReceiptService.PATH + "/" + id + "/stock-in", id,
                PurchaseReceiptResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    PurchaseReceipt document = flow.lock(actor, id, receipts.spec());
                    stockInLocked(actor, document, lockOrder(document));
                    return flow.act(actor, document, "STOCK_IN", null, receipts.spec());
                });
    }

    /**
     * Thao tác nhanh (D-36): lập phiếu nhận hàng, gửi duyệt, và nếu không phải duyệt (không vượt dung sai) thì ghi sổ
     * và nhập kho ngay. Vượt dung sai: phiếu dừng ở chờ duyệt.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReceiptResponse receiveAndStock(CurrentUser actor, String key, PurchaseReceiptRequest request) {
        return flow.idempotent(actor, key, "POST " + PurchaseReceiptService.PATH + "/receive-and-stock", request,
                PurchaseReceiptResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    PurchaseReceipt document = receipts.createDocument(actor, request);
                    flow.submitLocked(actor, document, receipts.spec());
                    if (document.getStatus() == PurchaseDocumentStatus.APPROVED) {
                        PurchaseOrder order = postLocked(actor, document);
                        stockInLocked(actor, document, order);
                    }
                    return flow.act(actor, document, "RECEIVE_AND_STOCK", null, receipts.spec());
                });
    }

    /**
     * Đảo: đảo phiếu nhập kho (hàng đã xuất thì 409 {@code HAS_DEPENDENT_DOCUMENTS}), hoàn số đã nhập, đã nhận, lỗi
     * của đơn mua và hàng đang về (BR-GEN-21).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseReceiptResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + PurchaseReceiptService.PATH + "/" + id + "/reverse", request,
                PurchaseReceiptResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    PurchaseReceipt document = flow.lock(actor, id, receipts.spec());
                    if (document.getStatus() != PurchaseDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + document.getDocumentNo()
                                + " chưa ghi sổ hoặc đã đảo.");
                    }
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    List<String> dependents = costRepository.findActiveReferences(document.getLines().stream()
                            .map(PurchaseReceiptLine::getId).toList(), PurchaseInvoice.TYPE_CODE);
                    if (!dependents.isEmpty()) {
                        throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Phiếu "
                                + document.getDocumentNo() + " đã được phân bổ chi phí / giảm giá; đảo trước: "
                                + String.join(", ", dependents));
                    }
                    PurchaseOrder order = lockOrder(document);
                    String reason = request.reason().trim();
                    if (document.isStocked()) {
                        if (document.getInventoryDocumentId() != null) {
                            stockIn.reverse(actor, document.getInventoryDocumentId(), date, reason);
                        }
                        applyStock(document, order, BigDecimal.ONE.negate());
                    }
                    document.getLines().forEach(line -> order.recordReceipt(line.getOrderLineId(),
                            line.getBaseQuantity().negate(), line.rejectedBaseQuantity().negate()));
                    orderRepository.save(order);
                    document.markReversed(actor.id(), Instant.now(clock), date, reason);
                    return flow.act(actor, document, "REVERSE", reason, receipts.spec());
                });
    }

    private PurchaseOrder postLocked(CurrentUser actor, PurchaseReceipt document) {
        PurchaseOrder order = lockOrder(document);
        order.requireReceivable();
        if (!document.isOverTolerance() && receipts.overTolerance(document, order)) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, "Phiếu " + document.getDocumentNo()
                    + " làm số đã nhận vượt dung sai so với đơn mua (có phiếu khác vừa ghi sổ); hủy và lập lại để "
                    + "gửi duyệt nhận vượt.");
        }
        document.getLines().forEach(line -> order.recordReceipt(line.getOrderLineId(), line.getBaseQuantity(),
                line.rejectedBaseQuantity()));
        orderRepository.save(order);
        document.markPosted(actor.id(), Instant.now(clock));
        return order;
    }

    private void stockInLocked(CurrentUser actor, PurchaseReceipt document, PurchaseOrder order) {
        if (document.getStatus() != PurchaseDocumentStatus.POSTED || document.isStocked()) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + document.getDocumentNo()
                    + " chưa ghi sổ hoặc đã nhập kho.");
        }
        List<StockInLine> lines = document.getLines().stream()
                .filter(line -> line.getInspectionResult().stocked())
                .map(PurchaseReceiptPostingService::toStockLine).toList();
        Long inventoryId = null;
        if (!lines.isEmpty()) {
            StockInResult result = stockIn.receive(actor, new StockInCommand(SOURCE_TYPE, document.getId(),
                    document.getDocumentNo(), document.getWarehouseId(), document.getSupplierId(),
                    document.getPostingDate(), document.getPostingDate(), "Nhập theo " + document.getDocumentNo(),
                    lines));
            inventoryId = result.documentId();
            log.info("Phiếu nhận hàng {} nhập kho {}", document.getDocumentNo(), result.documentNo());
        }
        applyStock(document, order, BigDecimal.ONE);
        document.markStocked(inventoryId, actor.id(), Instant.now(clock));
    }

    /** Số đã nhập của dòng đơn mua và hàng đang về theo phần nhập kho của phiếu ({@code sign} ±1). */
    private void applyStock(PurchaseReceipt document, PurchaseOrder order, BigDecimal sign) {
        List<IncomingChange> changes = new ArrayList<>();
        for (PurchaseReceiptLine line : document.getLines()) {
            if (line.getInspectionResult().stocked()) {
                BigDecimal incoming = order.recordStock(line.getOrderLineId(), line.getBaseQuantity().multiply(sign));
                changes.add(new IncomingChange(line.getProductId(), order.getWarehouseId(), incoming));
            }
        }
        orderRepository.save(order);
        incomingStock.apply(order.getCompanyId(), changes);
    }

    private PurchaseOrder lockOrder(PurchaseReceipt document) {
        return orderRepository.findByIdForUpdate(document.getPurchaseOrderId())
                .orElseThrow(BusinessException::notFound);
    }

    private static StockInLine toStockLine(PurchaseReceiptLine line) {
        return new StockInLine(line.getId(), line.getProductId(), line.getUnitId(), line.getQuantity(),
                line.getLocationId(), line.getLotNo(), line.getManufactureDate(), line.getExpiryDate(),
                line.getInspectionResult() == InspectionResult.QUARANTINE ? QualityStatus.QUARANTINE : null,
                line.getUnitCost(), line.getNote(), line.getSerialNumbers());
    }
}
