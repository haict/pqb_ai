package com.company.erp.finance.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.CommentRequest;
import com.company.erp.finance.application.dto.DebtOffsetLineRequest;
import com.company.erp.finance.application.dto.DebtOffsetLineResponse;
import com.company.erp.finance.application.dto.DebtOffsetRequest;
import com.company.erp.finance.application.dto.DebtOffsetResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtOffsetDocument;
import com.company.erp.finance.domain.DebtOffsetLine;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.finance.domain.OffsetType;
import com.company.erp.finance.infrastructure.persistence.DebtOffsetDocumentRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lập, sửa, gửi duyệt, duyệt, từ chối, hủy chứng từ bù trừ công nợ (DTCN, BR-AR-04). Ghi sổ, đảo ở
 * {@link DebtOffsetPostingService}.
 */
@Service
public class DebtOffsetService {

    static final String PATH = "/api/v1/debt-offsets";
    static final String VIEW = "debt-offset:view";
    static final String CREATE = "debt-offset:create";
    static final String APPROVE = "debt-offset:approve";
    static final String ENTITY = "DEBT_OFFSET";

    public record DebtOffsetFilter(Long companyId, Long branchId, OffsetType offsetType, FinanceDocumentStatus status,
                                   Long partnerId, LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final DebtOffsetDocumentRepository repository;
    private final OpenDebtItems openItems;
    private final DebtSettlementService settlement;
    private final FinanceDocumentFlow flow;
    private final FinanceAccess access;
    private final MasterDataQueryService masterData;
    private final ApprovalService approvalService;
    private final FinanceDocumentFlow.Spec<DebtOffsetDocument, DebtOffsetResponse> spec;

    @SuppressWarnings("java:S107")
    public DebtOffsetService(DebtOffsetDocumentRepository repository, OpenDebtItems openItems,
                             DebtSettlementService settlement, FinanceDocumentFlow flow, FinanceAccess access,
                             MasterDataQueryService masterData, ApprovalService approvalService) {
        this.repository = repository;
        this.openItems = openItems;
        this.settlement = settlement;
        this.flow = flow;
        this.access = access;
        this.masterData = masterData;
        this.approvalService = approvalService;
        this.spec = new FinanceDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                DebtOffsetResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<DebtOffsetResponse> search(CurrentUser actor, DebtOffsetFilter filter, Pageable pageable) {
        Page<DebtOffsetDocument> page = flow.search(actor, VIEW, repository,
                new FinanceDocumentFlow.Filter<DebtOffsetDocument>(filter.companyId(), filter.branchId(),
                        filter.status(), filter.partnerId(), filter.dateFrom(), filter.dateTo(), filter.keyword(),
                        filter.offsetType() == null ? null
                                : (root, query, cb) -> cb.equal(root.get("offsetType"), filter.offsetType())),
                pageable);
        Map<Long, PartnerInfo> partners = access.partners(page.getContent().stream()
                .map(DebtOffsetDocument::getPartnerId).toList());
        return page.map(document -> toResponse(document, partners.get(document.getPartnerId()), false));
    }

    @Transactional(readOnly = true)
    public DebtOffsetResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public DebtOffsetResponse create(CurrentUser actor, String idempotencyKey, DebtOffsetRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, DebtOffsetResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            DebtOffsetDocument document = new DebtOffsetDocument(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), DebtOffsetDocument.TYPE_CODE,
                            request.documentDate()), request.offsetType(), request.debtType());
            fill(document, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public DebtOffsetResponse update(CurrentUser actor, Long id, DebtOffsetRequest request) {
        access.requirePermission(actor, CREATE);
        DebtOffsetDocument document = flow.lock(actor, id, spec);
        FinanceDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId()) || document.getOffsetType() != request.offsetType()
                || document.getDebtType() != request.debtType()) {
            throw BusinessException.invalidField("offsetType", "Không đổi chi nhánh, loại bù trừ sau khi lập.");
        }
        fill(document, request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public DebtOffsetResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public DebtOffsetResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public DebtOffsetResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public DebtOffsetResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    FinanceDocumentFlow.Spec<DebtOffsetDocument, DebtOffsetResponse> spec() {
        return spec;
    }

    /**
     * Kiểm tra đối tượng, dòng: bù trừ trả trước trừ vào hóa đơn cùng loại công nợ, tổng không vượt trả trước còn;
     * bù trừ phải thu - phải trả có tổng dòng phải thu bằng tổng dòng phải trả.
     */
    private void fill(DebtOffsetDocument document, DebtOffsetRequest request) {
        Long partnerId = partner(document, request.partnerId());
        Set<DebtType> types = document.getOffsetType() == OffsetType.PREPAYMENT ? Set.of(document.getDebtType())
                : Set.of(DebtType.RECEIVABLE, DebtType.PAYABLE);
        Map<Long, DebtItem> items = openItems.load(request.lines().stream().map(DebtOffsetLineRequest::debtItemId)
                .toList());
        List<DebtOffsetLine> lines = new ArrayList<>();
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal payable = BigDecimal.ZERO;
        for (int i = 0; i < request.lines().size(); i++) {
            DebtOffsetLineRequest line = request.lines().get(i);
            DebtItem item = openItems.require(items.get(line.debtItemId()), document, partnerId, types,
                    line.amount(), "lines[" + i + "]");
            if (item.getDebtType() == DebtType.PAYABLE) {
                payable = payable.add(line.amount());
            } else {
                receivable = receivable.add(line.amount());
            }
            lines.add(new DebtOffsetLine(i + 1, item.getId(), line.amount(), trim(line.description())));
        }
        BigDecimal total = receivable.add(payable);
        if (document.getOffsetType() == OffsetType.AR_AP) {
            if (receivable.compareTo(payable) != 0) {
                throw BusinessException.invalidField("lines", "Tổng phải thu bù trừ ("
                        + receivable.stripTrailingZeros().toPlainString() + ") phải bằng tổng phải trả ("
                        + payable.stripTrailingZeros().toPlainString() + ").");
            }
            total = receivable;
        } else {
            BigDecimal available = settlement.prepayment(document.getCompanyId(), document.getBranchId(),
                    document.getDebtType(), partnerId);
            if (total.compareTo(available) > 0) {
                throw BusinessException.invalidField("lines", "Tiền trả trước còn "
                        + available.stripTrailingZeros().toPlainString() + ", không đủ bù trừ.");
            }
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(request.documentDate(), postingDate, trim(request.description()), partnerId, total,
                lines);
    }

    /** Bù trừ phải thu - phải trả: đối tượng phải vừa là khách hàng vừa là nhà cung cấp (P-07). */
    private Long partner(DebtOffsetDocument document, Long partnerId) {
        if (document.getOffsetType() == OffsetType.PREPAYMENT) {
            return access.debtPartner(document.getCompanyId(), partnerId, document.getDebtType(), "partnerId").id();
        }
        access.debtPartner(document.getCompanyId(), partnerId, DebtType.RECEIVABLE, "partnerId");
        return access.debtPartner(document.getCompanyId(), partnerId, DebtType.PAYABLE, "partnerId").id();
    }

    DebtOffsetResponse toResponse(DebtOffsetDocument document, boolean detailed) {
        return toResponse(document, masterData.partner(document.getPartnerId()).orElse(null), detailed);
    }

    private DebtOffsetResponse toResponse(DebtOffsetDocument document, PartnerInfo partner, boolean detailed) {
        List<DebtOffsetLineResponse> lines = List.of();
        if (detailed) {
            Map<Long, DebtItem> items = openItems.load(document.getLines().stream()
                    .map(DebtOffsetLine::getDebtItemId).toList());
            lines = document.getLines().stream().map(line -> {
                DebtItem item = items.get(line.getDebtItemId());
                return new DebtOffsetLineResponse(line.getId(), line.getLineNo(), line.getDebtItemId(),
                        item == null ? null : item.getDebtType(), item == null ? null : item.label(),
                        item == null ? null : item.getDueDate(), item == null ? null : item.getRemainingAmount(),
                        line.getAmount(), line.getDescription());
            }).toList();
        }
        return new DebtOffsetResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getOffsetType(), document.getDebtType(), document.getPartnerId(),
                partner == null ? null : partner.code(), partner == null ? null : partner.name(),
                document.getDocumentDate(), document.getPostingDate(), document.getDescription(),
                document.getTotalAmount(), document.getStatus(), document.getSubmittedAt(), document.getApprovedAt(),
                document.getPostedAt(), document.getReversedAt(), document.getReverseDate(), document.getReason(),
                document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(DebtOffsetDocument.TYPE_CODE, document.getId()) : List.of());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
