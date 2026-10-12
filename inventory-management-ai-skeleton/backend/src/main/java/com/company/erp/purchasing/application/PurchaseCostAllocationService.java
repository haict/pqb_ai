package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.CostAllocationSourceRequest;
import com.company.erp.purchasing.application.dto.CostAllocationSourceResponse;
import com.company.erp.purchasing.application.dto.CostAllocationTargetResponse;
import com.company.erp.purchasing.application.dto.PurchaseCostAllocationRequest;
import com.company.erp.purchasing.application.dto.PurchaseCostAllocationResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.domain.PurchaseCostAllocation;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseInvoice;
import com.company.erp.purchasing.domain.PurchaseInvoiceLine;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import com.company.erp.purchasing.domain.PurchaseReceiptLine;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseCostAllocationRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseInvoiceRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReceiptRepository;
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
 * Phân bổ chi phí mua hàng (PBC, BR-PUR-12, 16): chọn dòng dịch vụ của hóa đơn mua đã ghi sổ (cùng chi nhánh) và các
 * phiếu nhận đã nhập kho; server tính phân bổ theo giá trị tạm tính hoặc số lượng. Gửi duyệt (duyệt khi có luồng),
 * duyệt, từ chối, hủy. Ghi sổ, đảo ở {@link PurchaseCostAllocationPostingService}.
 */
@Service
public class PurchaseCostAllocationService {

    static final String PATH = "/api/v1/purchase-cost-allocations";
    static final String VIEW = "purchase-cost-allocation:view";
    static final String CREATE = "purchase-cost-allocation:create";
    static final String APPROVE = "purchase-cost-allocation:approve";
    static final String ENTITY = "PURCHASE_COST_ALLOCATION";

    public record AllocationFilter(Long companyId, Long branchId, PurchaseDocumentStatus status, LocalDate dateFrom,
                                   LocalDate dateTo, String keyword) {
    }

    private final PurchaseCostAllocationRepository repository;
    private final PurchaseInvoiceRepository invoiceRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final PurchaseDocumentFlow.Spec<PurchaseCostAllocation, PurchaseCostAllocationResponse> spec;

    @SuppressWarnings("java:S107")
    public PurchaseCostAllocationService(PurchaseCostAllocationRepository repository,
                                         PurchaseInvoiceRepository invoiceRepository,
                                         PurchaseReceiptRepository receiptRepository, PurchaseDocumentFlow flow,
                                         PurchasingAccess access, ApprovalService approvalService) {
        this.repository = repository;
        this.invoiceRepository = invoiceRepository;
        this.receiptRepository = receiptRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                PurchaseCostAllocationResponse.class, null, null, document -> { }, document -> { });
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

    /** Dòng dịch vụ của hóa đơn mua đã ghi sổ trong chi nhánh còn chưa phân bổ / giảm hết. */
    @Transactional(readOnly = true)
    public List<CostAllocationSourceResponse> costLines(CurrentUser actor, Long branchId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        List<PurchaseInvoice> invoices = invoiceRepository.findWithUnallocatedServices(branch.companyId(),
                branch.id(), PurchaseDocumentStatus.POSTED);
        Map<Long, PartnerInfo> suppliers = access.partners(invoices.stream().map(PurchaseInvoice::getSupplierId)
                .toList());
        Map<Long, ProductInfo> products = access.products(invoices.stream().flatMap(invoice -> invoice.getLines()
                .stream()).map(PurchaseInvoiceLine::getProductId).toList());
        List<CostAllocationSourceResponse> rows = new ArrayList<>();
        for (PurchaseInvoice invoice : invoices) {
            PartnerInfo supplier = suppliers.get(invoice.getSupplierId());
            for (PurchaseInvoiceLine line : invoice.getLines()) {
                if (!line.isGoods() && line.remainingAmount().signum() > 0) {
                    rows.add(sourceRow(null, invoice, supplier, line, products.get(line.getProductId()),
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
    public PurchaseCostAllocationResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public PurchaseCostAllocationResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseCostAllocationResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
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
        List<PurchaseCostAllocation.TargetValues> targets = targets(document, request.receiptIds());
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(request.documentDate(), postingDate, request.method(),
                PurchaseRequisitionService.trim(request.description()), sources, targets);
    }

    /** Dòng chi phí: dòng dịch vụ của hóa đơn mua đã ghi sổ cùng chi nhánh, không vượt phần còn lại. */
    private List<PurchaseCostAllocation.SourceValues> sources(PurchaseCostAllocation document,
                                                              List<CostAllocationSourceRequest> requests) {
        List<Long> lineIds = requests.stream().map(CostAllocationSourceRequest::invoiceLineId).toList();
        if (new HashSet<>(lineIds).size() != lineIds.size()) {
            throw BusinessException.invalidField("sources", "Mỗi dòng chi phí chỉ chọn một lần.");
        }
        Map<Long, PurchaseInvoiceLine> lines = new HashMap<>();
        invoiceRepository.findByLineIds(lineIds).stream()
                .filter(invoice -> invoice.getCompanyId().equals(document.getCompanyId())
                        && invoice.getBranchId().equals(document.getBranchId())
                        && invoice.getStatus() == PurchaseDocumentStatus.POSTED)
                .forEach(invoice -> invoice.getLines().forEach(line -> lines.put(line.getId(), line)));
        List<PurchaseCostAllocation.SourceValues> values = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            CostAllocationSourceRequest request = requests.get(i);
            String field = "sources[" + i + "]";
            PurchaseInvoiceLine line = lines.get(request.invoiceLineId());
            if (line == null || line.isGoods()) {
                throw BusinessException.invalidField(field + ".invoiceLineId", "Chỉ chọn dòng dịch vụ của hóa đơn mua "
                        + "đã ghi sổ cùng chi nhánh.");
            }
            BigDecimal amount = request.amount().setScale(0, RoundingMode.HALF_UP);
            if (amount.compareTo(line.remainingAmount()) > 0) {
                throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng hóa đơn chỉ còn "
                        + line.remainingAmount().stripTrailingZeros().toPlainString() + " chưa phân bổ.");
            }
            values.add(new PurchaseCostAllocation.SourceValues(line.getId(), line.getProductId(), amount));
        }
        return values;
    }

    /** Đích: mọi dòng đã nhập kho (đạt / cách ly) của các phiếu nhận đã ghi sổ, nhập kho, cùng chi nhánh. */
    private List<PurchaseCostAllocation.TargetValues> targets(PurchaseCostAllocation document, List<Long> receiptIds) {
        List<PurchaseCostAllocation.TargetValues> targets = new ArrayList<>();
        Map<Long, PurchaseReceipt> receipts = new HashMap<>();
        receiptRepository.findAllById(receiptIds).forEach(receipt -> receipts.put(receipt.getId(), receipt));
        for (Long receiptId : new LinkedHashSet<>(receiptIds)) {
            PurchaseReceipt receipt = receipts.get(receiptId);
            if (receipt == null || !receipt.getCompanyId().equals(document.getCompanyId())
                    || !receipt.getBranchId().equals(document.getBranchId())
                    || receipt.getStatus() != PurchaseDocumentStatus.POSTED || !receipt.isStocked()) {
                throw BusinessException.invalidField("receiptIds", "Phiếu nhận hàng #" + receiptId + " không tồn tại, "
                        + "khác chi nhánh hoặc chưa nhập kho.");
            }
            for (PurchaseReceiptLine line : receipt.getLines()) {
                if (line.stockedBaseQuantity().signum() > 0) {
                    targets.add(new PurchaseCostAllocation.TargetValues(line.getId(), line.getProductId(),
                            line.stockedBaseQuantity(), line.getUnitCost().multiply(line.getQuantity())
                            .setScale(0, RoundingMode.HALF_UP)));
                }
            }
        }
        return targets;
    }

    private static CostAllocationSourceResponse sourceRow(Integer lineNo, PurchaseInvoice invoice,
                                                          PartnerInfo supplier, PurchaseInvoiceLine line,
                                                          ProductInfo product, BigDecimal amount,
                                                          BigDecimal remaining) {
        return new CostAllocationSourceResponse(lineNo, invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getDocumentNo(), invoice == null ? null : invoice.invoiceLabel(),
                supplier == null ? null : supplier.name(), line == null ? null : line.getId(),
                product == null ? null : product.id(), product == null ? null : product.code(),
                product == null ? null : product.name(), amount, remaining);
    }

    PurchaseCostAllocationResponse toResponse(PurchaseCostAllocation document, boolean detailed) {
        List<CostAllocationSourceResponse> sources = List.of();
        List<CostAllocationTargetResponse> targets = List.of();
        List<Long> receiptIds = List.of();
        if (detailed) {
            Map<Long, PurchaseInvoice> invoices = new HashMap<>();
            List<Long> invoiceLineIds = document.getSources().stream()
                    .map(PurchaseCostAllocation.Source::getInvoiceLineId).toList();
            invoiceRepository.findByLineIds(invoiceLineIds).forEach(invoice -> invoice.getLines()
                    .forEach(line -> invoices.put(line.getId(), invoice)));
            Map<Long, PurchaseReceipt> receipts = new HashMap<>();
            List<Long> receiptLineIds = document.getTargets().stream()
                    .map(PurchaseCostAllocation.Target::getReceiptLineId).toList();
            receiptRepository.findByLineIds(receiptLineIds).forEach(receipt -> receipt.getLines()
                    .forEach(line -> receipts.put(line.getId(), receipt)));
            Map<Long, ProductInfo> products = access.products(Stream.concat(
                    document.getSources().stream().map(PurchaseCostAllocation.Source::getProductId),
                    document.getTargets().stream().map(PurchaseCostAllocation.Target::getProductId)).toList());
            Map<Long, PartnerInfo> suppliers = access.partners(invoices.values().stream()
                    .map(PurchaseInvoice::getSupplierId).toList());
            sources = document.getSources().stream().map(source -> {
                PurchaseInvoice invoice = invoices.get(source.getInvoiceLineId());
                return sourceRow(source.getLineNo(), invoice,
                        invoice == null ? null : suppliers.get(invoice.getSupplierId()),
                        invoice == null ? null : invoice.line(source.getInvoiceLineId(), "sources"),
                        products.get(source.getProductId()), source.getAmount(), null);
            }).toList();
            targets = document.getTargets().stream().map(target -> {
                PurchaseReceipt receipt = receipts.get(target.getReceiptLineId());
                ProductInfo product = products.get(target.getProductId());
                return new CostAllocationTargetResponse(target.getLineNo(), receipt == null ? null : receipt.getId(),
                        receipt == null ? null : receipt.getDocumentNo(), target.getReceiptLineId(),
                        target.getProductId(), product == null ? null : product.code(),
                        product == null ? null : product.name(), target.getBaseQuantity(), target.getBasisValue(),
                        target.getAllocatedAmount());
            }).toList();
            receiptIds = targets.stream().map(CostAllocationTargetResponse::receiptId).distinct().toList();
        }
        return new PurchaseCostAllocationResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(), document.getMethod(),
                document.getDescription(), document.getAmount(), document.getStatus(), document.getSubmittedAt(),
                document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(), document.getReverseDate(),
                document.getReason(), document.getCreatedBy(), document.getVersion(), receiptIds, sources, targets,
                detailed ? approvalService.history(PurchaseCostAllocation.TYPE_CODE, document.getId()) : List.of());
    }
}
