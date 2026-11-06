package com.company.erp.sales.application;

import com.company.erp.accounting.application.JournalQueryService;
import com.company.erp.accounting.application.JournalQueryService.SourceAccounts;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.StockOutService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.sales.application.dto.PrintCompanyResponse;
import com.company.erp.sales.application.dto.StockOutSlipResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.CompanyRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mẫu in phiếu xuất kho từ đơn bán (BR-SAL-21, BR-UI); quyền xem đơn bán. Đơn chưa ghi sổ (trừ đã hủy) in bản nháp
 * gửi khách (D-71): chưa có số phiếu, tài khoản; lô / hạn dùng / serial theo chỉ định trên dòng (trống: kho chọn FEFO
 * khi ghi sổ). Đầu phiếu theo công ty người in
 * chọn (D-58): công ty đang hoạt động trong phạm vi người dùng, mặc định công ty của đơn; in theo công ty khác ghi nhật
 * ký.
 */
@Service
public class SalesOrderSlipService {

    static final String PRINT_AS_COMPANY = "PRINT_AS_COMPANY";

    private final SalesOrderRepository repository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final StockOutService stockOut;
    private final OrganizationQueryService organization;
    private final JournalQueryService journals;
    private final SalesReceiptSourceService receiptSources;

    @SuppressWarnings("java:S107")
    public SalesOrderSlipService(SalesOrderRepository repository, SalesDocumentFlow flow, SalesAccess access,
                                 StockOutService stockOut, OrganizationQueryService organization,
                                 JournalQueryService journals, SalesReceiptSourceService receiptSources) {
        this.repository = repository;
        this.flow = flow;
        this.access = access;
        this.stockOut = stockOut;
        this.organization = organization;
        this.journals = journals;
        this.receiptSources = receiptSources;
    }

    /** Công ty được chọn để in đầu phiếu: đang hoạt động, trong phạm vi người dùng. */
    @Transactional(readOnly = true)
    public List<PrintCompanyResponse> printCompanies(CurrentUser actor) {
        access.requirePermission(actor, SalesOrderService.VIEW);
        return organization.activeCompanies(actor.dataScope().companyIds()).stream()
                .map(company -> new PrintCompanyResponse(company.id(), company.code(), company.name(),
                        company.address()))
                .toList();
    }

    /** {@code printCompanyId} trống: đầu phiếu theo công ty của đơn. */
    @Transactional
    public StockOutSlipResponse stockOutSlip(CurrentUser actor, Long id, Long printCompanyId) {
        access.requirePermission(actor, SalesOrderService.VIEW);
        SalesOrder order = flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound));
        if (order.getStatus() == SalesDocumentStatus.CANCELLED || order.getStatus() == SalesDocumentStatus.REVERSED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + order.getDocumentNo()
                    + " đã hủy hoặc đã đảo nên không in phiếu xuất kho.");
        }
        boolean draft = order.getStatus() != SalesDocumentStatus.POSTED;
        List<SalesOrderLine> goods = order.getLines().stream().filter(line -> !line.isServiceLine()).toList();
        CompanyRef company = printCompany(actor, order, printCompanyId);
        PartnerInfo customer = access.partners(List.of(order.getCustomerId())).get(order.getCustomerId());
        WarehouseInfo warehouse = order.getWarehouseId() == null ? null
                : access.warehouses(List.of(order.getWarehouseId())).get(order.getWarehouseId());
        List<String> debitAccounts = List.of();
        List<String> creditAccounts = List.of();
        if (!draft) {
            SourceAccounts accounts = journals.accountsBySource(order.getCompanyId(), SalesOrder.TYPE_CODE,
                    order.getId(), goods.stream().map(SalesOrderLine::getId).collect(Collectors.toSet()));
            debitAccounts = accounts.debitCodes();
            creditAccounts = accounts.creditCodes();
        }
        String slipNo = draft || order.getInventoryDocumentId() == null ? null
                : stockOut.documentNumbers(Set.of(order.getInventoryDocumentId())).get(order.getInventoryDocumentId());
        LocalDate slipDate = order.getPostingDate() != null ? order.getPostingDate() : order.getDocumentDate();
        List<StockOutSlipResponse.Line> lines = lines(goods, draft);
        BigDecimal total = lines.stream().map(StockOutSlipResponse.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = goods.stream().map(SalesOrderLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> taxRates = goods.stream().map(SalesOrderLine::getTaxRate).filter(Objects::nonNull)
                .map(BigDecimal::stripTrailingZeros).distinct().sorted().toList();
        String address = order.getDeliveryAddress() != null ? order.getDeliveryAddress()
                : customer == null ? null : customer.address();
        return new StockOutSlipResponse(company == null ? null : company.name(),
                company == null ? null : company.address(), order.getId(), order.getDocumentNo(),
                order.getDocumentDate(), slipNo, slipDate, debitAccounts, creditAccounts, customer == null ? null : customer.name(), address,
                customer == null ? null : customer.taxCode(), order.getReceiverName(), description(order, customer),
                order.getEinvoiceSeries(), order.getEinvoiceNo(), order.getEinvoiceDate(),
                warehouse == null ? null : warehouse.code(), warehouse == null ? null : warehouse.name(), lines, total,
                taxRates, tax, total.add(tax), draft);
    }

    private CompanyRef printCompany(CurrentUser actor, SalesOrder order, Long printCompanyId) {
        if (printCompanyId == null || printCompanyId.equals(order.getCompanyId())) {
            return organization.company(order.getCompanyId()).orElse(null);
        }
        CompanyRef company = organization.activeCompanies(actor.dataScope().companyIds()).stream()
                .filter(candidate -> candidate.id().equals(printCompanyId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField("companyId",
                        "Công ty in phiếu không tồn tại, đã ngừng hoạt động hoặc ngoài phạm vi của bạn."));
        access.audit(actor, PRINT_AS_COMPANY, SalesOrderService.ENTITY, order.getId(), order.getDocumentNo(),
                "In phiếu xuất kho theo công ty " + company.code() + " - " + company.name(),
                Map.of("companyId", order.getCompanyId()), Map.of("printCompanyId", company.id()),
                order.getCompanyId(), order.getBranchId());
        return company;
    }

    private List<StockOutSlipResponse.Line> lines(List<SalesOrderLine> goods, boolean draft) {
        Map<Long, ProductInfo> products = access.products(goods.stream().map(SalesOrderLine::getProductId).toList());
        Map<Long, LocalDate> expiries = draft ? receiptSources.expiryDates(goods) : Map.of();
        List<StockOutSlipResponse.Line> lines = new ArrayList<>();
        for (SalesOrderLine line : goods) {
            ProductInfo product = products.get(line.getProductId());
            List<StockOutSlipResponse.Item> items = draft ? plannedItems(line, expiries.get(line.getId()))
                    : line.getItems().stream()
                    .map(item -> new StockOutSlipResponse.Item(item.getLotNo(), item.getExpiryDate(),
                            item.getSerialNo(), item.getBaseQuantity()))
                    .toList();
            lines.add(new StockOutSlipResponse.Line(lines.size() + 1, product == null ? null : product.code(),
                    product == null ? null : product.name(), line.getUnitId(), line.getQuantity(),
                    line.netUnitPrice(), line.getAmount(), items));
        }
        return lines;
    }

    /** Bản nháp: lô / serial chỉ định trên dòng; không chỉ định thì trống (kho chọn khi ghi sổ). */
    private static List<StockOutSlipResponse.Item> plannedItems(SalesOrderLine line, LocalDate expiry) {
        if (!line.getSerialNumbers().isEmpty()) {
            return line.getSerialNumbers().stream()
                    .map(serial -> new StockOutSlipResponse.Item(line.getLotNo(), expiry, serial, BigDecimal.ONE))
                    .toList();
        }
        if (line.getLotNo() != null) {
            return List.of(new StockOutSlipResponse.Item(line.getLotNo(), expiry, null, line.getBaseQuantity()));
        }
        return List.of();
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
