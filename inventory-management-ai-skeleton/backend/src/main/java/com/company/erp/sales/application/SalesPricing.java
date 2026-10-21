package com.company.erp.sales.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.DebtExposureService;
import com.company.erp.finance.application.DebtExposureService.Exposure;
import com.company.erp.finance.application.DebtPostingService.InvoiceSide;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.PriceQueryService;
import com.company.erp.masterdata.application.PriceQueryService.SalesPrice;
import com.company.erp.sales.application.dto.PriceSuggestionResponse;
import com.company.erp.sales.application.dto.SalesLineRequest;
import com.company.erp.sales.domain.SalesPricedLine;
import com.company.erp.sales.domain.SalesPricedLine.PricedValues;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Định giá dòng bán và chính sách thẩm quyền dùng chung cho đơn bán: lấy giá theo thứ tự ưu tiên (BR-SAL-02), giá
 * sàn, chiết khấu đại lý mặc định (BR-SAL-35), thuế suất; lý do cần thẩm quyền khi ghi sổ (D-61): giá sau chiết khấu
 * dưới giá sàn, chiết khấu dòng vượt ngưỡng (BR-SAL-03, D-04), vượt hạn mức tín dụng hoặc có nợ quá hạn (BR-SAL-14,
 * D-22).
 */
@Component
class SalesPricing {

    static final String DISCOUNT_THRESHOLD = "sales.discount-approval-percent";

    private final SalesAccess access;
    private final PriceQueryService prices;
    private final DebtExposureService debtExposure;
    private final ParameterService parameters;

    SalesPricing(SalesAccess access, PriceQueryService prices, DebtExposureService debtExposure,
                 ParameterService parameters) {
        this.access = access;
        this.prices = prices;
        this.debtExposure = debtExposure;
        this.parameters = parameters;
    }

    /** Giá đề xuất cho màn hình lập chứng từ. */
    @Transactional(readOnly = true)
    public PriceSuggestionResponse suggest(CurrentUser actor, Long customerId, Long productId, Long unitId,
                                           BigDecimal quantity, LocalDate date) {
        ProductInfo product = access.productOf(actor, productId);
        CustomerProfile customer = customerId == null ? null
                : access.customer(actor, product.companyId(), customerId, "customerId");
        SalesPrice price = prices.salesPrice(product.companyId(), customerId, productId, unitId, quantity, date);
        return new PriceSuggestionResponse(price.unitPrice(), price.source() == null ? null : price.source().name(),
                price.priceListCode(), price.floorPrice(),
                customer == null ? null : customer.agentDiscountPercent(), product.defaultTaxRateId());
    }

    /** Giá trị dòng từ yêu cầu: kiểm tra hàng, đơn vị, thuế; bổ sung giá / chiết khấu / thuế mặc định. */
    PricedValues line(Long companyId, CustomerProfile customer, SalesLineRequest line, LocalDate date, String field) {
        ProductInfo product = access.product(companyId, line.productId(), field + ".productId");
        BigDecimal factor = access.conversionFactor(line.productId(), line.unitId(), date, field + ".unitId");
        BigDecimal base = SalesAccess.baseQuantity(line.quantity(), factor, field + ".quantity");
        SalesPrice price = prices.salesPrice(companyId, customer.id(), line.productId(), line.unitId(),
                line.quantity(), date);
        boolean promotional = Boolean.TRUE.equals(line.promotional());
        BigDecimal unitPrice = line.unitPrice() != null ? line.unitPrice() : price.unitPrice();
        if (unitPrice == null && !promotional) {
            throw BusinessException.invalidField(field + ".unitPrice", "Hàng " + product.code()
                    + " chưa có giá trong bảng giá áp dụng; nhập đơn giá.");
        }
        BigDecimal discount = line.discountPercent() != null ? line.discountPercent()
                : customer.agentDiscountPercent() != null ? customer.agentDiscountPercent() : BigDecimal.ZERO;
        Long taxRateId = line.taxRateId() != null ? line.taxRateId() : product.defaultTaxRateId();
        TaxRateInfo tax = taxRateId == null ? null : access.taxRate(companyId, taxRateId, date, field + ".taxRateId");
        return new PricedValues(product.id(), line.unitId(), factor, line.quantity(), base, price.unitPrice(),
                price.floorPrice(), price.source() == null ? null : price.source().name(),
                unitPrice == null ? BigDecimal.ZERO : unitPrice, discount, tax == null ? null : tax.id(),
                tax == null ? null : tax.rate(), promotional, SalesAccess.trim(line.description()));
    }

    /** Lý do cần thẩm quyền giá (BR-SAL-03): dưới giá sàn, chiết khấu dòng vượt ngưỡng tham số. */
    List<String> priceReasons(Long companyId, List<? extends SalesPricedLine> lines) {
        BigDecimal threshold = parameters.decimalValue(companyId, DISCOUNT_THRESHOLD);
        List<String> reasons = new ArrayList<>();
        for (SalesPricedLine line : lines) {
            if (line.belowFloor()) {
                reasons.add("Dòng " + line.getLineNo() + ": giá sau chiết khấu " + plain(line.netUnitPrice())
                        + " dưới giá sàn " + plain(line.getFloorPrice()) + ".");
            }
            if (!line.isPromotional() && line.getDiscountPercent().compareTo(threshold) > 0) {
                reasons.add("Dòng " + line.getLineNo() + ": chiết khấu " + plain(line.getDiscountPercent())
                        + "% vượt ngưỡng " + plain(threshold) + "%.");
            }
        }
        return reasons;
    }

    /** Lý do cần thẩm quyền tín dụng (BR-SAL-14, D-22): công nợ hiện tại + giá trị đơn vượt hạn mức, hoặc có nợ quá hạn. */
    List<String> creditReasons(Long companyId, CustomerProfile customer, BigDecimal orderTotal, LocalDate today) {
        Exposure exposure = debtExposure.exposure(companyId, InvoiceSide.RECEIVABLE, customer.id(), today);
        List<String> reasons = new ArrayList<>();
        if (customer.creditLimit() != null
                && exposure.outstanding().add(orderTotal).compareTo(customer.creditLimit()) > 0) {
            reasons.add("Công nợ " + plain(exposure.outstanding()) + " + giá trị đơn " + plain(orderTotal)
                    + " vượt hạn mức tín dụng " + plain(customer.creditLimit()) + ".");
        }
        if (exposure.overdue().signum() > 0) {
            reasons.add("Khách còn nợ quá hạn " + plain(exposure.overdue()) + ".");
        }
        return reasons;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
