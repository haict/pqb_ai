package com.company.erp.finance.application;

import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.finance.application.DebtPostingService.DebtItemCommand;
import com.company.erp.finance.application.DebtPostingService.DebtReversalCommand;
import com.company.erp.finance.application.dto.DebtOpeningResponse;
import com.company.erp.finance.application.dto.ReverseRequest;
import com.company.erp.finance.domain.DebtEntryType;
import com.company.erp.finance.domain.DebtOpeningDocument;
import com.company.erp.finance.domain.DebtOpeningLine;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ (thẳng từ nháp, D-60) và đảo chứng từ công nợ đầu kỳ: mỗi dòng thành một khoản công nợ và một dòng sổ chi
 * tiết (loại OPENING).
 * Không sinh bút toán: số dư đầu kỳ của tài khoản công nợ nhập bằng công cụ số dư đầu kỳ kế toán (Giai đoạn 11), tránh
 * ghi hai lần `[Giả định]`, kế toán trưởng xác nhận trước UAT.
 */
@Service
public class DebtOpeningPostingService {

    static final String POST = "debt-opening:post";

    private final DebtOpeningService documents;
    private final DebtPostingService debtPosting;
    private final FinanceAccess access;
    private final IdempotencyService idempotency;
    private final Clock clock;

    public DebtOpeningPostingService(DebtOpeningService documents, DebtPostingService debtPosting,
                                     FinanceAccess access, IdempotencyService idempotency, Clock clock) {
        this.documents = documents;
        this.debtPosting = debtPosting;
        this.access = access;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtOpeningResponse post(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/debt-openings/" + id + "/post", id);
        return idempotency.execute(command, DebtOpeningResponse.class, () -> {
            access.requirePermission(actor, POST);
            DebtOpeningDocument document = documents.lock(actor, id);
            document.markPosted(actor.id(), Instant.now(clock));
            for (DebtOpeningLine line : document.getLines()) {
                debtPosting.record(new DebtItemCommand(document.getCompanyId(), document.getBranchId(),
                        document.getDebtType(), line.getPartnerId(), DebtOpeningService.DOCUMENT_TYPE,
                        document.getId(), line.getId(), document.getDocumentNo(), null,
                        originDate(document, line), line.getDueDate(), line.getAmount(), document.getPostingDate(),
                        DebtEntryType.OPENING, line.getSalesUserId(), line.getNote(), actor.id()));
            }
            return documents.act(actor, document, "POST", null);
        });
    }

    /**
     * Ngày phát sinh của khoản công nợ: không có ngày hóa đơn (D-60) nên lấy ngày chứng từ, trừ khi hạn thanh toán sớm
     * hơn (nợ cũ đã quá hạn) thì lấy hạn thanh toán, giữ ràng buộc hạn không trước ngày phát sinh `[Giả định]`.
     */
    private static LocalDate originDate(DebtOpeningDocument document, DebtOpeningLine line) {
        return line.getDueDate().isBefore(document.getDocumentDate()) ? line.getDueDate() : document.getDocumentDate();
    }

    /**
     * Đảo chứng từ đã ghi sổ (BR-GEN-21): người có quyền ghi sổ thực hiện (D-60), bắt buộc lý do. Khoản công nợ đã
     * thanh toán / đối trừ thì từ chối.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtOpeningResponse reverse(CurrentUser actor, Long id, String idempotencyKey, ReverseRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/debt-openings/" + id + "/reverse", request);
        return idempotency.execute(command, DebtOpeningResponse.class, () -> {
            access.requirePermission(actor, POST);
            DebtOpeningDocument document = documents.lock(actor, id);
            LocalDate reverseDate = request.postingDate() != null ? request.postingDate() : documents.today();
            document.markReversed(actor.id(), Instant.now(clock), reverseDate, request.reason().trim());
            debtPosting.reverse(new DebtReversalCommand(document.getCompanyId(), document.getBranchId(),
                    DebtOpeningService.DOCUMENT_TYPE, document.getId(), reverseDate,
                    document.getDocumentNo(), actor.id()));
            return documents.act(actor, document, "REVERSE", request.reason());
        });
    }
}
