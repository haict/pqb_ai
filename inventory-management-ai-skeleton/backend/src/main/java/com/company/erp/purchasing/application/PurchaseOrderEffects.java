package com.company.erp.purchasing.application;

import com.company.erp.inventory.application.IncomingStockService;
import com.company.erp.inventory.application.IncomingStockService.IncomingChange;
import com.company.erp.purchasing.domain.PurchaseOrder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hàng đang về do đơn mua thay đổi (BR mục 5.4), trong transaction của đơn mua. Từ D-61 đơn mua không qua duyệt, ghi sổ
 * là nhập kho ngay nên không cộng hàng đang về nữa; chỉ trừ phần đã cộng của đơn đã duyệt trước D-61 khi hủy / ghi sổ.
 * Chỉ dòng hàng hóa (dịch vụ không qua kho, BR-PUR-16). Thứ tự khóa: đơn mua (đã khóa) → tổng tồn theo (hàng, kho).
 */
@Component
class PurchaseOrderEffects {

    private final IncomingStockService incomingStock;

    PurchaseOrderEffects(IncomingStockService incomingStock) {
        this.incomingStock = incomingStock;
    }

    /** Hủy hoặc ghi sổ đơn đã duyệt trước D-61: trừ hàng đang về đã cộng lúc duyệt. */
    void released(PurchaseOrder order) {
        List<IncomingChange> changes = order.getLines().stream().filter(line -> !line.isServiceLine())
                .map(line -> new IncomingChange(line.getProductId(), order.getWarehouseId(),
                        line.getBaseQuantity().negate()))
                .toList();
        if (!changes.isEmpty()) {
            incomingStock.apply(order.getCompanyId(), changes);
        }
    }
}
