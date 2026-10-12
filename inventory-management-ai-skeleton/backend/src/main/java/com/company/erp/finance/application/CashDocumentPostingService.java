package com.company.erp.finance.application;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.finance.application.DebtPostingService.DebtItemCommand;
import com.company.erp.finance.application.DebtPostingService.DebtReversalCommand;
import com.company.erp.finance.application.DebtSettlementService.AllocationRequest;
import com.company.erp.finance.application.DebtSettlementService.SettlementCommand;
import com.company.erp.finance.application.DebtSettlementService.SettlementResult;
import com.company.erp.finance.application.DebtSettlementService.SettlementReversalCommand;
import com.company.erp.finance.application.dto.CashDocumentResponse;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.AllocationMode;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashDocument;
import com.company.erp.finance.domain.CashDocumentLine;
import com.company.erp.finance.domain.CashLedgerEntry;
import com.company.erp.finance.domain.CashPurpose;
import com.company.erp.finance.domain.DebtEntryType;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ và đảo chứng từ thu chi trong một transaction (BR-GEN-20): khóa chứng từ → quỹ (id tăng dần) → khoản công nợ
 * (id tăng dần); ghi phân bổ / trả trước vào sổ công nợ, sổ quỹ, bút toán theo quy tắc hạch toán (mã nghiệp vụ = nội
 * dung chứng từ, tài khoản tiền lấy từ quỹ). Lỗi ở bất kỳ bước nào rollback toàn bộ.
 */
@Service
public class CashDocumentPostingService {

    static final String POST = "cash-document:post";
    static final String REVERSE = "cash-document:reverse";
    static final String ADVANCE_DUE_DAYS = "finance.advance-due-days";

    private final CashDocumentService documents;
    private final CashLedgerWriter cashLedger;
    private final DebtSettlementService settlement;
    private final DebtPostingService debtPosting;
    private final JournalPostingService journalPosting;
    private final ParameterService parameters;
    private final FinanceAccess access;
    private final IdempotencyService idempotency;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public CashDocumentPostingService(CashDocumentService documents, CashLedgerWriter cashLedger,
                                      DebtSettlementService settlement, DebtPostingService debtPosting,
                                      JournalPostingService journalPosting, ParameterService parameters,
                                      FinanceAccess access, IdempotencyService idempotency, Clock clock) {
        this.documents = documents;
        this.cashLedger = cashLedger;
        this.settlement = settlement;
        this.debtPosting = debtPosting;
        this.journalPosting = journalPosting;
        this.parameters = parameters;
        this.access = access;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashDocumentResponse post(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/cash-documents/" + id + "/post", id);
        return idempotency.execute(command, CashDocumentResponse.class, () -> {
            access.requirePermission(actor, POST);
            CashDocument document = documents.lock(actor, id);
            Instant now = Instant.now(clock);
            document.markPosted(actor.id(), now);
            Map<Long, CashAccount> accounts = cashLedger.lock(accountIds(document));
            CashAccount cash = requireActive(accounts.get(document.getCashAccountId()), "cashAccountId");
            CashAccount target = document.getTargetCashAccountId() == null ? null
                    : requireActive(accounts.get(document.getTargetCashAccountId()), "targetCashAccountId");
            if (document.getPurpose().settlesDebt()) {
                SettlementResult result = settlement.settle(settlementCommand(document, actor.id()));
                document.recordSettlement(result.allocated(), result.prepayment());
            }
            if (document.getPurpose().createdDebt() != null) {
                debtPosting.record(advance(document, actor.id()));
            }
            writeCash(document, cash, target, actor.id(), now);
            journalPosting.post(new PostingRequest(document.getCompanyId(), document.getBranchId(),
                    document.getPostingDate(), sourceType(document), document.getId(), document.getDocumentNo(),
                    journalDescription(document), actor.id(), journalLines(document, cash, target)));
            return documents.act(actor, document, "POST", null);
        });
    }

    /**
     * Đảo chứng từ đã ghi sổ (BR-GEN-21): hoàn phân bổ vào hóa đơn, dòng sổ quỹ ngược (quỹ tiền mặt không âm sau
     * đảo), bút toán đảo. Bắt buộc lý do.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashDocumentResponse reverse(CurrentUser actor, Long id, String idempotencyKey, ReverseRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/cash-documents/" + id + "/reverse", request);
        return idempotency.execute(command, CashDocumentResponse.class, () -> {
            access.requirePermission(actor, REVERSE);
            CashDocument document = documents.lock(actor, id);
            LocalDate reverseDate = request.postingDate() != null ? request.postingDate() : documents.today();
            Instant now = Instant.now(clock);
            document.markReversed(actor.id(), now, reverseDate, request.reason().trim());
            Map<Long, CashAccount> accounts = cashLedger.lock(accountIds(document));
            if (document.getPurpose().settlesDebt()) {
                settlement.reverseSettlement(new SettlementReversalCommand(document.getCompanyId(),
                        document.getBranchId(), sourceType(document), document.getId(), reverseDate, actor.id()));
            }
            if (document.getPurpose().createdDebt() != null) {
                debtPosting.reverse(new DebtReversalCommand(document.getCompanyId(), document.getBranchId(),
                        sourceType(document), document.getId(), reverseDate, document.getDocumentNo(), actor.id()));
            }
            cashLedger.reverse(document.getCompanyId(), sourceType(document), document.getId(), accounts,
                    reverseDate, actor.id(), now);
            journalPosting.reverse(new ReversalRequest(document.getCompanyId(), sourceType(document),
                    document.getId(), reverseDate, "Đảo " + document.getDocumentNo() + ": " + request.reason().trim(),
                    actor.id()));
            return documents.act(actor, document, "REVERSE", request.reason());
        });
    }

    private static List<Long> accountIds(CashDocument document) {
        return Stream.of(document.getCashAccountId(), document.getTargetCashAccountId()).filter(Objects::nonNull)
                .toList();
    }

    private static CashAccount requireActive(CashAccount account, String field) {
        if (account == null || !account.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Quỹ của chứng từ đã ngừng hoạt động ("
                    + field + ").");
        }
        return account;
    }

    private static SettlementCommand settlementCommand(CashDocument document, Long actorId) {
        List<AllocationRequest> allocations = document.getAllocationMode() == AllocationMode.AUTO ? null
                : document.getLines().stream()
                .map(line -> new AllocationRequest(line.getDebtItemId(), line.getId(), line.getAmount()))
                .toList();
        CashPurpose purpose = document.getPurpose();
        return new SettlementCommand(document.getCompanyId(), document.getBranchId(), purpose.debtType(),
                document.getPartnerId(), sourceType(document), document.getId(), document.getDocumentNo(),
                document.getPostingDate(), document.getTotalAmount(), allocations, true, purpose.debtEntryType(),
                document.getDescription(), actorId);
    }

    /** Khoản tạm ứng của nhân viên (BR-CSH-04): hạn hoàn ứng = ngày hạch toán + tham số số ngày. */
    private DebtItemCommand advance(CashDocument document, Long actorId) {
        int days = parameters.integerValue(document.getCompanyId(), ADVANCE_DUE_DAYS);
        return new DebtItemCommand(document.getCompanyId(), document.getBranchId(), document.getPurpose().createdDebt(),
                document.getPartnerId(), sourceType(document), document.getId(), null, document.getDocumentNo(),
                document.getDocumentNo(), document.getPostingDate(), document.getPostingDate().plusDays(days),
                document.getTotalAmount(), document.getPostingDate(), DebtEntryType.ADVANCE, null,
                document.getDescription(), actorId);
    }

    /** Sổ quỹ: thu ghi vào quỹ, chi ghi ra; chuyển tiền ghi ra quỹ chuyển rồi vào quỹ nhận. */
    private void writeCash(CashDocument document, CashAccount cash, CashAccount target, Long actorId, Instant now) {
        CashLedgerEntry.Reference reference = new CashLedgerEntry.Reference(sourceType(document), document.getId(),
                null, document.getDocumentNo(), document.getPartnerId(), document.getCounterpartyName());
        String code = document.getPurpose().name();
        switch (document.getPurpose().direction()) {
            case IN -> cashLedger.record(cash, document.getPostingDate(), code, true, document.getTotalAmount(),
                    reference, document.getDescription(), actorId, now);
            case OUT -> cashLedger.record(cash, document.getPostingDate(), code, false, document.getTotalAmount(),
                    reference, document.getDescription(), actorId, now);
            case TRANSFER -> {
                cashLedger.record(cash, document.getPostingDate(), code, false, document.getTotalAmount(), reference,
                        document.getDescription(), actorId, now);
                cashLedger.record(target, document.getPostingDate(), code, true, document.getTotalAmount(), reference,
                        document.getDescription(), actorId, now);
            }
        }
    }

    /**
     * Dòng hạch toán: thu / trả công nợ, tạm ứng, hoàn ứng một dòng theo số tiền chứng từ (tài khoản công nợ do quy tắc, tài khoản tiền do
     * quỹ); thu chi khác mỗi dòng một tài khoản đối ứng; chuyển tiền Nợ tài khoản quỹ nhận / Có tài khoản quỹ chuyển.
     */
    private static List<PostingLine> journalLines(CashDocument document, CashAccount cash, CashAccount target) {
        String code = document.getPurpose().name();
        Long partnerId = document.getPartnerId();
        Long cashGl = cash.getGlAccountId();
        List<PostingLine> lines = new ArrayList<>();
        switch (document.getPurpose()) {
            case CUSTOMER_RECEIPT, ADVANCE_RETURN -> lines.add(line(code, document.getTotalAmount(), cashGl, null, partnerId, null,
                    document.getDescription()));
            case SUPPLIER_PAYMENT, EMPLOYEE_ADVANCE -> lines.add(line(code, document.getTotalAmount(), null, cashGl, partnerId, null,
                    document.getDescription()));
            case OTHER_RECEIPT -> document.getLines().forEach(item -> lines.add(line(code, item.getAmount(), cashGl,
                    item.getAccountId(), partnerId, item.getId(), describe(document, item))));
            case OTHER_PAYMENT -> document.getLines().forEach(item -> lines.add(line(code, item.getAmount(),
                    item.getAccountId(), cashGl, partnerId, item.getId(), describe(document, item))));
            case INTERNAL_TRANSFER -> lines.add(line(code, document.getTotalAmount(), target.getGlAccountId(),
                    cashGl, null, null, document.getDescription()));
        }
        return lines;
    }

    private static PostingLine line(String code, BigDecimal amount, Long debitAccountId, Long creditAccountId,
                                    Long partnerId, Long sourceLineId, String description) {
        return new PostingLine(code, amount, null, null, debitAccountId, creditAccountId, partnerId, null, null, null,
                null, sourceLineId, description);
    }

    private static String describe(CashDocument document, CashDocumentLine line) {
        return line.getDescription() != null ? line.getDescription() : document.getDescription();
    }

    private static String journalDescription(CashDocument document) {
        return document.getDescription() != null ? document.getDescription() : document.getDocumentNo();
    }

    private static String sourceType(CashDocument document) {
        return document.getDocumentType().name();
    }
}
