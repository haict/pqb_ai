package com.company.erp.purchasing.application;

import com.company.erp.accounting.application.JournalQueryService;
import com.company.erp.accounting.application.JournalQueryService.SourceAccounts;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.purchasing.application.dto.PrintCompanyResponse;
import com.company.erp.purchasing.application.dto.StockInSlipResponse;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
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

/**
 * Mẫu in phiếu nhập kho từ đơn mua đã ghi sổ (BR-PUR-08); quyền xem đơn mua. Đầu phiếu theo công ty người in chọn
 * (D-58): công ty đang hoạt động trong phạm vi người dùng, mặc định công ty của đơn; in theo công ty khác ghi nhật ký.
 */
@Service
public class PurchaseOrderSlipService {

    static final String PRINT_AS_COMPANY = "PRINT_AS_COMPANY";

    private final PurchaseOrderRepository repository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final StockInService stockIn;
    private final OrganizationQueryService organization;
    private final JournalQueryService journals;

    public PurchaseOrderSlipService(PurchaseOrderRepository repository, PurchaseDocumentFlow flow,
                                    PurchasingAccess access, StockInService stockIn,
                                    OrganizationQueryService organization, JournalQueryService journals) {
        this.repository = repository;
        this.flow = flow;
        this.access = access;
        this.stockIn = stockIn;
        this.organization = organization;
        this.journals = journals;
    }

    /** Công ty được chọn để in đầu phiếu: đang hoạt động, trong phạm vi người dùng. */
    @Transactional(readOnly = true)
    public List<PrintCompanyResponse> printCompanies(CurrentUser actor) {
        access.requirePermission(actor, PurchaseOrderService.VIEW);
        return organization.activeCompanies(actor.dataScope().companyIds()).stream()
                .map(company -> new PrintCompanyResponse(company.id(), company.code(), company.name(),
                        company.address()))
                .toList();
    }

    /** {@code printCompanyId} trống: đầu phiếu theo công ty của đơn. */
    @Transactional
    public StockInSlipResponse stockInSlip(CurrentUser actor, Long id, Long printCompanyId) {
        access.requirePermission(actor, PurchaseOrderService.VIEW);
        PurchaseOrder order = flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound));
        if (order.getStatus() != PurchaseDocumentStatus.POSTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn mua " + order.getDocumentNo()
                    + " chưa ghi sổ (hoặc đã đảo) nên không có phiếu nhập kho để in.");
        }
        List<PurchaseOrderLine> goods = order.getLines().stream().filter(line -> !line.isServiceLine()).toList();
        CompanyRef company = printCompany(actor, order, printCompanyId);
        PartnerInfo supplier = access.partners(List.of(order.getSupplierId())).get(order.getSupplierId());
        WarehouseInfo warehouse = access.warehouses(List.of(order.getWarehouseId())).get(order.getWarehouseId());
        SourceAccounts accounts = journals.accountsBySource(order.getCompanyId(), PurchaseOrder.TYPE_CODE,
                order.getId(), goods.stream().map(PurchaseOrderLine::getId).collect(Collectors.toSet()));
        String slipNo = order.getInventoryDocumentId() == null ? null
                : stockIn.documentNumbers(Set.of(order.getInventoryDocumentId())).get(order.getInventoryDocumentId());
        List<StockInSlipResponse.Line> lines = lines(goods);
        BigDecimal total = lines.stream().map(StockInSlipResponse.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = goods.stream().map(PurchaseOrderLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> taxRates = goods.stream().map(PurchaseOrderLine::getTaxRate).filter(Objects::nonNull)
                .map(BigDecimal::stripTrailingZeros).distinct().sorted().toList();
        return new StockInSlipResponse(company == null ? null : company.name(),
                company == null ? null : company.address(), order.getId(), order.getDocumentNo(), slipNo,
                order.getPostingDate(), accounts.debitCodes(), accounts.creditCodes(),
                supplier == null ? null : supplier.name(), supplier == null ? null : supplier.address(),
                supplier == null ? null : supplier.taxCode(), description(order, supplier),
                order.getInvoiceSeries(), order.getInvoiceNo(), order.getInvoiceDate(),
                warehouse == null ? null : warehouse.code(), warehouse == null ? null : warehouse.name(), lines, total,
                taxRates, tax, total.add(tax));
    }

    private CompanyRef printCompany(CurrentUser actor, PurchaseOrder order, Long printCompanyId) {
        if (printCompanyId == null || printCompanyId.equals(order.getCompanyId())) {
            return organization.company(order.getCompanyId()).orElse(null);
        }
        CompanyRef company = organization.activeCompanies(actor.dataScope().companyIds()).stream()
                .filter(candidate -> candidate.id().equals(printCompanyId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField("companyId",
                        "Công ty in phiếu không tồn tại, đã ngừng hoạt động hoặc ngoài phạm vi của bạn."));
        access.audit(actor, PRINT_AS_COMPANY, PurchaseOrderService.ENTITY, order.getId(), order.getDocumentNo(),
                "In phiếu nhập kho theo công ty " + company.code() + " - " + company.name(),
                Map.of("companyId", order.getCompanyId()), Map.of("printCompanyId", company.id()),
                order.getCompanyId(), order.getBranchId());
        return company;
    }

    private List<StockInSlipResponse.Line> lines(List<PurchaseOrderLine> goods) {
        Map<Long, ProductInfo> products = access.products(goods.stream().map(PurchaseOrderLine::getProductId)
                .toList());
        List<StockInSlipResponse.Line> lines = new ArrayList<>();
        for (PurchaseOrderLine line : goods) {
            ProductInfo product = products.get(line.getProductId());
            lines.add(new StockInSlipResponse.Line(lines.size() + 1, product == null ? null : product.code(),
                    product == null ? null : product.name(), line.getLotNo(), line.getExpiryDate(), line.getUnitId(),
                    line.getQuantity(), line.netUnitPrice(), line.netAmount()));
        }
        return lines;
    }

    /** Diễn giải của đơn; trống thì "Mua hàng của <NCC> theo hóa đơn <số>". */
    private static String description(PurchaseOrder order, PartnerInfo supplier) {
        if (order.getDescription() != null) {
            return order.getDescription();
        }
        String text = "Mua hàng của " + (supplier == null ? "" : supplier.name());
        return order.getInvoiceNo() == null ? text : text + " theo hóa đơn " + order.getInvoiceNo();
    }
}
