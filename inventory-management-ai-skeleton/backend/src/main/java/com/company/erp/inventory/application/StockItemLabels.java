package com.company.erp.inventory.application;

import com.company.erp.masterdata.application.MasterDataQueryService;
import org.springframework.stereotype.Component;

/** Tên hiển thị hàng / kho cho thông báo lỗi: người dùng đọc mã - tên thay vì ID nội bộ. */
@Component
class StockItemLabels {

    private final MasterDataQueryService masterData;

    StockItemLabels(MasterDataQueryService masterData) {
        this.masterData = masterData;
    }

    String product(Long productId) {
        return masterData.product(productId)
                .map(product -> product.code() + " - " + product.name())
                .orElse("#" + productId);
    }

    String warehouse(Long warehouseId) {
        return masterData.warehouse(warehouseId)
                .map(warehouse -> warehouse.code() + " - " + warehouse.name())
                .orElse("#" + warehouseId);
    }
}
