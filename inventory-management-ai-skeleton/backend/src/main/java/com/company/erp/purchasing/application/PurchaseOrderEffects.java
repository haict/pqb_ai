package com.company.erp.purchasing.application;

import com.company.erp.inventory.application.IncomingStockService;
import com.company.erp.inventory.application.IncomingStockService.IncomingChange;
import com.company.erp.purchasing.domain.PurchaseOrder;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hàng đang về do đơn mua thay đổi (BR mục 5.4), trong transaction của đơn mua: duyệt cộng, hủy đơn đã duyệt / ghi sổ
 * trừ. Chỉ dòng hàng hóa (dịch vụ không qua kho, BR-PUR-16). Thứ tự khóa: đơn mua (đã khóa) → tổng tồn theo (hàng,
 * kho).
 */
@Component
class PurchaseOrderEffects {

    private final IncomingStockService incomingStock;

    PurchaseOrderEffects(IncomingStockService incomingStock) {
        this.incomingStock = incomingStock;
    }

    /** Đơn mua chuyển APPROVED: cộng hàng đang về. */
    void approved(PurchaseOrder order) {
        applyIncoming(order, BigDecimal.ONE);
    }

    /** Hủy đơn đã duyệt, hoặc ghi sổ (hàng đã nhập kho): trừ hàng đang về. */
    void released(PurchaseOrder order) {
        applyIncoming(order, BigDecimal.ONE.negate());
    }

    private void applyIncoming(PurchaseOrder order, BigDecimal sign) {
        List<IncomingChange> changes = order.getLines().stream().filter(line -> !line.isServiceLine())
                .map(line -> new IncomingChange(line.getProductId(), order.getWarehouseId(),
                        line.getBaseQuantity().multiply(sign)))
                .toList();
        if (!changes.isEmpty()) {
            incomingStock.apply(order.getCompanyId(), changes);
        }
    }
}
