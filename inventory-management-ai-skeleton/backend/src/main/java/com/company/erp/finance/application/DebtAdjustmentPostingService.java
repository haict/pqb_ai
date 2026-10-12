package com.company.erp.finance.application;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.finance.application.DebtPostingService.DebtItemCommand;
import com.company.erp.finance.application.DebtPostingService.DebtReversalCommand;
import com.company.erp.finance.application.DebtSettlementService.AllocationRequest;
import com.company.erp.finance.application.DebtSettlementService.SettlementCommand;
import com.company.erp.finance.application.DebtSettlementService.SettlementReversalCommand;
import com.company.erp.finance.application.dto.DebtAdjustmentResponse;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.AdjustmentDirection;
import com.company.erp.finance.domain.DebtAdjustmentDocument;
import com.company.erp.finance.domain.DebtAdjustmentLine;
import com.company.erp.finance.domain.DebtEntryType;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ và đảo điều chỉnh công nợ / thanh toán tạm ứng trong một transaction: dòng giảm phân bổ vào hóa đơn (khóa,
 * kiểm tra số còn lại), dòng tăng sinh khoản công nợ mới; bút toán mỗi dòng theo quy tắc hạch toán mã
 * {@code <loại công nợ>_<INCREASE|DECREASE>} (tài khoản công nợ do quy tắc, tài khoản đối ứng theo dòng).
 */
@Service
public class DebtAdjustmentPostingService {

    static final String POST = "debt-adjustment:post";
    static final String REVERSE = "debt-adjustment:reverse";

    private final DebtAdjustmentService documents;
    private final DebtSettlementService settlement;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final FinanceDocumentFlow flow;
    private final FinanceAccess access;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public DebtAdjustmentPostingService(DebtAdjustmentService documents, DebtSettlementService settlement,
                                        DebtPostingService debtPosting, JournalPostingService journalPosting,
                                        FinanceDocumentFlow flow, FinanceAccess access, Clock clock) {
        this.documents = documents;
        this.settlement = settlement;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.flow = flow;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtAdjustmentResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + DebtAdjustmentService.PATH + "/" + id + "/post", id,
                DebtAdjustmentResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    DebtAdjustmentDocument document = flow.lock(actor, id, documents.spec());
                    document.markPosted(actor.id(), Instant.now(clock));
                    List<DebtAdjustmentLine> decreases = lines(document, AdjustmentDirection.DECREASE);
                    if (!decreases.isEmpty()) {
                        settlement.settle(new SettlementCommand(document.getCompanyId(), document.getBranchId(),
                                document.getDebtType(), document.getPartnerId(), document.typeCode(),
                                document.getId(), document.getDocumentNo(), document.getPostingDate(),
                                sum(decreases), decreases.stream().map(line -> new AllocationRequest(
                                        line.getDebtItemId(), line.getId(), line.getAmount())).toList(), false,
                                DebtEntryType.ADJUSTMENT, document.getDescription(), actor.id()));
                    }
                    for (DebtAdjustmentLine line : lines(document, AdjustmentDirection.INCREASE)) {
                        debtPosting.record(increase(document, line, actor.id()));
                    }
                    journalPosting.post(new PostingRequest(document.getCompanyId(), document.getBranchId(),
                            document.getPostingDate(), document.typeCode(), document.getId(),
                            document.getDocumentNo(), description(document), actor.id(), journalLines(document)));
                    return flow.act(actor, document, "POST", null, documents.spec());
                });
    }

    /**
     * Đảo (BR-GEN-21): hoàn phân bổ của dòng giảm, đảo khoản công nợ của dòng tăng (đã được thanh toán thì từ chối),
     * bút toán đảo. Bắt buộc lý do.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtAdjustmentResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + DebtAdjustmentService.PATH + "/" + id + "/reverse", request,
                DebtAdjustmentResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    DebtAdjustmentDocument document = flow.lock(actor, id, documents.spec());
                    LocalDate reverseDate = request.postingDate() != null ? request.postingDate()
                            : access.today();
                    document.markReversed(actor.id(), Instant.now(clock), reverseDate, request.reason().trim());
                    if (!lines(document, AdjustmentDirection.DECREASE).isEmpty()) {
                        settlement.reverseSettlement(new SettlementReversalCommand(document.getCompanyId(),
                                document.getBranchId(), document.typeCode(), document.getId(), reverseDate,
                                actor.id()));
                    }
                    if (!lines(document, AdjustmentDirection.INCREASE).isEmpty()) {
                        debtPosting.reverse(new DebtReversalCommand(document.getCompanyId(), document.getBranchId(),
                                document.typeCode(), document.getId(), reverseDate, document.getDocumentNo(),
                                actor.id()));
                    }
                    journalPosting.reverse(new ReversalRequest(document.getCompanyId(), document.typeCode(),
                            document.getId(), reverseDate, "Đảo " + document.getDocumentNo() + ": "
                            + request.reason().trim(), actor.id()));
                    return flow.act(actor, document, "REVERSE", request.reason(), documents.spec());
                });
    }

    /** Khoản công nợ mới của dòng tăng: số hóa đơn mặc định là số chứng từ, hạn mặc định là ngày hạch toán. */
    private static DebtItemCommand increase(DebtAdjustmentDocument document, DebtAdjustmentLine line, Long actorId) {
        LocalDate dueDate = line.getDueDate() != null ? line.getDueDate() : document.getPostingDate();
        return new DebtItemCommand(document.getCompanyId(), document.getBranchId(), document.getDebtType(),
                document.getPartnerId(), document.typeCode(), document.getId(), line.getId(),
                document.getDocumentNo(), line.getInvoiceNo() != null ? line.getInvoiceNo() : document.getDocumentNo(),
                document.getDocumentDate(), dueDate.isBefore(document.getDocumentDate()) ? document.getDocumentDate()
                : dueDate, line.getAmount(), document.getPostingDate(), DebtEntryType.ADJUSTMENT, null,
                line.getDescription() != null ? line.getDescription() : document.getDescription(), actorId);
    }

    /**
     * Tài khoản công nợ do quy tắc; tài khoản đối ứng của dòng ở vế còn lại: công nợ tăng ở vế Nợ (phải thu, tạm ứng)
     * thì dòng tăng ghi đối ứng bên Có, dòng giảm ghi đối ứng bên Nợ; phải trả ngược lại.
     */
    private static List<PostingLine> journalLines(DebtAdjustmentDocument document) {
        DebtType debtType = document.getDebtType();
        return document.getLines().stream().map(line -> {
            boolean increase = line.getDirection() == AdjustmentDirection.INCREASE;
            boolean debtOnDebit = debtType.increasesOnDebit() == increase;
            return new PostingLine(debtType.name() + "_" + line.getDirection().name(), line.getAmount(), null, null,
                    debtOnDebit ? null : line.getAccountId(), debtOnDebit ? line.getAccountId() : null,
                    document.getPartnerId(), null, null, null, null, line.getId(),
                    line.getDescription() != null ? line.getDescription() : description(document));
        }).toList();
    }

    private static List<DebtAdjustmentLine> lines(DebtAdjustmentDocument document, AdjustmentDirection direction) {
        return document.getLines().stream().filter(line -> line.getDirection() == direction).toList();
    }

    private static BigDecimal sum(List<DebtAdjustmentLine> lines) {
        return lines.stream().map(DebtAdjustmentLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String description(DebtAdjustmentDocument document) {
        return document.getDescription() != null ? document.getDescription() : document.getDocumentNo();
    }
}
