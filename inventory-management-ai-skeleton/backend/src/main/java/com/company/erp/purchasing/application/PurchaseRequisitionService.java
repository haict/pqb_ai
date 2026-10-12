package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.OpenRequisitionLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseRequisitionLineRequest;
import com.company.erp.purchasing.application.dto.PurchaseRequisitionLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseRequisitionRequest;
import com.company.erp.purchasing.application.dto.PurchaseRequisitionResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.PurchaseDocument;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseRequisition;
import com.company.erp.purchasing.domain.PurchaseRequisitionLine;
import com.company.erp.purchasing.domain.RequisitionSource;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseRequisitionRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseRequisitionRepository.OpenLineRow;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.OrganizationQueryService.UserRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Đề nghị mua hàng (DNM, BR-PUR-01): lập, sửa, gửi duyệt, duyệt, từ chối, hủy, đóng phần còn lại. */
@Service
public class PurchaseRequisitionService {

    static final String PATH = "/api/v1/purchase-requisitions";
    static final String VIEW = "purchase-requisition:view";
    static final String CREATE = "purchase-requisition:create";
    static final String APPROVE = "purchase-requisition:approve";
    static final String CLOSE = "purchase-requisition:close";
    static final String ENTITY = "PURCHASE_REQUISITION";

    public record RequisitionFilter(Long companyId, Long branchId, PurchaseDocumentStatus status, LocalDate dateFrom,
                                    LocalDate dateTo, String keyword) {
    }

    private final PurchaseRequisitionRepository repository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final Clock clock;
    private final PurchaseDocumentFlow.Spec<PurchaseRequisition, PurchaseRequisitionResponse> spec;

    public PurchaseRequisitionService(PurchaseRequisitionRepository repository, PurchaseDocumentFlow flow,
                                      PurchasingAccess access, ApprovalService approvalService, Clock clock) {
        this.repository = repository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.clock = clock;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                PurchaseRequisitionResponse.class, null, null, document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequisitionResponse> search(CurrentUser actor, RequisitionFilter filter, Pageable pageable) {
        Page<PurchaseRequisition> page = flow.search(actor, VIEW, repository,
                new PurchaseDocumentFlow.Filter<>(filter.companyId(), filter.branchId(), filter.status(),
                        filter.dateFrom(), filter.dateTo(), filter.keyword(), null), pageable);
        Map<Long, UserRef> users = access.users(page.getContent().stream()
                .map(PurchaseRequisition::getRequesterId).toList());
        return page.map(document -> toResponse(document, users.get(document.getRequesterId()), false));
    }

    @Transactional(readOnly = true)
    public PurchaseRequisitionResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Dòng đề nghị đã duyệt còn số chưa đặt của chi nhánh, để lập đơn mua theo đề nghị. */
    @Transactional(readOnly = true)
    public List<OpenRequisitionLineResponse> openLines(CurrentUser actor, Long branchId, Long productId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        List<OpenLineRow> rows = repository.findOpenLines(branch.companyId(), branch.id(), PurchaseDocument.OPEN,
                productId);
        Map<Long, ProductInfo> products = access.products(rows.stream().map(OpenLineRow::getProductId).toList());
        return rows.stream().map(row -> {
            ProductInfo product = products.get(row.getProductId());
            return new OpenRequisitionLineResponse(row.getRequisitionId(), row.getDocumentNo(), row.getDocumentDate(),
                    row.getLineId(), row.getLineNo(), row.getProductId(), product == null ? null : product.code(),
                    product == null ? null : product.name(), product == null ? null : product.baseUnitId(),
                    row.getRemaining(), row.getSuggestedSupplierId(), row.getRequiredDate());
        }).toList();
    }

    @Transactional
    public PurchaseRequisitionResponse create(CurrentUser actor, String idempotencyKey,
                                              PurchaseRequisitionRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseRequisitionResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    BranchRef branch = access.usableBranch(actor, request.branchId());
                    PurchaseRequisition document = new PurchaseRequisition(branch.companyId(), branch.id(),
                            access.nextNumber(branch.companyId(), branch.id(), PurchaseRequisition.TYPE_CODE,
                                    request.documentDate()), RequisitionSource.MANUAL);
                    fill(actor, document, request);
                    document = repository.saveAndFlush(document);
                    return flow.act(actor, document, "CREATE", null, spec);
                });
    }

    @Transactional
    public PurchaseRequisitionResponse update(CurrentUser actor, Long id, PurchaseRequisitionRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseRequisition document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())) {
            throw BusinessException.invalidField("branchId", "Không đổi chi nhánh sau khi lập.");
        }
        fill(actor, document, request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public PurchaseRequisitionResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public PurchaseRequisitionResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseRequisitionResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseRequisitionResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    /** Dòng đề nghị mua do chứng từ nguồn module khác sinh (số lượng theo đơn vị {@code unitId}). */
    public record SourceRequisitionLine(Long productId, Long unitId, BigDecimal quantity, LocalDate requiredDate,
                                        String note) {
    }

    /** Đề nghị mua đã lập từ chứng từ nguồn. */
    public record SourceRequisitionResult(Long id, String documentNo) {
    }

    /**
     * API công khai: lập và gửi duyệt đề nghị mua từ đơn bán thiếu hàng (BR-SAL-15, BR-PUR-01) trong transaction của
     * chứng từ nguồn; quyền do module nguồn kiểm tra. Gửi duyệt theo luồng DNM (không có luồng thì tự duyệt).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SourceRequisitionResult createFromSalesOrder(CurrentUser actor, Long branchId, LocalDate documentDate,
                                                        Long sourceId, String sourceNo,
                                                        List<SourceRequisitionLine> lines) {
        BranchRef branch = access.usableBranch(actor, branchId);
        PurchaseRequisition document = new PurchaseRequisition(branch.companyId(), branch.id(),
                access.nextNumber(branch.companyId(), branch.id(), PurchaseRequisition.TYPE_CODE, documentDate),
                RequisitionSource.SALES_ORDER);
        document.linkSource(sourceId, sourceNo);
        fill(actor, document, new PurchaseRequisitionRequest(branchId, documentDate, null, null,
                "Thiếu hàng cho đơn bán " + sourceNo, lines.stream()
                .map(line -> new PurchaseRequisitionLineRequest(line.productId(), line.unitId(), line.quantity(), null,
                        line.requiredDate(), line.note())).toList(), null));
        document = repository.saveAndFlush(document);
        flow.submitLocked(actor, document, spec);
        flow.act(actor, document, "CREATE_FROM_SALES_ORDER", null, spec);
        return new SourceRequisitionResult(document.getId(), document.getDocumentNo());
    }

    /** Đóng phần chưa đặt mua (BR-PUR-05); đơn mua đã lập theo đề nghị không bị ảnh hưởng. */
    @Transactional
    public PurchaseRequisitionResponse close(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/close", request,
                PurchaseRequisitionResponse.class, () -> {
                    access.requirePermission(actor, CLOSE);
                    PurchaseRequisition document = flow.lock(actor, id, spec);
                    document.close(actor.id(), Instant.now(clock), request.reason().trim());
                    return flow.act(actor, document, "CLOSE", request.reason(), spec);
                });
    }

    private void fill(CurrentUser actor, PurchaseRequisition document, PurchaseRequisitionRequest request) {
        Long companyId = document.getCompanyId();
        Long requesterId = request.requesterId() == null ? actor.id()
                : access.user(companyId, request.requesterId(), "requesterId").id();
        List<PurchaseRequisitionLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseRequisitionLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            access.product(companyId, line.productId(), field + ".productId");
            BigDecimal factor = access.conversionFactor(line.productId(), line.unitId(), request.documentDate(),
                    field + ".unitId");
            if (line.suggestedSupplierId() != null) {
                access.supplier(companyId, line.suggestedSupplierId(), field + ".suggestedSupplierId");
            }
            lines.add(new PurchaseRequisitionLine(i + 1, line.productId(), line.unitId(), factor, line.quantity(),
                    PurchasingAccess.baseQuantity(line.quantity(), factor, field + ".quantity"),
                    line.suggestedSupplierId(), line.requiredDate(), trim(line.note())));
        }
        document.replaceContent(request.documentDate(), request.requiredDate(), requesterId,
                trim(request.description()), lines);
    }

    private PurchaseRequisitionResponse toResponse(PurchaseRequisition document, boolean detailed) {
        UserRef requester = document.getRequesterId() == null ? null
                : access.users(List.of(document.getRequesterId())).get(document.getRequesterId());
        return toResponse(document, requester, detailed);
    }

    private PurchaseRequisitionResponse toResponse(PurchaseRequisition document, UserRef requester,
                                                   boolean detailed) {
        List<PurchaseRequisitionLineResponse> lines = List.of();
        if (detailed) {
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(PurchaseRequisitionLine::getProductId).toList());
            Map<Long, PartnerInfo> suppliers = access.partners(document.getLines().stream()
                    .map(PurchaseRequisitionLine::getSuggestedSupplierId).toList());
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                PartnerInfo supplier = suppliers.get(line.getSuggestedSupplierId());
                return new PurchaseRequisitionLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                        product == null ? null : product.code(), product == null ? null : product.name(),
                        line.getUnitId(), product == null ? null : product.baseUnitId(), line.getConversionFactor(),
                        line.getQuantity(), line.getBaseQuantity(), line.getOrderedQuantity(),
                        line.getClosedQuantity(), line.remainingQuantity(), line.getSuggestedSupplierId(),
                        supplier == null ? null : supplier.name(), line.getRequiredDate(), line.getNote());
            }).toList();
        }
        return new PurchaseRequisitionResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getSourceType(), document.getDocumentDate(),
                document.getRequiredDate(), document.getRequesterId(), requester == null ? null : requester.fullName(),
                document.getDescription(), document.getStatus(), document.getSubmittedAt(), document.getApprovedAt(),
                document.getClosedAt(), document.getReason(), document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(PurchaseRequisition.TYPE_CODE, document.getId()) : List.of());
    }

    static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
