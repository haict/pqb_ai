package com.company.erp.inventory.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.PostingCommand.ProductWarehouse;
import com.company.erp.inventory.application.dto.InventoryDocumentResponse;
import com.company.erp.inventory.application.dto.ReceiveRequest;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.system.application.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ chứng từ kho (CLAUDE.md mục 9): khóa chứng từ, kiểm tra trạng thái và quyền, lập biến động, gọi
 * {@link InventoryPostingService}, chuyển trạng thái, audit - trong một transaction. Mức cô lập READ COMMITTED: đọc
 * số dư mới nhất sau khi khóa và không khóa khoảng (gap lock) khi tạo dòng số dư mới (docs/02 mục chiến lược khóa).
 */
@Service
public class InventoryDocumentPostingService {

    private static final Logger log = LoggerFactory.getLogger(InventoryDocumentPostingService.class);

    private final InventoryDocumentRepository documentRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryDocumentService documentService;
    private final DocumentPostingPlanner planner;
    private final InventoryPostingService postingService;
    private final InventoryDocumentMapper mapper;
    private final InventoryAccess access;
    private final IdempotencyService idempotency;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public InventoryDocumentPostingService(InventoryDocumentRepository documentRepository,
                                           InventoryTransactionRepository transactionRepository,
                                           InventoryDocumentService documentService, DocumentPostingPlanner planner,
                                           InventoryPostingService postingService, InventoryDocumentMapper mapper,
                                           InventoryAccess access, IdempotencyService idempotency,
                                           BusinessCalendar calendar, Clock clock) {
        this.documentRepository = documentRepository;
        this.transactionRepository = transactionRepository;
        this.documentService = documentService;
        this.planner = planner;
        this.postingService = postingService;
        this.mapper = mapper;
        this.access = access;
        this.idempotency = idempotency;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryDocumentResponse post(CurrentUser actor, Long id, String idempotencyKey) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/inventory-documents/" + id + "/post", id);
        return idempotency.execute(command, InventoryDocumentResponse.class, () -> {
            InventoryDocument document = documentService.lockVisible(actor, id);
            access.requirePermission(actor, document.getDocumentType().permission("post"));
            if (document.getStatus() != InventoryDocumentStatus.APPROVED) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ " + document.getDocumentNo()
                        + " ở trạng thái " + document.getStatus() + ", chỉ ghi sổ chứng từ đã duyệt.");
            }
            postApproved(document, actor.id());
            documentRepository.saveAndFlush(document);
            InventoryDocumentResponse response = mapper.toResponse(document, actor);
            documentService.audit(actor, "POST", document, null, null, response.transactions());
            log.info("Ghi sổ chứng từ kho {} ({})", document.getDocumentNo(), document.getStatus());
            return response;
        });
    }

    /** Nhận hàng chuyển kho khác chi nhánh: người nhận cần phạm vi kho đích (BR-INV-30). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryDocumentResponse receive(CurrentUser actor, Long id, String idempotencyKey,
                                             ReceiveRequest request) {
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/inventory-documents/" + id + "/receive", request);
        return idempotency.execute(command, InventoryDocumentResponse.class, () -> {
            InventoryDocument document = documentService.lockVisible(actor, id);
            access.requirePermission(actor, document.getDocumentType().permission("post"));
            if (!access.canAccessWarehouse(actor, document.getDestinationWarehouseId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người thuộc kho đích được xác nhận nhận hàng.");
            }
            if (document.getStatus() != InventoryDocumentStatus.IN_TRANSIT) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ " + document.getDocumentNo()
                        + " không ở trạng thái đang vận chuyển.");
            }
            LocalDate postingDate = request != null && request.postingDate() != null ? request.postingDate()
                    : calendar.today();
            if (postingDate.isBefore(document.getPostingDate())) {
                throw BusinessException.invalidField("postingDate", "Ngày nhận không được trước ngày xuất chuyển.");
            }
            List<InventoryTransaction> shipped = transactionRepository
                    .findByReferenceTypeAndReferenceIdOrderByIdAsc(document.getDocumentType().name(), id);
            post(document, postingDate, actor.id(), planner.receive(document, shipped), Set.of());
            document.markReceived(actor.id(), Instant.now(clock), postingDate);
            documentRepository.saveAndFlush(document);
            InventoryDocumentResponse response = mapper.toResponse(document, actor);
            documentService.audit(actor, "RECEIVE", document, null, null, response.status());
            return response;
        });
    }

    /**
     * Ghi sổ chứng từ đã duyệt; dùng cả cho chứng từ do hệ thống sinh (điều chỉnh từ kiểm kê). Phải gọi trong
     * transaction READ COMMITTED đã khóa chứng từ.
     */
    void postApproved(InventoryDocument document, Long actorId) {
        Instant now = Instant.now(clock);
        switch (document.getDocumentType()) {
            case PCK -> postTransfer(document, actorId, now);
            case PCL -> {
                List<PostedMovement> outs = post(document, document.getPostingDate(), actorId,
                        planner.statusOuts(document), Set.of());
                post(document, document.getPostingDate(), actorId, planner.statusIns(document, outs), Set.of());
                document.markPosted(actorId, now);
            }
            case PDK -> postReversal(document, actorId, now);
            default -> {
                post(document, document.getPostingDate(), actorId, planner.plan(document, actorId), Set.of());
                document.markPosted(actorId, now);
            }
        }
    }

    /**
     * Chuyển kho: xuất kho nguồn rồi nhập kho đích (cùng chi nhánh) hoặc kho "đang vận chuyển" (khác chi nhánh). Khóa
     * cả (hàng, kho nhận) ngay lần ghi đầu để mọi khóa lấy theo một thứ tự.
     */
    private void postTransfer(InventoryDocument document, Long actorId, Instant now) {
        boolean crossBranch = document.getTransitWarehouseId() != null;
        Long target = crossBranch ? document.getTransitWarehouseId() : document.getDestinationWarehouseId();
        Set<ProductWarehouse> targetKeys = document.getLines().stream()
                .map(line -> new ProductWarehouse(line.getProductId(), target))
                .collect(Collectors.toSet());
        List<PostedMovement> outs = post(document, document.getPostingDate(), actorId,
                planner.transferOuts(document), targetKeys);
        post(document, document.getPostingDate(), actorId,
                planner.transferIns(document, outs, target, !crossBranch), Set.of());
        if (crossBranch) {
            document.markShipped(actorId, now);
        } else {
            document.markPosted(actorId, now);
        }
    }

    /** Đảo: dòng ngược đúng từng dòng sổ gốc; chứng từ gốc chuyển REVERSED (BR-GEN-21). */
    private void postReversal(InventoryDocument reversal, Long actorId, Instant now) {
        InventoryDocument original = documentRepository.findByIdForUpdate(reversal.getReversalOfId())
                .orElseThrow(() -> new IllegalStateException("Thiếu chứng từ gốc của " + reversal.getDocumentNo()));
        if (original.getStatus() != InventoryDocumentStatus.POSTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ gốc " + original.getDocumentNo()
                    + " không còn ở trạng thái đã ghi sổ.");
        }
        List<InventoryTransaction> originals = transactionRepository
                .findByReferenceTypeAndReferenceIdOrderByIdAsc(original.getDocumentType().name(), original.getId());
        post(reversal, reversal.getPostingDate(), actorId,
                planner.reversal(originals, original.getTransitWarehouseId()), Set.of());
        original.markReversed();
        documentRepository.saveAndFlush(original);
        reversal.markPosted(actorId, now);
    }

    private List<PostedMovement> post(InventoryDocument document, LocalDate postingDate, Long actorId,
                                      List<StockMovement> movements, Set<ProductWarehouse> extraLockKeys) {
        return postingService.post(new PostingCommand(document.getCompanyId(), document.getDocumentType().name(),
                document.getId(), document.getDocumentNo(), postingDate, document.getPartnerId(), actorId, movements,
                document.getIssuePolicyId(), document.getStockCountId(), extraLockKeys));
    }
}
