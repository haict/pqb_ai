package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.PurchaseReturnLineRequest;
import com.company.erp.purchasing.application.dto.PurchaseReturnLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseReturnRequest;
import com.company.erp.purchasing.application.dto.PurchaseReturnResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.application.dto.ReturnableLineResponse;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.domain.PurchaseReturn;
import com.company.erp.purchasing.domain.PurchaseReturnLine;
import com.company.erp.purchasing.domain.PurchaseReturnType;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReturnRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trả hàng nhà cung cấp (THN) / giảm giá hàng mua (GGM), BR-PUR-14: lập theo dòng của một đơn mua đã ghi sổ (đúng lô,
 * serial, đơn giá sau chiết khấu), sửa, hủy (D-61 không qua duyệt). Ghi sổ, đảo ở
 * {@link PurchaseReturnPostingService}.
 */
@Service
public class PurchaseReturnService {

    static final String PATH = "/api/v1/purchase-returns";
    static final String VIEW = "purchase-return:view";
    static final String CREATE = "purchase-return:create";
    static final String ENTITY = "PURCHASE_RETURN";

    public record ReturnFilter(Long companyId, Long branchId, PurchaseReturnType documentType,
                               PurchaseDocumentStatus status, Long supplierId, LocalDate dateFrom, LocalDate dateTo,
                               String keyword) {
    }

    private final PurchaseReturnRepository repository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final StockInService stockIn;
    private final PurchaseDocumentFlow.Spec<PurchaseReturn, PurchaseReturnResponse> spec;

    @SuppressWarnings("java:S107")
    public PurchaseReturnService(PurchaseReturnRepository repository, PurchaseOrderRepository orderRepository,
                                 PurchaseDocumentFlow flow, PurchasingAccess access, ApprovalService approvalService,
                                 StockInService stockIn) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.stockIn = stockIn;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), PurchaseReturnResponse.class, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<PurchaseReturnResponse> search(CurrentUser actor, ReturnFilter filter, Pageable pageable) {
        Page<PurchaseReturn> page = flow.search(actor, VIEW, repository,
                new PurchaseDocumentFlow.Filter<PurchaseReturn>(filter.companyId(), filter.branchId(),
                        filter.status(), filter.dateFrom(), filter.dateTo(), filter.keyword(),
                        (root, query, cb) -> cb.and(
                                filter.supplierId() == null ? cb.conjunction()
                                        : cb.equal(root.get("supplierId"), filter.supplierId()),
                                filter.documentType() == null ? cb.conjunction()
                                        : cb.equal(root.get("documentType"), filter.documentType()))),
                pageable);
        Map<Long, PartnerInfo> suppliers = access.partners(page.getContent().stream()
                .map(PurchaseReturn::getSupplierId).toList());
        return page.map(document -> toResponse(document, suppliers.get(document.getSupplierId()), false));
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Dòng còn trả / giảm giá được của đơn mua đã ghi sổ {@code orderId} (cùng chi nhánh, nhà cung cấp). */
    @Transactional(readOnly = true)
    public List<ReturnableLineResponse> returnableLines(CurrentUser actor, Long branchId, Long supplierId,
                                                        Long orderId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        PurchaseOrder order = postedOrder(branch, supplierId, orderId);
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(PurchaseOrderLine::getProductId).toList());
        List<ReturnableLineResponse> rows = new ArrayList<>();
        for (PurchaseOrderLine line : order.getLines()) {
            if (line.returnableQuantity().signum() > 0 || line.remainingAmount().signum() > 0) {
                rows.add(row(order, line, products.get(line.getProductId())));
            }
        }
        return rows;
    }

    @Transactional
    public PurchaseReturnResponse create(CurrentUser actor, String idempotencyKey, PurchaseReturnRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseReturnResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            PartnerInfo supplier = access.supplier(branch.companyId(), request.supplierId(), "supplierId");
            PurchaseOrder order = postedOrder(branch, supplier.id(), request.purchaseOrderId());
            PurchaseReturn document = new PurchaseReturn(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), request.documentType().name(),
                            request.documentDate()), request.documentType(), supplier.id(), order.getId(),
                    request.documentType() == PurchaseReturnType.THN ? order.getWarehouseId() : null);
            fill(document, order, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public PurchaseReturnResponse update(CurrentUser actor, Long id, PurchaseReturnRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseReturn document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId()) || !document.getSupplierId().equals(request.supplierId())
                || document.getDocumentType() != request.documentType()
                || !document.getPurchaseOrderId().equals(request.purchaseOrderId())) {
            throw BusinessException.invalidField("supplierId", "Không đổi loại, chi nhánh, nhà cung cấp, đơn mua sau "
                    + "khi lập.");
        }
        BranchRef branch = access.usableBranch(actor, document.getBranchId());
        fill(document, postedOrder(branch, document.getSupplierId(), document.getPurchaseOrderId()), request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public PurchaseReturnResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    PurchaseDocumentFlow.Spec<PurchaseReturn, PurchaseReturnResponse> spec() {
        return spec;
    }

    private void fill(PurchaseReturn document, PurchaseOrder order, PurchaseReturnRequest request) {
        Map<Long, ProductInfo> products = access.products(order.getLines().stream()
                .map(PurchaseOrderLine::getProductId).toList());
        List<PurchaseReturnLine> lines = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseReturnLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            if (!seen.add(line.orderLineId())) {
                throw BusinessException.invalidField(field, "Mỗi dòng đơn mua chỉ xuất hiện một lần.");
            }
            PurchaseOrderLine orderLine = order.line(line.orderLineId(), field + ".orderLineId");
            lines.add(new PurchaseReturnLine(i + 1, request.documentType() == PurchaseReturnType.GGM
                    ? discountLine(orderLine, line, field)
                    : returnLine(orderLine, line, products.get(orderLine.getProductId()), field)));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new PurchaseReturn.Header(request.documentDate(), postingDate,
                PurchasingAccess.trim(request.description())), lines);
    }

    /** Dòng trả hàng: không vượt số còn trả được của dòng đơn mua, đúng serial đã nhập. */
    private static PurchaseReturnLine.Values returnLine(PurchaseOrderLine orderLine, PurchaseReturnLineRequest line,
                                                        ProductInfo product, String field) {
        if (line.quantity() == null) {
            throw BusinessException.invalidField(field + ".quantity", "Phải nhập số lượng trả.");
        }
        if (orderLine.isServiceLine()) {
            throw BusinessException.invalidField(field + ".orderLineId", "Dòng dịch vụ không trả hàng được; dùng giảm "
                    + "giá hàng mua.");
        }
        BigDecimal base = PurchasingAccess.baseQuantity(line.quantity(), orderLine.getConversionFactor(),
                field + ".quantity");
        if (base.compareTo(orderLine.returnableQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": chỉ còn trả được "
                    + orderLine.returnableQuantity().stripTrailingZeros().toPlainString() + " (đơn vị cơ sở).");
        }
        List<String> serials = serials(product, orderLine, line, base, field);
        return new PurchaseReturnLine.Values(orderLine.getId(), orderLine.getProductId(), orderLine.getUnitId(),
                orderLine.getConversionFactor(), line.quantity(), base, orderLine.getLotNo(),
                line.qualityStatus() != null ? line.qualityStatus() : QualityStatus.PASSED, orderLine.netUnitPrice(),
                null, orderLine.getTaxRateId(), orderLine.getTaxRate(), PurchasingAccess.trim(line.description()),
                serials);
    }

    /** Dòng giảm giá: không vượt giá trị còn lại của dòng đơn mua (BR-PUR-14). */
    private static PurchaseReturnLine.Values discountLine(PurchaseOrderLine orderLine, PurchaseReturnLineRequest line,
                                                          String field) {
        if (line.amount() == null) {
            throw BusinessException.invalidField(field + ".amount", "Phải nhập số tiền giảm giá.");
        }
        BigDecimal amount = line.amount().setScale(0, RoundingMode.HALF_UP);
        if (amount.compareTo(orderLine.remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + orderLine.getLineNo()
                    + " của đơn mua chỉ còn giá trị " + orderLine.remainingAmount().stripTrailingZeros()
                    .toPlainString() + ".");
        }
        return new PurchaseReturnLine.Values(orderLine.getId(), orderLine.getProductId(), orderLine.getUnitId(),
                orderLine.getConversionFactor(), null, null, null, null, null, amount, orderLine.getTaxRateId(),
                orderLine.getTaxRate(), PurchasingAccess.trim(line.description()), List.of());
    }

    /** Hàng quản lý serial: đúng số lượng, thuộc serial đã nhập ở dòng đơn mua (BR-PUR-14). */
    private static List<String> serials(ProductInfo product, PurchaseOrderLine orderLine,
                                        PurchaseReturnLineRequest line, BigDecimal base, String field) {
        List<String> serials = line.serialNumbers() == null ? List.of()
                : line.serialNumbers().stream().map(String::trim).toList();
        if (product == null || !product.serialTracked()) {
            if (!serials.isEmpty()) {
                throw BusinessException.invalidField(field + ".serialNumbers", "Hàng không quản lý serial.");
            }
            return serials;
        }
        if (new HashSet<>(serials).size() != serials.size()
                || base.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Số serial phải bằng số lượng trả và không "
                    + "trùng.");
        }
        if (!orderLine.getSerialNumbers().containsAll(serials)) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Serial không thuộc dòng đơn mua gốc.");
        }
        return serials;
    }

    private PurchaseOrder postedOrder(BranchRef branch, Long supplierId, Long orderId) {
        return orderRepository.findById(orderId)
                .filter(found -> found.getCompanyId().equals(branch.companyId())
                        && found.getBranchId().equals(branch.id()) && found.getSupplierId().equals(supplierId)
                        && found.getStatus() == PurchaseDocumentStatus.POSTED)
                .orElseThrow(() -> BusinessException.invalidField("purchaseOrderId", "Đơn mua không tồn tại, khác "
                        + "chi nhánh / nhà cung cấp hoặc chưa ghi sổ."));
    }

    private static ReturnableLineResponse row(PurchaseOrder order, PurchaseOrderLine line, ProductInfo product) {
        return new ReturnableLineResponse(order.getId(), order.getDocumentNo(), order.getPostingDate(),
                order.invoiceLabel(), line.getId(), order.getWarehouseId(), line.isServiceLine(),
                line.getProductId(), product == null ? null : product.code(), product == null ? null : product.name(),
                line.getUnitId(), line.getLotNo(), line.getExpiryDate(),
                line.isServiceLine() ? null : QualityStatus.PASSED,
                line.returnableQuantity().divide(line.getConversionFactor(), 3, RoundingMode.DOWN),
                line.netUnitPrice(), line.getTaxRate(), line.remainingAmount(), List.copyOf(line.getSerialNumbers()));
    }

    PurchaseReturnResponse toResponse(PurchaseReturn document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getSupplierId())).get(document.getSupplierId()),
                detailed);
    }

    private PurchaseReturnResponse toResponse(PurchaseReturn document, PartnerInfo supplier, boolean detailed) {
        List<PurchaseReturnLineResponse> lines = List.of();
        PurchaseOrder order = null;
        String inventoryNo = null;
        if (detailed) {
            order = orderRepository.findById(document.getPurchaseOrderId()).orElse(null);
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(PurchaseReturnLine::getProductId).toList());
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                return new PurchaseReturnLineResponse(line.getId(), line.getLineNo(), line.getOrderLineId(),
                        line.getProductId(), product == null ? null : product.code(),
                        product == null ? null : product.name(), line.getUnitId(), line.getQuantity(),
                        line.getBaseQuantity(), line.getLotNo(), line.getQualityStatus(), line.getUnitPrice(),
                        line.getAmount(), line.getTaxRate(), line.getTaxAmount(), line.getDescription(),
                        List.copyOf(line.getSerialNumbers()));
            }).toList();
            if (document.getInventoryDocumentId() != null) {
                inventoryNo = stockIn.documentNumbers(List.of(document.getInventoryDocumentId()))
                        .get(document.getInventoryDocumentId());
            }
        }
        return new PurchaseReturnResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentType(), document.getDocumentDate(),
                document.getPostingDate(), document.getSupplierId(), supplier == null ? null : supplier.code(),
                supplier == null ? null : supplier.name(), document.getPurchaseOrderId(),
                order == null ? null : order.getDocumentNo(), order == null ? null : order.invoiceLabel(),
                document.getWarehouseId(), document.getInventoryDocumentId(), inventoryNo, document.getDescription(),
                document.getAmount(), document.getTaxAmount(), document.getTotalAmount(), document.getStatus(),
                document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(),
                document.getReverseDate(), document.getReason(), document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(document.typeCode(), document.getId()) : List.of());
    }
}
