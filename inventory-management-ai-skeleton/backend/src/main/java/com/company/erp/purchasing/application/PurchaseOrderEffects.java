package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.inventory.application.IncomingStockService;
import com.company.erp.inventory.application.IncomingStockService.IncomingChange;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.domain.PurchaseRequisition;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseRequisitionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Chỉ tiêu điều phối do đơn mua thay đổi, trong transaction của đơn mua (BR mục 5.3): số đã đặt của dòng đề nghị mua
 * và hàng đang về (BR mục 5.4). Thứ tự khóa: đơn mua (đã khóa) → đề nghị mua theo id → tổng tồn theo (hàng, kho).
 */
@Component
class PurchaseOrderEffects {

    private final PurchaseRequisitionRepository requisitionRepository;
    private final IncomingStockService incomingStock;
    private final PurchasingAccess access;

    PurchaseOrderEffects(PurchaseRequisitionRepository requisitionRepository, IncomingStockService incomingStock,
                         PurchasingAccess access) {
        this.requisitionRepository = requisitionRepository;
        this.incomingStock = incomingStock;
        this.access = access;
    }

    /** Đơn mua chuyển APPROVED: cộng số đã đặt (không vượt số còn lại của đề nghị) và hàng đang về. */
    void approved(PurchaseOrder order) {
        applyRequisitions(order, BigDecimal.ONE);
        applyIncoming(order, PurchaseOrderLine::getBaseQuantity, BigDecimal.ONE);
    }

    /** Hủy đơn mua đã duyệt (chưa nhận hàng): hoàn số đã đặt và hàng đang về. */
    void cancelled(PurchaseOrder order) {
        applyRequisitions(order, BigDecimal.ONE.negate());
        applyIncoming(order, PurchaseOrderLine::outstandingQuantity, BigDecimal.ONE.negate());
    }

    /** Trước khi đóng phần còn lại: trừ hàng đang về theo số chưa nhập kho. */
    void closing(PurchaseOrder order) {
        applyIncoming(order, PurchaseOrderLine::outstandingQuantity, BigDecimal.ONE.negate());
    }

    private void applyRequisitions(PurchaseOrder order, BigDecimal sign) {
        List<PurchaseOrderLine> linked = order.getLines().stream()
                .filter(line -> line.getRequisitionLineId() != null).toList();
        if (linked.isEmpty()) {
            return;
        }
        List<Long> requisitionIds = requisitionRepository.findIdsByLineIds(linked.stream()
                .map(PurchaseOrderLine::getRequisitionLineId).toList());
        for (Long requisitionId : requisitionIds) {
            PurchaseRequisition requisition = requisitionRepository.findByIdForUpdate(requisitionId)
                    .orElseThrow(BusinessException::notFound);
            requisition.getLines().forEach(requisitionLine -> linked.stream()
                    .filter(line -> requisitionLine.getId().equals(line.getRequisitionLineId()))
                    .forEach(line -> requisition.addOrdered(requisitionLine.getId(),
                            line.getBaseQuantity().multiply(sign), "lines[" + (line.getLineNo() - 1) + "]")));
            requisitionRepository.save(requisition);
        }
    }

    /** Chỉ hàng quản lý tồn kho có hàng đang về (dịch vụ không qua kho, BR-PUR-16). */
    private void applyIncoming(PurchaseOrder order, Function<PurchaseOrderLine, BigDecimal> quantity,
                               BigDecimal sign) {
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(PurchaseOrderLine::getProductId).toList());
        List<IncomingChange> changes = new ArrayList<>();
        for (PurchaseOrderLine line : order.getLines()) {
            ProductInfo product = products.get(line.getProductId());
            if (product != null && product.stockTracked()) {
                changes.add(new IncomingChange(line.getProductId(), order.getWarehouseId(),
                        quantity.apply(line).multiply(sign)));
            }
        }
        if (!changes.isEmpty()) {
            incomingStock.apply(order.getCompanyId(), changes);
        }
    }
}
