package com.company.erp.sales.application;

import com.company.erp.accounting.application.JournalQueryService;
import com.company.erp.accounting.application.JournalQueryService.SourceAccounts;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.StockOutService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.sales.application.dto.StockOutSlipResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.CompanyRef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mẫu in phiếu xuất kho từ đơn bán đã ghi sổ (BR-SAL-21, BR-UI); chỉ đọc, quyền xem đơn bán. */
@Service
public class SalesOrderSlipService {

    private final SalesOrderRepository repository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final StockOutService stockOut;
    private final OrganizationQueryService organization;
    private final JournalQueryService journals;

    public SalesOrderSlipService(SalesOrderRepository repository, SalesDocumentFlow flow, SalesAccess access,
                                 StockOutService stockOut, OrganizationQueryService organization,
                                 JournalQueryService journals) {
        this.repository = repository;
        this.flow = flow;
        this.access = access;
        this.stockOut = stockOut;
        this.organization = organization;
        this.journals = journals;
    }

    @Transactional(readOnly = true)
    public StockOutSlipResponse stockOutSlip(CurrentUser actor, Long id) {
        access.requirePermission(actor, SalesOrderService.VIEW);
        SalesOrder order = flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound));
        if (order.getStatus() != SalesDocumentStatus.POSTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + order.getDocumentNo()
                    + " chưa ghi sổ (hoặc đã đảo) nên không có phiếu xuất kho để in.");
        }
        List<SalesOrderLine> goods = order.getLines().stream().filter(line -> !line.isServiceLine()).toList();
        CompanyRef company = organization.company(order.getCompanyId()).orElse(null);
        PartnerInfo customer = access.partners(List.of(order.getCustomerId())).get(order.getCustomerId());
        WarehouseInfo warehouse = order.getWarehouseId() == null ? null
                : access.warehouses(List.of(order.getWarehouseId())).get(order.getWarehouseId());
        SourceAccounts accounts = journals.accountsBySource(order.getCompanyId(), SalesOrder.TYPE_CODE,
                order.getId(), goods.stream().map(SalesOrderLine::getId).collect(Collectors.toSet()));
        String slipNo = order.getInventoryDocumentId() == null ? null
                : stockOut.documentNumbers(Set.of(order.getInventoryDocumentId())).get(order.getInventoryDocumentId());
        List<StockOutSlipResponse.Line> lines = lines(goods);
        BigDecimal total = lines.stream().map(StockOutSlipResponse.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = goods.stream().map(SalesOrderLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> taxRates = goods.stream().map(SalesOrderLine::getTaxRate).filter(Objects::nonNull)
                .map(BigDecimal::stripTrailingZeros).distinct().sorted().toList();
        String address = order.getDeliveryAddress() != null ? order.getDeliveryAddress()
                : customer == null ? null : customer.address();
        return new StockOutSlipResponse(company == null ? null : company.name(),
                company == null ? null : company.address(), order.getId(), order.getDocumentNo(),
                order.getDocumentDate(), slipNo, order.getPostingDate(), accounts.debitCodes(),
                accounts.creditCodes(), customer == null ? null : customer.name(), address,
                customer == null ? null : customer.taxCode(), order.getReceiverName(), description(order, customer),
                order.getEinvoiceSeries(), order.getEinvoiceNo(), order.getEinvoiceDate(),
                warehouse == null ? null : warehouse.code(), warehouse == null ? null : warehouse.name(), lines, total,
                taxRates, tax, total.add(tax));
    }

    private List<StockOutSlipResponse.Line> lines(List<SalesOrderLine> goods) {
        Map<Long, ProductInfo> products = access.products(goods.stream().map(SalesOrderLine::getProductId).toList());
        List<StockOutSlipResponse.Line> lines = new ArrayList<>();
        for (SalesOrderLine line : goods) {
            ProductInfo product = products.get(line.getProductId());
            List<StockOutSlipResponse.Item> items = line.getItems().stream()
                    .map(item -> new StockOutSlipResponse.Item(item.getLotNo(), item.getExpiryDate(),
                            item.getSerialNo(), item.getBaseQuantity()))
                    .toList();
            lines.add(new StockOutSlipResponse.Line(lines.size() + 1, product == null ? null : product.code(),
                    product == null ? null : product.name(), line.getUnitId(), line.getQuantity(),
                    line.netUnitPrice(), line.getAmount(), items));
        }
        return lines;
    }

    /** Diễn giải của đơn; trống thì "Xuất kho bán hàng cho <khách> theo đơn <số>". */
    private static String description(SalesOrder order, PartnerInfo customer) {
        if (order.getDescription() != null) {
            return order.getDescription();
        }
        return "Xuất kho bán hàng cho " + (customer == null ? "" : customer.name()) + " theo đơn "
                + order.getDocumentNo();
    }
}
