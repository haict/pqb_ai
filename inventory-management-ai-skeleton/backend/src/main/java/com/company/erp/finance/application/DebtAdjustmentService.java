package com.company.erp.finance.application;

import com.company.erp.accounting.application.AccountLookupService;
import com.company.erp.accounting.application.AccountLookupService.AccountInfo;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.DebtAdjustmentLineRequest;
import com.company.erp.finance.application.dto.DebtAdjustmentLineResponse;
import com.company.erp.finance.application.dto.DebtAdjustmentRequest;
import com.company.erp.finance.application.dto.DebtAdjustmentResponse;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.AdjustmentDirection;
import com.company.erp.finance.domain.DebtAdjustmentDocument;
import com.company.erp.finance.domain.DebtAdjustmentLine;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtReconciliation;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.finance.infrastructure.persistence.DebtAdjustmentDocumentRepository;
import com.company.erp.finance.infrastructure.persistence.DebtReconciliationRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lập, sửa, hủy điều chỉnh công nợ (DCCN, BR-AR-07; quyền hạn theo quyền ghi sổ, D-61 không qua duyệt) và
 * thanh toán tạm ứng (TTTU, BR-CSH-04). Ghi sổ, đảo ở {@link DebtAdjustmentPostingService}.
 */
@Service
public class DebtAdjustmentService {

    static final String PATH = "/api/v1/debt-adjustments";
    static final String VIEW = "debt-adjustment:view";
    static final String CREATE = "debt-adjustment:create";
    static final String ENTITY = "DEBT_ADJUSTMENT";

    public record DebtAdjustmentFilter(Long companyId, Long branchId, String documentType, DebtType debtType,
                                       FinanceDocumentStatus status, Long partnerId, LocalDate dateFrom,
                                       LocalDate dateTo, String keyword) {
    }

    private final DebtAdjustmentDocumentRepository repository;
    private final DebtReconciliationRepository reconciliations;
    private final OpenDebtItems openItems;
    private final AccountLookupService accounts;
    private final FinanceDocumentFlow flow;
    private final FinanceAccess access;
    private final MasterDataQueryService masterData;
    private final ApprovalService approvalService;
    private final FinanceDocumentFlow.Spec<DebtAdjustmentDocument, DebtAdjustmentResponse> spec;

    @SuppressWarnings("java:S107")
    public DebtAdjustmentService(DebtAdjustmentDocumentRepository repository,
                                 DebtReconciliationRepository reconciliations, OpenDebtItems openItems,
                                 AccountLookupService accounts, FinanceDocumentFlow flow, FinanceAccess access,
                                 MasterDataQueryService masterData, ApprovalService approvalService) {
        this.repository = repository;
        this.reconciliations = reconciliations;
        this.openItems = openItems;
        this.accounts = accounts;
        this.flow = flow;
        this.access = access;
        this.masterData = masterData;
        this.approvalService = approvalService;
        this.spec = new FinanceDocumentFlow.Spec<>(PATH, VIEW, CREATE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                DebtAdjustmentResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<DebtAdjustmentResponse> search(CurrentUser actor, DebtAdjustmentFilter filter, Pageable pageable) {
        Page<DebtAdjustmentDocument> page = flow.search(actor, VIEW, repository,
                new FinanceDocumentFlow.Filter<DebtAdjustmentDocument>(filter.companyId(), filter.branchId(),
                        filter.status(), filter.partnerId(), filter.dateFrom(), filter.dateTo(), filter.keyword(),
                        (root, query, cb) -> cb.and(
                                filter.documentType() == null ? cb.conjunction()
                                        : cb.equal(root.get("documentType"), filter.documentType()),
                                filter.debtType() == null ? cb.conjunction()
                                        : cb.equal(root.get("debtType"), filter.debtType()))),
                pageable);
        Map<Long, PartnerInfo> partners = access.partners(page.getContent().stream()
                .map(DebtAdjustmentDocument::getPartnerId).toList());
        return page.map(document -> toResponse(document, partners.get(document.getPartnerId()), false));
    }

    @Transactional(readOnly = true)
    public DebtAdjustmentResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    @Transactional
    public DebtAdjustmentResponse create(CurrentUser actor, String idempotencyKey, DebtAdjustmentRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, DebtAdjustmentResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            DebtAdjustmentDocument document = new DebtAdjustmentDocument(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), request.documentType(),
                            request.documentDate()), request.documentType(), request.debtType());
            fill(document, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public DebtAdjustmentResponse update(CurrentUser actor, Long id, DebtAdjustmentRequest request) {
        access.requirePermission(actor, CREATE);
        DebtAdjustmentDocument document = flow.lock(actor, id, spec);
        FinanceDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())
                || !document.getDocumentType().equals(request.documentType())
                || document.getDebtType() != request.debtType()) {
            throw BusinessException.invalidField("documentType", "Không đổi chi nhánh, loại chứng từ, loại công nợ "
                    + "sau khi lập.");
        }
        fill(document, request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public DebtAdjustmentResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    FinanceDocumentFlow.Spec<DebtAdjustmentDocument, DebtAdjustmentResponse> spec() {
        return spec;
    }

    /**
     * Kiểm tra đối tượng, biên bản đối chiếu (nếu có), dòng: dòng giảm phải chọn hóa đơn còn nợ không vượt số còn lại;
     * tài khoản đối ứng là tài khoản chi tiết; tài khoản theo dõi đối tượng gắn đối tượng của chứng từ.
     */
    private void fill(DebtAdjustmentDocument document, DebtAdjustmentRequest request) {
        Long partnerId = access.debtPartner(document.getCompanyId(), request.partnerId(), document.getDebtType(),
                "partnerId").id();
        Long reconciliationId = reconciliation(document, partnerId, request.reconciliationId());
        Map<Long, DebtItem> items = openItems.load(request.lines().stream().map(DebtAdjustmentLineRequest::debtItemId)
                .toList());
        Map<Long, AccountInfo> found = accounts.accounts(request.lines().stream()
                        .map(DebtAdjustmentLineRequest::accountId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AccountInfo::id, Function.identity()));
        List<DebtAdjustmentLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            DebtAdjustmentLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            Long itemId = null;
            if (line.direction() == AdjustmentDirection.DECREASE) {
                if (line.debtItemId() == null) {
                    throw BusinessException.invalidField(field + ".debtItemId", "Dòng giảm phải chọn hóa đơn.");
                }
                itemId = openItems.require(items.get(line.debtItemId()), document, partnerId,
                        Set.of(document.getDebtType()), line.amount(), field).getId();
            } else if (line.dueDate() != null && line.dueDate().isBefore(request.documentDate())) {
                throw BusinessException.invalidField(field + ".dueDate", "Hạn thanh toán không được trước ngày chứng từ.");
            }
            AccountInfo account = found.get(line.accountId());
            if (account == null || !account.companyId().equals(document.getCompanyId()) || !account.usable()) {
                throw BusinessException.invalidField(field + ".accountId", "Tài khoản đối ứng không tồn tại, đã ngừng "
                        + "hoặc là tài khoản tổng hợp.");
            }
            if (line.costItemId() != null) {
                access.costItem(document.getCompanyId(), line.costItemId(), field + ".costItemId");
            }
            lines.add(new DebtAdjustmentLine(i + 1, new DebtAdjustmentLine.Content(line.direction(), itemId,
                    account.id(), line.costItemId(), line.amount(),
                    line.direction() == AdjustmentDirection.INCREASE ? trim(line.invoiceNo()) : null,
                    line.direction() == AdjustmentDirection.INCREASE ? line.dueDate() : null,
                    trim(line.description()))));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(request.documentDate(), postingDate, trim(request.description()), partnerId,
                reconciliationId, lines);
    }

    /** Biên bản đối chiếu liên quan: cùng công ty, đối tượng, loại công nợ (truy vết chênh lệch, BR-AR-07). */
    private Long reconciliation(DebtAdjustmentDocument document, Long partnerId, Long reconciliationId) {
        if (reconciliationId == null) {
            return null;
        }
        DebtReconciliation found = reconciliations.findById(reconciliationId)
                .filter(item -> item.getCompanyId().equals(document.getCompanyId())
                        && item.getPartnerId().equals(partnerId) && item.getDebtType() == document.getDebtType())
                .orElseThrow(() -> BusinessException.invalidField("reconciliationId", "Biên bản đối chiếu không thuộc "
                        + "đối tượng, loại công nợ của chứng từ."));
        return found.getId();
    }

    DebtAdjustmentResponse toResponse(DebtAdjustmentDocument document, boolean detailed) {
        return toResponse(document, masterData.partner(document.getPartnerId()).orElse(null), detailed);
    }

    private DebtAdjustmentResponse toResponse(DebtAdjustmentDocument document, PartnerInfo partner,
                                              boolean detailed) {
        List<DebtAdjustmentLineResponse> lines = detailed ? lines(document) : List.of();
        return new DebtAdjustmentResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentType(), document.getDebtType(), document.getPartnerId(),
                partner == null ? null : partner.code(), partner == null ? null : partner.name(),
                document.getReconciliationId(), document.getDocumentDate(), document.getPostingDate(),
                document.getDescription(), document.getTotalAmount(), document.getStatus(),
                document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(),
                document.getReversedAt(), document.getReverseDate(), document.getReason(), document.getCreatedBy(),
                document.getVersion(), lines,
                detailed ? approvalService.history(document.getDocumentType(), document.getId()) : List.of());
    }

    private List<DebtAdjustmentLineResponse> lines(DebtAdjustmentDocument document) {
        Map<Long, DebtItem> items = openItems.load(document.getLines().stream()
                .map(DebtAdjustmentLine::getDebtItemId).filter(Objects::nonNull).toList());
        Map<Long, AccountInfo> glAccounts = accounts.accounts(document.getLines().stream()
                        .map(DebtAdjustmentLine::getAccountId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AccountInfo::id, Function.identity()));
        return document.getLines().stream().map(line -> {
            DebtItem item = line.getDebtItemId() == null ? null : items.get(line.getDebtItemId());
            AccountInfo account = glAccounts.get(line.getAccountId());
            return new DebtAdjustmentLineResponse(line.getId(), line.getLineNo(), line.getDirection(),
                    line.getDebtItemId(), item == null ? null : item.label(), line.getAccountId(),
                    account == null ? null : account.code(), account == null ? null : account.name(),
                    line.getCostItemId(), line.getAmount(), line.getInvoiceNo(), line.getDueDate(),
                    line.getDescription());
        }).toList();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
