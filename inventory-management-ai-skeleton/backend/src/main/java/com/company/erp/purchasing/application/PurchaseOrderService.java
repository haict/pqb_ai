package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.purchasing.application.dto.DeletedOrderResponse;
import com.company.erp.purchasing.application.dto.PurchaseOrderLineRequest;
import com.company.erp.purchasing.application.dto.PurchaseOrderLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseOrderRequest;
import com.company.erp.purchasing.application.dto.PurchaseOrderResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đơn mua hàng (DMH, BR-PUR-04, D-54): lập, sửa, xóa đơn chưa từng ghi sổ (D-61 không qua duyệt, D-65 bỏ hủy). Xóa đơn
 * đã duyệt trước D-61 trừ hàng đang về trong cùng transaction. Ghi sổ, đảo và kiểm soát ngưỡng giá trị ở {@link PurchaseOrderPostingService}.
 */
@Service
public class PurchaseOrderService {

    static final String PATH = "/api/v1/purchase-orders";
    static final String VIEW = "purchase-order:view";
    static final String CREATE = "purchase-order:create";
    static final String ENTITY = "PURCHASE_ORDER";
    static final String DOMESTIC_CURRENCY = "VND";

    public record OrderFilter(Long companyId, Long branchId, PurchaseDocumentStatus status, Long supplierId,
                              Long warehouseId, LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final PurchaseOrderRepository repository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final StockInService stockIn;
    private final PurchaseDocumentFlow.Spec<PurchaseOrder, PurchaseOrderResponse> spec;

    public PurchaseOrderService(PurchaseOrderRepository repository, PurchaseDocumentFlow flow,
                                PurchasingAccess access, PurchaseOrderEffects effects,
                                ApprovalService approvalService, StockInService stockIn) {
        this.repository = repository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.stockIn = stockIn;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), PurchaseOrderResponse.class, effects::released);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> search(CurrentUser actor, OrderFilter filter, Pageable pageable) {
        Page<PurchaseOrder> page = flow.search(actor, VIEW, repository,
                new PurchaseDocumentFlow.Filter<PurchaseOrder>(filter.companyId(), filter.branchId(), filter.status(),
                        filter.dateFrom(), filter.dateTo(), filter.keyword(), (root, query, cb) -> cb.and(
                        filter.supplierId() == null ? cb.conjunction()
                                : cb.equal(root.get("supplierId"), filter.supplierId()),
                        filter.warehouseId() == null ? cb.conjunction()
                                : cb.equal(root.get("warehouseId"), filter.warehouseId()))),
                pageable);
        Map<Long, PartnerInfo> suppliers = access.partners(page.getContent().stream()
                .map(PurchaseOrder::getSupplierId).toList());
        Map<Long, WarehouseInfo> warehouses = access.warehouses(page.getContent().stream()
                .map(PurchaseOrder::getWarehouseId).toList());
        Map<Long, String> inventoryNumbers = inventoryNumbers(page.getContent());
        return page.map(document -> toResponse(document, suppliers.get(document.getSupplierId()),
                warehouses.get(document.getWarehouseId()), inventoryNumbers.get(document.getInventoryDocumentId()),
                false));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public PurchaseOrderResponse create(CurrentUser actor, String idempotencyKey, PurchaseOrderRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseOrderResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            PurchaseOrder document = new PurchaseOrder(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), PurchaseOrder.TYPE_CODE,
                            request.documentDate()));
            fill(document, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public PurchaseOrderResponse update(CurrentUser actor, Long id, PurchaseOrderRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseOrder document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())) {
            throw BusinessException.invalidField("branchId", "Không đổi chi nhánh sau khi lập.");
        }
        fill(document, request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    /**
     * Xóa hẳn đơn chưa từng ghi sổ (D-65): đóng yêu cầu duyệt cũ còn treo, trừ hàng đang về của đơn đã duyệt trước
     * D-61; audit lưu toàn bộ đơn trước khi xóa.
     */
    @Transactional
    public DeletedOrderResponse delete(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.idempotent(actor, key, "DELETE " + PATH + "/" + id, request, DeletedOrderResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            PurchaseOrder document = flow.lock(actor, id, spec);
            document.requireDeletable();
            String reason = request.reason().trim();
            PurchaseOrderResponse before = toResponse(document, true);
            if (document.getStatus() == PurchaseDocumentStatus.SUBMITTED) {
                approvalService.cancelPending(actor, PurchaseOrder.TYPE_CODE, id, reason);
            }
            if (document.getStatus() == PurchaseDocumentStatus.APPROVED) {
                spec.onCancelled().accept(document);
            }
            repository.delete(document);
            repository.flush();
            access.audit(actor, "DELETE", ENTITY, id, document.getDocumentNo(), reason, before, null,
                    document.getCompanyId(), document.getBranchId());
            return new DeletedOrderResponse(id, document.getDocumentNo());
        });
    }

    PurchaseDocumentFlow.Spec<PurchaseOrder, PurchaseOrderResponse> spec() {
        return spec;
    }

    /** Đơn mua khác còn hiệu lực trùng hóa đơn (MST, ký hiệu, số): 409 {@code DUPLICATE_DOCUMENT} (BR-PUR-09). */
    void requireUniqueInvoice(Long companyId, String duplicateKey, Long documentId, String label) {
        if (duplicateKey == null) {
            return;
        }
        repository.findDuplicate(companyId, duplicateKey, documentId).ifPresent(other -> {
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "Hóa đơn " + label
                    + " đã được nhập ở đơn mua " + other + ".");
        });
    }

    private void fill(PurchaseOrder document, PurchaseOrderRequest request) {
        Long companyId = document.getCompanyId();
        PartnerInfo supplier = access.supplier(companyId, request.supplierId(), "supplierId");
        access.receivingWarehouse(document.getBranchId(), request.warehouseId(), "warehouseId");
        String currency = request.currencyCode() == null ? DOMESTIC_CURRENCY : request.currencyCode();
        if (!DOMESTIC_CURRENCY.equals(currency)) {
            throw BusinessException.invalidField("currencyCode", "Giai đoạn này chỉ hỗ trợ đơn mua bằng VND (D-24).");
        }
        Integer paymentTerm = request.paymentTermDays() != null ? request.paymentTermDays()
                : supplier.supplierPaymentTermDays();
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        PurchaseOrder.Invoice invoice = invoice(supplier, request);
        if (invoice != null) {
            // Kiểm tra trước khi đổi entity: truy vấn sau khi đổi sẽ tự flush và vướng ràng buộc unique.
            requireUniqueInvoice(companyId, PurchaseOrder.duplicateKey(invoice.sellerTaxCode(), invoice.series(),
                    invoice.number()), document.getId(), invoice.series().trim() + "-" + invoice.number().trim());
        }
        LocalDate dueDate = request.dueDate() != null ? request.dueDate()
                : (invoice != null ? invoice.date() : postingDate).plusDays(paymentTerm == null ? 0 : paymentTerm);
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            lines.add(line(document, request, request.lines().get(i), i));
        }
        document.replaceContent(new PurchaseOrder.Header(request.documentDate(), postingDate, supplier.id(),
                request.warehouseId(), request.expectedDate(), paymentTerm, currency, BigDecimal.ONE, invoice, dueDate,
                PurchasingAccess.trim(request.description())), lines);
    }

    /** Hóa đơn của nhà cung cấp: đủ ký hiệu, số, ngày hoặc để trống cả ba; MST người bán theo danh mục nhà cung cấp. */
    private static PurchaseOrder.Invoice invoice(PartnerInfo supplier, PurchaseOrderRequest request) {
        String series = PurchasingAccess.trim(request.invoiceSeries());
        String number = PurchasingAccess.trim(request.invoiceNo());
        if (series == null && number == null && request.invoiceDate() == null) {
            return null;
        }
        if (series == null || number == null || request.invoiceDate() == null) {
            throw BusinessException.invalidField(series == null ? "invoiceSeries" : number == null ? "invoiceNo"
                    : "invoiceDate", "Hóa đơn của nhà cung cấp phải có đủ ký hiệu, số và ngày.");
        }
        String taxCode = PurchasingAccess.trim(supplier.taxCode());
        if (taxCode == null) {
            throw BusinessException.invalidField("supplierId", "Nhà cung cấp " + supplier.code()
                    + " chưa có mã số thuế; bổ sung trong danh mục đối tác.");
        }
        return new PurchaseOrder.Invoice(taxCode, series, number, request.invoiceDate());
    }

    private PurchaseOrderLine line(PurchaseOrder document, PurchaseOrderRequest request, PurchaseOrderLineRequest line,
                                   int index) {
        String field = "lines[" + index + "]";
        Long companyId = document.getCompanyId();
        ProductInfo product = access.product(companyId, line.productId(), field + ".productId");
        BigDecimal factor = access.conversionFactor(line.productId(), line.unitId(), request.documentDate(),
                field + ".unitId");
        BigDecimal base = PurchasingAccess.baseQuantity(line.quantity(), factor, field + ".quantity");
        Long taxRateId = line.taxRateId() != null ? line.taxRateId() : product.defaultTaxRateId();
        TaxRateInfo tax = taxRateId == null ? null
                : access.taxRate(companyId, taxRateId, request.documentDate(), field + ".taxRateId");
        boolean service = !product.stockTracked();
        if (!service) {
            requireTracking(product, line, field);
        }
        List<String> serials = service ? List.of() : serials(product, line, base, field);
        return new PurchaseOrderLine(index + 1, new PurchaseOrderLine.Values(line.productId(), line.unitId(), factor,
                line.quantity(), base, line.unitPrice(), line.discountPercent(), tax == null ? null : tax.id(),
                tax == null ? null : tax.rate(), service, service ? null : line.locationId(),
                service ? null : PurchasingAccess.trim(line.lotNo()), service ? null : line.manufactureDate(),
                service ? null : line.expiryDate(), serials, PurchasingAccess.trim(line.note())));
    }

    /**
     * Kiểm tra lô / hạn dùng theo cấu hình hàng ngay khi lưu đơn (BR-INV-01), cùng quy tắc với phiếu nhập kho
     * sinh lúc ghi sổ, để đơn đã lưu không bị chặn khi ghi sổ.
     */
    private static void requireTracking(ProductInfo product, PurchaseOrderLineRequest line, String field) {
        if (!product.lotTracked()) {
            if (PurchasingAccess.trim(line.lotNo()) != null || line.manufactureDate() != null
                    || line.expiryDate() != null) {
                throw BusinessException.invalidField(field + ".lotNo", "Hàng " + product.code()
                        + " không quản lý lô: bỏ số lô, ngày sản xuất, hạn dùng.");
            }
            return;
        }
        if (PurchasingAccess.trim(line.lotNo()) == null) {
            throw BusinessException.invalidField(field + ".lotNo", "Hàng " + product.code() + " phải nhập số lô.");
        }
        if (product.expiryTracked() && line.expiryDate() == null) {
            throw BusinessException.invalidField(field + ".expiryDate", "Hàng " + product.code()
                    + " phải nhập hạn dùng.");
        }
        if (line.manufactureDate() != null && line.expiryDate() != null
                && !line.expiryDate().isAfter(line.manufactureDate())) {
            throw BusinessException.invalidField(field + ".expiryDate", "Hạn dùng phải sau ngày sản xuất.");
        }
    }

    /** Hàng quản lý serial: mỗi serial một đơn vị cơ sở (BR-INV-02). */
    private static List<String> serials(ProductInfo product, PurchaseOrderLineRequest line, BigDecimal base,
                                        String field) {
        List<String> serials = line.serialNumbers() == null ? List.of()
                : line.serialNumbers().stream().map(String::trim).distinct().toList();
        if (!product.serialTracked()) {
            if (!serials.isEmpty()) {
                throw BusinessException.invalidField(field + ".serialNumbers", "Hàng " + product.code()
                        + " không quản lý serial.");
            }
            return List.of();
        }
        if (base.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Số serial không trùng (" + serials.size()
                    + ") phải bằng số lượng theo đơn vị cơ sở (" + base.stripTrailingZeros().toPlainString() + ").");
        }
        return serials;
    }

    private Map<Long, String> inventoryNumbers(List<PurchaseOrder> documents) {
        List<Long> ids = documents.stream().map(PurchaseOrder::getInventoryDocumentId).filter(Objects::nonNull)
                .toList();
        return ids.isEmpty() ? new HashMap<>() : new HashMap<>(stockIn.documentNumbers(ids));
    }

    PurchaseOrderResponse toResponse(PurchaseOrder document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getSupplierId())).get(document.getSupplierId()),
                access.warehouses(List.of(document.getWarehouseId())).get(document.getWarehouseId()),
                inventoryNumbers(List.of(document)).get(document.getInventoryDocumentId()), detailed);
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder document, PartnerInfo supplier, WarehouseInfo warehouse,
                                             String inventoryNo, boolean detailed) {
        List<PurchaseOrderLineResponse> lines = List.of();
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(PurchaseOrderLine::getProductId).toList());
            lines = document.getLines().stream().map(line -> toResponse(line, products.get(line.getProductId())))
                    .toList();
        }
        return new PurchaseOrderResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(),
                document.getSupplierId(), supplier == null ? null : supplier.code(),
                supplier == null ? null : supplier.name(), document.getWarehouseId(),
                warehouse == null ? null : warehouse.code(), warehouse == null ? null : warehouse.name(),
                document.getExpectedDate(), document.getPaymentTermDays(), document.getCurrencyCode(),
                document.getExchangeRate(), supplier == null ? null : supplier.taxCode(),
                supplier == null ? null : supplier.address(), document.getInvoiceSeries(),
                document.getInvoiceNo(), document.getInvoiceDate(), document.getDueDate(), document.getDescription(),
                document.getAmount(), document.getDiscountAmount(), document.getTaxAmount(), document.getTotalAmount(),
                document.getStatus(), document.getInventoryDocumentId(), inventoryNo, document.getSubmittedAt(),
                document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(), document.getReverseDate(),
                document.getReason(), document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(PurchaseOrder.TYPE_CODE, document.getId()) : List.of());
    }

    private static PurchaseOrderLineResponse toResponse(PurchaseOrderLine line, ProductInfo product) {
        return new PurchaseOrderLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                product == null ? null : product.code(), product == null ? null : product.name(), line.getUnitId(),
                product == null ? null : product.baseUnitId(), line.getConversionFactor(), line.getQuantity(),
                line.getBaseQuantity(), line.getUnitPrice(), line.getDiscountPercent(), line.getDiscountAmount(),
                line.getAmount(), line.getTaxRateId(), line.getTaxRate(), line.getTaxAmount(), line.isServiceLine(),
                line.getLocationId(), line.getLotNo(), line.getManufactureDate(), line.getExpiryDate(),
                List.copyOf(line.getSerialNumbers()), line.getReturnedQuantity(), line.getAdjustedAmount(),
                line.getAllocatedAmount(), line.returnableQuantity(), line.remainingAmount(), line.getNote());
    }
}
