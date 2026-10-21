package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.CostAllocationSourceRequest;
import com.company.erp.purchasing.application.dto.CostAllocationSourceResponse;
import com.company.erp.purchasing.application.dto.CostAllocationTargetResponse;
import com.company.erp.purchasing.application.dto.PurchaseCostAllocationRequest;
import com.company.erp.purchasing.application.dto.PurchaseCostAllocationResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.PurchaseCostAllocation;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseOrderLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseCostAllocationRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phân bổ chi phí mua hàng (PBC, BR-PUR-12, 16): chọn dòng dịch vụ của đơn mua đã ghi sổ (cùng chi nhánh) và các đơn
 * mua đã ghi sổ có hàng hóa nhận phân bổ; server tính phân bổ theo giá trị nhập hoặc số lượng. Sửa, hủy (D-61 không
 * qua duyệt). Ghi sổ, đảo ở {@link PurchaseCostAllocationPostingService}.
 */
@Service
public class PurchaseCostAllocationService {

    static final String PATH = "/api/v1/purchase-cost-allocations";
    static final String VIEW = "purchase-cost-allocation:view";
    static final String CREATE = "purchase-cost-allocation:create";
    static final String ENTITY = "PURCHASE_COST_ALLOCATION";

    public record AllocationFilter(Long companyId, Long branchId, PurchaseDocumentStatus status, LocalDate dateFrom,
                                   LocalDate dateTo, String keyword) {
    }

    private final PurchaseCostAllocationRepository repository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final PurchaseDocumentFlow.Spec<PurchaseCostAllocation, PurchaseCostAllocationResponse> spec;

    @SuppressWarnings("java:S107")
    public PurchaseCostAllocationService(PurchaseCostAllocationRepository repository,
                                         PurchaseOrderRepository orderRepository, PurchaseDocumentFlow flow,
                                         PurchasingAccess access, ApprovalService approvalService) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), PurchaseCostAllocationResponse.class, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<PurchaseCostAllocationResponse> search(CurrentUser actor, AllocationFilter filter,
                                                       Pageable pageable) {
        return flow.search(actor, VIEW, repository, new PurchaseDocumentFlow.Filter<PurchaseCostAllocation>(
                        filter.companyId(), filter.branchId(), filter.status(), filter.dateFrom(), filter.dateTo(),
                        filter.keyword(), null), pageable)
                .map(document -> toResponse(document, false));
    }

    @Transactional(readOnly = true)
    public PurchaseCostAllocationResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Dòng dịch vụ của đơn mua đã ghi sổ trong chi nhánh còn chưa phân bổ / giảm hết. */
    @Transactional(readOnly = true)
    public List<CostAllocationSourceResponse> costLines(CurrentUser actor, Long branchId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        List<PurchaseOrder> orders = orderRepository.findWithUnallocatedServices(branch.companyId(), branch.id(),
                PurchaseDocumentStatus.POSTED);
        Map<Long, PartnerInfo> suppliers = access.partners(orders.stream().map(PurchaseOrder::getSupplierId)
                .toList());
        Map<Long, ProductInfo> products = access.products(orders.stream().flatMap(order -> order.getLines()
                .stream()).map(PurchaseOrderLine::getProductId).toList());
        List<CostAllocationSourceResponse> rows = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            PartnerInfo supplier = suppliers.get(order.getSupplierId());
            for (PurchaseOrderLine line : order.getLines()) {
                if (line.isServiceLine() && line.remainingAmount().signum() > 0) {
                    rows.add(sourceRow(null, order, supplier, line, products.get(line.getProductId()),
                            line.remainingAmount(), line.remainingAmount()));
                }
            }
        }
        return rows;
    }

    @Transactional
    public PurchaseCostAllocationResponse create(CurrentUser actor, String idempotencyKey,
                                                 PurchaseCostAllocationRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseCostAllocationResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    BranchRef branch = access.usableBranch(actor, request.branchId());
                    PurchaseCostAllocation document = new PurchaseCostAllocation(branch.companyId(), branch.id(),
                            access.nextNumber(branch.companyId(), branch.id(), PurchaseCostAllocation.TYPE_CODE,
                                    request.documentDate()));
                    fill(document, request);
                    document = repository.saveAndFlush(document);
                    return flow.act(actor, document, "CREATE", null, spec);
                });
    }

    @Transactional
    public PurchaseCostAllocationResponse update(CurrentUser actor, Long id, PurchaseCostAllocationRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseCostAllocation document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())) {
            throw BusinessException.invalidField("branchId", "Không đổi chi nhánh sau khi lập.");
        }
        fill(document, request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public PurchaseCostAllocationResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    PurchaseDocumentFlow.Spec<PurchaseCostAllocation, PurchaseCostAllocationResponse> spec() {
        return spec;
    }

    private void fill(PurchaseCostAllocation document, PurchaseCostAllocationRequest request) {
        List<PurchaseCostAllocation.SourceValues> sources = sources(document, request.sources());
        List<PurchaseCostAllocation.TargetValues> targets = targets(document, request.orderIds());
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(request.documentDate(), postingDate, request.method(),
                PurchasingAccess.trim(request.description()), sources, targets);
    }

    /** Dòng chi phí: dòng dịch vụ của đơn mua đã ghi sổ cùng chi nhánh, không vượt phần còn lại. */
    private List<PurchaseCostAllocation.SourceValues> sources(PurchaseCostAllocation document,
                                                              List<CostAllocationSourceRequest> requests) {
        List<Long> lineIds = requests.stream().map(CostAllocationSourceRequest::orderLineId).toList();
        if (new HashSet<>(lineIds).size() != lineIds.size()) {
            throw BusinessException.invalidField("sources", "Mỗi dòng chi phí chỉ chọn một lần.");
        }
        Map<Long, PurchaseOrderLine> lines = new HashMap<>();
        orderRepository.findByLineIds(lineIds).stream()
                .filter(order -> order.getCompanyId().equals(document.getCompanyId())
                        && order.getBranchId().equals(document.getBranchId())
                        && order.getStatus() == PurchaseDocumentStatus.POSTED)
                .forEach(order -> order.getLines().forEach(line -> lines.put(line.getId(), line)));
        List<PurchaseCostAllocation.SourceValues> values = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            CostAllocationSourceRequest request = requests.get(i);
            String field = "sources[" + i + "]";
            PurchaseOrderLine line = lines.get(request.orderLineId());
            if (line == null || !line.isServiceLine()) {
                throw BusinessException.invalidField(field + ".orderLineId", "Chỉ chọn dòng dịch vụ của đơn mua đã "
                        + "ghi sổ cùng chi nhánh.");
            }
            BigDecimal amount = request.amount().setScale(0, RoundingMode.HALF_UP);
            if (amount.compareTo(line.remainingAmount()) > 0) {
                throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng đơn mua chỉ còn "
                        + line.remainingAmount().stripTrailingZeros().toPlainString() + " chưa phân bổ.");
            }
            values.add(new PurchaseCostAllocation.SourceValues(line.getId(), line.getProductId(), amount));
        }
        return values;
    }

    /** Đích: mọi dòng hàng hóa của các đơn mua đã ghi sổ cùng chi nhánh (cơ sở: số lượng nhập, giá trị nhập). */
    private List<PurchaseCostAllocation.TargetValues> targets(PurchaseCostAllocation document, List<Long> orderIds) {
        List<PurchaseCostAllocation.TargetValues> targets = new ArrayList<>();
        Map<Long, PurchaseOrder> orders = new HashMap<>();
        orderRepository.findAllById(orderIds).forEach(order -> orders.put(order.getId(), order));
        for (Long orderId : new LinkedHashSet<>(orderIds)) {
            PurchaseOrder order = orders.get(orderId);
            if (order == null || !order.getCompanyId().equals(document.getCompanyId())
                    || !order.getBranchId().equals(document.getBranchId())
                    || order.getStatus() != PurchaseDocumentStatus.POSTED) {
                throw BusinessException.invalidField("orderIds", "Đơn mua #" + orderId + " không tồn tại, khác chi "
                        + "nhánh hoặc chưa ghi sổ.");
            }
            for (PurchaseOrderLine line : order.getLines()) {
                if (!line.isServiceLine()) {
                    targets.add(new PurchaseCostAllocation.TargetValues(line.getId(), line.getProductId(),
                            line.getBaseQuantity(), line.netAmount()));
                }
            }
        }
        return targets;
    }

    private static CostAllocationSourceResponse sourceRow(Integer lineNo, PurchaseOrder order, PartnerInfo supplier,
                                                          PurchaseOrderLine line, ProductInfo product,
                                                          BigDecimal amount, BigDecimal remaining) {
        return new CostAllocationSourceResponse(lineNo, order == null ? null : order.getId(),
                order == null ? null : order.getDocumentNo(), order == null ? null : order.invoiceLabel(),
                supplier == null ? null : supplier.name(), line == null ? null : line.getId(),
                product == null ? null : product.id(), product == null ? null : product.code(),
                product == null ? null : product.name(), amount, remaining);
    }

    PurchaseCostAllocationResponse toResponse(PurchaseCostAllocation document, boolean detailed) {
        List<CostAllocationSourceResponse> sources = List.of();
        List<CostAllocationTargetResponse> targets = List.of();
        List<Long> orderIds = List.of();
        if (detailed) {
            Map<Long, PurchaseOrder> ordersByLine = new HashMap<>();
            List<Long> lineIds = Stream.concat(
                    document.getSources().stream().map(PurchaseCostAllocation.Source::getOrderLineId),
                    document.getTargets().stream().map(PurchaseCostAllocation.Target::getOrderLineId)).toList();
            orderRepository.findByLineIds(lineIds).forEach(order -> order.getLines()
                    .forEach(line -> ordersByLine.put(line.getId(), order)));
            Map<Long, ProductInfo> products = access.products(Stream.concat(
                    document.getSources().stream().map(PurchaseCostAllocation.Source::getProductId),
                    document.getTargets().stream().map(PurchaseCostAllocation.Target::getProductId)).toList());
            Map<Long, PartnerInfo> suppliers = access.partners(ordersByLine.values().stream()
                    .map(PurchaseOrder::getSupplierId).toList());
            sources = document.getSources().stream().map(source -> {
                PurchaseOrder order = ordersByLine.get(source.getOrderLineId());
                return sourceRow(source.getLineNo(), order,
                        order == null ? null : suppliers.get(order.getSupplierId()),
                        order == null ? null : order.line(source.getOrderLineId(), "sources"),
                        products.get(source.getProductId()), source.getAmount(), null);
            }).toList();
            targets = document.getTargets().stream().map(target -> {
                PurchaseOrder order = ordersByLine.get(target.getOrderLineId());
                ProductInfo product = products.get(target.getProductId());
                return new CostAllocationTargetResponse(target.getLineNo(), order == null ? null : order.getId(),
                        order == null ? null : order.getDocumentNo(), target.getOrderLineId(),
                        target.getProductId(), product == null ? null : product.code(),
                        product == null ? null : product.name(), target.getBaseQuantity(), target.getBasisValue(),
                        target.getAllocatedAmount());
            }).toList();
            orderIds = targets.stream().map(CostAllocationTargetResponse::orderId).distinct().toList();
        }
        return new PurchaseCostAllocationResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(), document.getMethod(),
                document.getDescription(), document.getAmount(), document.getStatus(), document.getSubmittedAt(),
                document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(), document.getReverseDate(),
                document.getReason(), document.getCreatedBy(), document.getVersion(), orderIds, sources, targets,
                detailed ? approvalService.history(PurchaseCostAllocation.TYPE_CODE, document.getId()) : List.of());
    }
}
