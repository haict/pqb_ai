package com.company.erp.sales.application;

import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.SalesLineResponse;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.domain.SalesPricedLine;
import java.math.BigDecimal;

/** Chuyển dòng báo giá / đơn bán sang DTO (nội bộ module). */
final class SalesLineMapper {

    private SalesLineMapper() {
    }

    static SalesLineResponse toResponse(SalesPricedLine line, ProductInfo product, BigDecimal available,
                                        BigDecimal reserved) {
        SalesOrderLine order = line instanceof SalesOrderLine orderLine ? orderLine : null;
        return new SalesLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                product == null ? null : product.code(), product == null ? null : product.name(), line.getUnitId(),
                product == null ? null : product.baseUnitId(), line.getConversionFactor(), line.getQuantity(),
                line.getBaseQuantity(), line.getListPrice(), line.getFloorPrice(), line.getPriceSource(),
                line.getUnitPrice(), line.getDiscountPercent(), line.getDiscountAmount(), line.getAmount(),
                line.getTaxRateId(), line.getTaxRate(), line.getTaxAmount(), line.isPromotional(),
                order != null && order.isServiceLine(), order == null ? null : order.getExpectedDate(),
                order == null ? null : order.getIssuedQuantity(), order == null ? null : order.getDeliveredQuantity(),
                order == null ? null : order.getInvoicedQuantity(), order == null ? null : order.getReturnedQuantity(),
                order == null ? null : order.getClosedQuantity(), order == null ? null : order.outstandingQuantity(),
                available, reserved, line.getDescription());
    }
}
