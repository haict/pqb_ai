package com.company.erp.sales.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.InventoryReservationService;
import com.company.erp.inventory.application.InventoryReservationService.ReservationUse;
import com.company.erp.inventory.application.StockOutService;
import com.company.erp.inventory.application.StockOutService.IssuedItem;
import com.company.erp.inventory.application.StockOutService.StockOutCommand;
import com.company.erp.inventory.application.StockOutService.StockOutLine;
import com.company.erp.inventory.application.StockOutService.StockOutResult;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.sales.application.dto.ConfirmDeliveryRequest;
import com.company.erp.sales.application.dto.ReverseRequest;
import com.company.erp.sales.application.dto.SalesDeliveryResponse;
import com.company.erp.sales.application.dto.ShipRequest;
import com.company.erp.sales.domain.SalesDelivery;
import com.company.erp.sales.domain.SalesDeliveryItem;
import com.company.erp.sales.domain.SalesDeliveryLine;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xuất kho và giao hàng theo phiếu giao (BR-SAL-20..23): xuất kho (PXK có nguồn qua {@link StockOutService}, FEFO hoặc
 * lô chỉ định, dùng phần hàng đã giữ cho đơn, kiểm tra hạn dùng còn lại tối thiểu của khách D-07), cập nhật số đã
 * xuất của đơn; đang giao; khách nhận (số đã giao); giao thất bại / đảo (đảo phiếu xuất, hoàn giữ chỗ, số đã xuất).
 * Thứ tự khóa: phiếu giao → đơn bán → (kho: kỳ, tổng (hàng, kho), giữ chỗ, tồn chi tiết, serial); cùng chiều đơn → tồn
 * với duyệt / giữ hàng. READ COMMITTED như ghi sổ kho.
 */
@Service
public class SalesDeliveryPostingService {

    static final String POST = "sales-delivery:post";
    static final String DELIVER = "sales-delivery:deliver";
    static final String REVERSE = "sales-delivery:reverse";
    private static final Logger log = LoggerFactory.getLogger(SalesDeliveryPostingService.class);

    private final SalesDeliveryService deliveries;
    private final SalesOrderRepository orderRepository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final StockOutService stockOut;
    private final InventoryReservationService reservations;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public SalesDeliveryPostingService(SalesDeliveryService deliveries, SalesOrderRepository orderRepository,
                                       SalesDocumentFlow flow, SalesAccess access, StockOutService stockOut,
                                       InventoryReservationService reservations, BusinessCalendar calendar,
                                       Clock clock) {
        this.deliveries = deliveries;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.stockOut = stockOut;
        this.reservations = reservations;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Xuất kho theo phiếu giao đã duyệt (BR-SAL-21). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesDeliveryResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + SalesDeliveryService.PATH + "/" + id + "/post", id,
                SalesDeliveryResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    SalesDelivery document = flow.lock(actor, id, deliveries.spec());
                    issueLocked(actor, document);
                    return flow.act(actor, document, "POST", null, deliveries.spec());
                });
    }

    @Transactional
    public SalesDeliveryResponse ship(CurrentUser actor, Long id, String key, ShipRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesDeliveryService.PATH + "/" + id + "/ship", request,
                SalesDeliveryResponse.class, () -> {
                    access.requirePermission(actor, DELIVER);
                    SalesDelivery document = flow.lock(actor, id, deliveries.spec());
                    document.markShipped(request == null ? null : SalesAccess.trim(request.shipperName()),
                            Instant.now(clock));
                    return flow.act(actor, document, "SHIP", null, deliveries.spec());
                });
    }

    /** Khách nhận hàng: cộng số đã giao của đơn (BR-SAL-20, 22). */
    @Transactional
    public SalesDeliveryResponse confirm(CurrentUser actor, Long id, String key, ConfirmDeliveryRequest request) {
        return flow.idempotent(actor, key, "POST " + SalesDeliveryService.PATH + "/" + id + "/confirm", request,
                SalesDeliveryResponse.class, () -> {
                    access.requirePermission(actor, DELIVER);
                    SalesDelivery document = flow.lock(actor, id, deliveries.spec());
                    confirmLocked(document, request);
                    return flow.act(actor, document, "CONFIRM", null, deliveries.spec());
                });
    }

    /** Giao thất bại: hàng quay về kho bằng chứng từ đảo phiếu xuất (BR-SAL-23); bắt buộc lý do. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesDeliveryResponse fail(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return reverse(actor, id, key, request, true);
    }

    /** Đảo phiếu giao chưa giao / chưa xuất hóa đơn (lập sai). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SalesDeliveryResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return reverse(actor, id, key, request, false);
    }

    private SalesDeliveryResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request,
                                          boolean failed) {
        String action = failed ? "fail" : "reverse";
        return flow.idempotent(actor, key, "POST " + SalesDeliveryService.PATH + "/" + id + "/" + action, request,
                SalesDeliveryResponse.class, () -> {
                    access.requirePermission(actor, failed ? DELIVER : REVERSE);
                    SalesDelivery document = flow.lock(actor, id, deliveries.spec());
                    if (document.getStatus() != SalesDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + document.getDocumentNo()
                                + " chưa xuất kho hoặc đã đảo.");
                    }
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    document.markReversed(failed, actor.id(), Instant.now(clock), date, reason);
                    SalesOrder order = lockOrder(document);
                    stockOut.reverse(actor, document.getInventoryDocumentId(), date, reason);
                    reservations.restore(order.getCompanyId(), SalesOrder.TYPE_CODE, order.getId(),
                            reservationUses(document, order));
                    document.getLines().forEach(line -> order.recordIssue(line.getOrderLineId(),
                            line.getBaseQuantity().negate(), "lines"));
                    orderRepository.save(order);
                    return flow.act(actor, document, failed ? "FAIL" : "REVERSE", reason, deliveries.spec());
                });
    }

    /** Xuất kho phiếu giao đã khóa (dùng cả cho bán nhanh). */
    void issueLocked(CurrentUser actor, SalesDelivery document) {
        if (document.getStatus() != SalesDocumentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + document.getDocumentNo()
                    + " ở trạng thái " + document.getStatus() + ", chỉ xuất kho phiếu đã duyệt.");
        }
        SalesOrder order = lockOrder(document);
        for (SalesDeliveryLine line : document.getLines()) {
            order.recordIssue(line.getOrderLineId(), line.getBaseQuantity(), "lines[" + (line.getLineNo() - 1) + "]");
        }
        orderRepository.save(order);
        StockOutResult result = stockOut.issue(actor, new StockOutCommand(SalesDelivery.TYPE_CODE, document.getId(),
                document.getDocumentNo(), StockOutService.SALE, document.getWarehouseId(), document.getCustomerId(),
                document.getPostingDate(), document.getPostingDate(), "Xuất bán theo " + document.getDocumentNo(),
                document.getLines().stream().map(SalesDeliveryPostingService::toStockLine).toList(),
                SalesOrder.TYPE_CODE, order.getId(), reservationUses(document, order)));
        requireShelfLife(document, result.items());
        Map<Long, List<SalesDeliveryItem>> issued = new HashMap<>();
        for (IssuedItem item : result.items()) {
            issued.computeIfAbsent(item.sourceLineId(), ignored -> new ArrayList<>()).add(new SalesDeliveryItem(
                    item.lotId(), item.lotNo(), item.expiryDate(), item.serialNo(), item.qualityStatus(),
                    item.quantity()));
        }
        document.markIssued(result.documentId(), issued, actor.id(), Instant.now(clock));
        log.info("Phiếu giao {} xuất kho {}", document.getDocumentNo(), result.documentNo());
    }

    void confirmLocked(SalesDelivery document, ConfirmDeliveryRequest request) {
        document.markDelivered(request.receivedDate(), SalesAccess.trim(request.receivedBy()),
                SalesAccess.trim(request.signedDocumentRef()), SalesAccess.trim(request.note()));
        SalesOrder order = lockOrder(document);
        document.getLines().forEach(line -> order.recordDelivery(line.getOrderLineId(), line.getBaseQuantity()));
        orderRepository.save(order);
    }

    /** Hạn dùng còn lại tối thiểu theo khách (D-07): lô xuất phải còn hạn ít nhất số ngày khách yêu cầu. */
    private void requireShelfLife(SalesDelivery document, List<IssuedItem> items) {
        CustomerProfile customer = access.customerProfile(document.getCustomerId());
        Integer days = customer.minRemainingShelfLifeDays();
        if (days == null || days <= 0) {
            return;
        }
        LocalDate limit = document.getPostingDate().plusDays(days);
        items.stream().filter(item -> item.expiryDate() != null && item.expiryDate().isBefore(limit)).findFirst()
                .ifPresent(item -> {
                    throw new BusinessException(ErrorCode.LOT_NOT_SELLABLE, "Lô " + item.lotNo() + " hết hạn ngày "
                            + item.expiryDate() + ", còn dưới " + days + " ngày khách " + customer.code()
                            + " yêu cầu (D-07); chọn lô khác.");
                });
    }

    private SalesOrder lockOrder(SalesDelivery document) {
        return orderRepository.findByIdForUpdate(document.getOrderId()).orElseThrow(BusinessException::notFound);
    }

    /** Phần giữ chỗ của đơn được dùng: theo dòng đơn, đúng số lượng xuất (đơn vị cơ sở). */
    private static List<ReservationUse> reservationUses(SalesDelivery document, SalesOrder order) {
        return document.getLines().stream()
                .map(line -> new ReservationUse(line.getOrderLineId(), line.getProductId(), order.getWarehouseId(),
                        line.getBaseQuantity()))
                .toList();
    }

    private static StockOutLine toStockLine(SalesDeliveryLine line) {
        return new StockOutLine(line.getId(), line.getProductId(), line.getUnitId(), line.getQuantity(),
                line.getLotNo(), null, line.getLotOverrideReason(), line.getNote(), line.getSerialNumbers());
    }
}
