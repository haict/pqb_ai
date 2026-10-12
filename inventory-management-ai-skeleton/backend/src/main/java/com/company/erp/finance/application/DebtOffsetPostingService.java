package com.company.erp.finance.application;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.finance.application.DebtSettlementService.AllocationRequest;
import com.company.erp.finance.application.DebtSettlementService.PrepaymentOffsetCommand;
import com.company.erp.finance.application.DebtSettlementService.SettlementCommand;
import com.company.erp.finance.application.DebtSettlementService.SettlementReversalCommand;
import com.company.erp.finance.application.dto.DebtOffsetResponse;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.DebtEntryType;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtOffsetDocument;
import com.company.erp.finance.domain.DebtOffsetLine;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.OffsetType;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ và đảo bù trừ công nợ (BR-AR-04) trong một transaction. Bù trừ trả trước: dùng trả trước, phân bổ vào hóa đơn,
 * không sinh bút toán (cả hai vế cùng tài khoản công nợ). Bù trừ phải thu - phải trả: phân bổ hai phía và bút toán theo
 * quy tắc hạch toán mã {@code AR_AP_OFFSET}. Bỏ đối trừ = đảo chứng từ.
 */
@Service
public class DebtOffsetPostingService {

    static final String POST = "debt-offset:post";
    static final String REVERSE = "debt-offset:reverse";
    static final String AR_AP_OFFSET = "AR_AP_OFFSET";

    private final DebtOffsetService documents;
    private final DebtSettlementService settlement;
    private final OpenDebtItems openItems;
    private final JournalPostingService journalPosting;
    private final FinanceDocumentFlow flow;
    private final FinanceAccess access;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public DebtOffsetPostingService(DebtOffsetService documents, DebtSettlementService settlement,
                                    OpenDebtItems openItems, JournalPostingService journalPosting,
                                    FinanceDocumentFlow flow, FinanceAccess access, Clock clock) {
        this.documents = documents;
        this.settlement = settlement;
        this.openItems = openItems;
        this.journalPosting = journalPosting;
        this.flow = flow;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtOffsetResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + DebtOffsetService.PATH + "/" + id + "/post", id,
                DebtOffsetResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    DebtOffsetDocument document = flow.lock(actor, id, documents.spec());
                    document.markPosted(actor.id(), Instant.now(clock));
                    if (document.getOffsetType() == OffsetType.PREPAYMENT) {
                        settlement.applyPrepayment(new PrepaymentOffsetCommand(document.getCompanyId(),
                                document.getBranchId(), document.getDebtType(), document.getPartnerId(),
                                DebtOffsetDocument.TYPE_CODE, document.getId(), document.getDocumentNo(),
                                document.getPostingDate(), allocations(document.getLines()),
                                document.getDescription(), actor.id()));
                    } else {
                        postArAp(document, actor.id());
                    }
                    return flow.act(actor, document, "POST", null, documents.spec());
                });
    }

    /** Đảo (bỏ đối trừ, BR-AR-04): hoàn phân bổ, hoàn trả trước, bút toán đảo nếu có. Bắt buộc lý do. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtOffsetResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + DebtOffsetService.PATH + "/" + id + "/reverse", request,
                DebtOffsetResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    DebtOffsetDocument document = flow.lock(actor, id, documents.spec());
                    LocalDate reverseDate = request.postingDate() != null ? request.postingDate()
                            : access.today();
                    document.markReversed(actor.id(), Instant.now(clock), reverseDate, request.reason().trim());
                    settlement.reverseSettlement(new SettlementReversalCommand(document.getCompanyId(),
                            document.getBranchId(), DebtOffsetDocument.TYPE_CODE, document.getId(), reverseDate,
                            actor.id()));
                    if (document.getOffsetType() == OffsetType.AR_AP) {
                        journalPosting.reverse(new ReversalRequest(document.getCompanyId(),
                                DebtOffsetDocument.TYPE_CODE, document.getId(), reverseDate,
                                "Đảo " + document.getDocumentNo() + ": " + request.reason().trim(), actor.id()));
                    }
                    return flow.act(actor, document, "REVERSE", request.reason(), documents.spec());
                });
    }

    /** Phải thu và phải trả cùng giảm một số tiền; bút toán Nợ TK phải trả / Có TK phải thu theo quy tắc. */
    private void postArAp(DebtOffsetDocument document, Long actorId) {
        Map<Long, DebtItem> items = openItems.load(document.getLines().stream().map(DebtOffsetLine::getDebtItemId)
                .toList());
        for (DebtType type : List.of(DebtType.RECEIVABLE, DebtType.PAYABLE)) {
            List<DebtOffsetLine> side = document.getLines().stream()
                    .filter(line -> items.get(line.getDebtItemId()).getDebtType() == type).toList();
            settlement.settle(new SettlementCommand(document.getCompanyId(), document.getBranchId(), type,
                    document.getPartnerId(), DebtOffsetDocument.TYPE_CODE, document.getId(),
                    document.getDocumentNo(), document.getPostingDate(), document.getTotalAmount(),
                    allocations(side), false, DebtEntryType.OFFSET, document.getDescription(), actorId));
        }
        journalPosting.post(new PostingRequest(document.getCompanyId(), document.getBranchId(),
                document.getPostingDate(), DebtOffsetDocument.TYPE_CODE, document.getId(), document.getDocumentNo(),
                description(document), actorId, List.of(new PostingLine(AR_AP_OFFSET, document.getTotalAmount(), null,
                null, null, null, document.getPartnerId(), null, null, null, null, null, description(document)))));
    }

    private static List<AllocationRequest> allocations(List<DebtOffsetLine> lines) {
        return lines.stream().map(line -> new AllocationRequest(line.getDebtItemId(), line.getId(), line.getAmount()))
                .toList();
    }

    private static String description(DebtOffsetDocument document) {
        return document.getDescription() != null ? document.getDescription()
                : "Bù trừ công nợ " + document.getDocumentNo();
    }
}
