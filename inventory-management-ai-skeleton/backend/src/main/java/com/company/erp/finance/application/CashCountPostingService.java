package com.company.erp.finance.application;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.finance.application.dto.CashCountResponse;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashCount;
import com.company.erp.finance.domain.CashLedgerEntry;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ và đảo kiểm kê quỹ (BR-CSH-05): khóa quỹ, so số đếm với số dư sổ quỹ đến hết ngày kiểm kê; chênh lệch thừa
 * ghi tiền vào ({@code CASH_SURPLUS}), thiếu ghi tiền ra ({@code CASH_SHORTAGE}) và bút toán theo quy tắc hạch toán.
 * Không chênh lệch thì chỉ chốt số dư, không sinh sổ quỹ / bút toán.
 */
@Service
public class CashCountPostingService {

    static final String POST = "cash-count:post";
    static final String REVERSE = "cash-count:reverse";
    static final String SURPLUS = "CASH_SURPLUS";
    static final String SHORTAGE = "CASH_SHORTAGE";

    private final CashCountService documents;
    private final CashLedgerWriter cashLedger;
    private final JournalPostingService journalPosting;
    private final FinanceDocumentFlow flow;
    private final FinanceAccess access;
    private final Clock clock;

    public CashCountPostingService(CashCountService documents, CashLedgerWriter cashLedger,
                                   JournalPostingService journalPosting, FinanceDocumentFlow flow,
                                   FinanceAccess access, Clock clock) {
        this.documents = documents;
        this.cashLedger = cashLedger;
        this.journalPosting = journalPosting;
        this.flow = flow;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashCountResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + CashCountService.PATH + "/" + id + "/post", id,
                CashCountResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    CashCount count = flow.lock(actor, id, documents.spec());
                    Instant now = Instant.now(clock);
                    count.markPosted(actor.id(), now);
                    CashAccount account = cashLedger.lock(List.of(count.getCashAccountId()))
                            .get(count.getCashAccountId());
                    BigDecimal difference = count.recordBookBalance(documents.bookBalance(count));
                    if (difference.signum() != 0) {
                        boolean surplus = difference.signum() > 0;
                        String code = surplus ? SURPLUS : SHORTAGE;
                        cashLedger.record(account, count.getPostingDate(), code, surplus, difference.abs(),
                                new CashLedgerEntry.Reference(CashCount.TYPE_CODE, count.getId(), null,
                                        count.getDocumentNo(), null, null), description(count), actor.id(), now);
                        Long cashGl = account.getGlAccountId();
                        journalPosting.post(new PostingRequest(count.getCompanyId(), count.getBranchId(),
                                count.getPostingDate(), CashCount.TYPE_CODE, count.getId(), count.getDocumentNo(),
                                description(count), actor.id(), List.of(new PostingLine(code, difference.abs(), null,
                                null, surplus ? cashGl : null, surplus ? null : cashGl, null, null, null, null, null,
                                null, description(count)))));
                    }
                    return flow.act(actor, count, "POST", null, documents.spec());
                });
    }

    /** Đảo: dòng sổ quỹ ngược (quỹ không âm sau đảo), bút toán đảo. Bắt buộc lý do. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashCountResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + CashCountService.PATH + "/" + id + "/reverse", request,
                CashCountResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    CashCount count = flow.lock(actor, id, documents.spec());
                    LocalDate reverseDate = request.postingDate() != null ? request.postingDate() : access.today();
                    Instant now = Instant.now(clock);
                    count.markReversed(actor.id(), now, reverseDate, request.reason().trim());
                    if (count.getDifferenceAmount().signum() != 0) {
                        Map<Long, CashAccount> accounts = cashLedger.lock(List.of(count.getCashAccountId()));
                        cashLedger.reverse(count.getCompanyId(), CashCount.TYPE_CODE, count.getId(), accounts,
                                reverseDate, actor.id(), now);
                        journalPosting.reverse(new ReversalRequest(count.getCompanyId(), CashCount.TYPE_CODE,
                                count.getId(), reverseDate, "Đảo " + count.getDocumentNo() + ": "
                                + request.reason().trim(), actor.id()));
                    }
                    return flow.act(actor, count, "REVERSE", request.reason(), documents.spec());
                });
    }

    private static String description(CashCount count) {
        return count.getDescription() != null ? count.getDescription() : "Kiểm kê quỹ " + count.getDocumentNo();
    }
}
