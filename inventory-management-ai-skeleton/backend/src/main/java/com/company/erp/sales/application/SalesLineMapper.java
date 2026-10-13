package com.company.erp.sales.application;

import com.company.erp.masterdata.application.MasterDataQueryService.ProductDocumentInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.DeliveryItemResponse;
import com.company.erp.sales.application.dto.SalesLineResponse;
import com.company.erp.sales.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.util.List;

/** Chuyển dòng đơn bán sang DTO (nội bộ module). */
final class SalesLineMapper {

    private SalesLineMapper() {
    }

    static SalesLineResponse toResponse(SalesOrderLine line, ProductInfo product, ProductDocumentInfo document,
                                        BigDecimal available, BigDecimal reserved) {
        List<DeliveryItemResponse> items = line.getItems().stream()
                .map(item -> new DeliveryItemResponse(item.getLotNo(), item.getExpiryDate(), item.getSerialNo(),
                        item.getQualityStatus(), item.getBaseQuantity()))
                .toList();
        return new SalesLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                product == null ? null : product.code(), product == null ? null : product.name(), line.getUnitId(),
                product == null ? null : product.baseUnitId(), line.getConversionFactor(), line.getQuantity(),
                line.getBaseQuantity(), line.getListPrice(), line.getFloorPrice(), line.getPriceSource(),
                line.getUnitPrice(), line.getDiscountPercent(), line.getDiscountAmount(), line.getAmount(),
                line.getTaxRateId(), line.getTaxRate(), line.getTaxAmount(), line.isPromotional(),
                line.isServiceLine(), line.getExpectedDate(), line.getLotNo(), line.getLotOverrideReason(),
                List.copyOf(line.getSerialNumbers()), items, document == null ? null : document.originCountryCode(),
                document == null ? null : document.model(), document == null ? null : document.registrationNo(),
                line.getReturnedQuantity(), line.returnableQuantity(), line.getAdjustedAmount(),
                line.remainingAmount(), available, reserved, line.getDescription());
    }
}
