package com.company.erp.purchasing.application;

import com.company.erp.accounting.application.JournalPostingService;
import com.company.erp.accounting.application.JournalPostingService.PostingLine;
import com.company.erp.accounting.application.JournalPostingService.PostingRequest;
import com.company.erp.accounting.application.JournalPostingService.ReversalRequest;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.PurchaseCostAllocationResponse;
import com.company.erp.purchasing.application.dto.ReverseRequest;
import com.company.erp.purchasing.domain.PurchaseCostAllocation;
import com.company.erp.purchasing.domain.PurchaseCostEntry;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseInvoice;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseCostEntryRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseInvoiceRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReceiptRepository;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ / đảo phân bổ chi phí mua hàng (BR-PUR-12): số đã phân bổ của dòng dịch vụ hóa đơn, điều chỉnh giá trị nhập
 * theo dòng phiếu nhận (LANDED_COST, Giai đoạn 8 tính lại giá vốn), bút toán chuyển chi phí vào giá trị hàng theo quy
 * tắc hạch toán PBC. Thứ tự khóa: chứng từ → hóa đơn (id tăng dần) → bút toán. READ COMMITTED.
 */
@Service
public class PurchaseCostAllocationPostingService {

    static final String POST = "purchase-cost-allocation:post";
    static final String REVERSE = "purchase-cost-allocation:reverse";
    static final String LANDED_COST = "LANDED_COST";
    private static final Logger log = LoggerFactory.getLogger(PurchaseCostAllocationPostingService.class);

    private final PurchaseCostAllocationService allocations;
    private final PurchaseInvoiceRepository invoiceRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseCostEntryRepository costRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final JournalPostingService journalPosting;
    private final BusinessCalendar calendar;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public PurchaseCostAllocationPostingService(PurchaseCostAllocationService allocations,
                                                PurchaseInvoiceRepository invoiceRepository,
                                                PurchaseReceiptRepository receiptRepository,
                                                PurchaseCostEntryRepository costRepository, PurchaseDocumentFlow flow,
                                                PurchasingAccess access, JournalPostingService journalPosting,
                                                BusinessCalendar calendar, Clock clock) {
        this.allocations = allocations;
        this.invoiceRepository = invoiceRepository;
        this.receiptRepository = receiptRepository;
        this.costRepository = costRepository;
        this.flow = flow;
        this.access = access;
        this.journalPosting = journalPosting;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseCostAllocationResponse post(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PurchaseCostAllocationService.PATH + "/" + id + "/post", id,
                PurchaseCostAllocationResponse.class, () -> {
                    access.requirePermission(actor, POST);
                    PurchaseCostAllocation document = flow.lock(actor, id, allocations.spec());
                    if (document.getStatus() != PurchaseDocumentStatus.APPROVED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ "
                                + document.getDocumentNo() + " ở trạng thái " + document.getStatus()
                                + ", chỉ ghi sổ chứng từ đã duyệt.");
                    }
                    applySources(document, BigDecimal.ONE);
                    recordCosts(actor, document);
                    journalPosting.post(new PostingRequest(document.getCompanyId(), document.getBranchId(),
                            document.getPostingDate(), PurchaseCostAllocation.TYPE_CODE, document.getId(),
                            document.getDocumentNo(), description(document), actor.id(), journalLines(document)));
                    document.markPosted(actor.id(), Instant.now(clock));
                    log.info("Ghi sổ phân bổ chi phí mua {}", document.getDocumentNo());
                    return flow.act(actor, document, "POST", null, allocations.spec());
                });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseCostAllocationResponse reverse(CurrentUser actor, Long id, String key, ReverseRequest request) {
        return flow.idempotent(actor, key, "POST " + PurchaseCostAllocationService.PATH + "/" + id + "/reverse",
                request, PurchaseCostAllocationResponse.class, () -> {
                    access.requirePermission(actor, REVERSE);
                    PurchaseCostAllocation document = flow.lock(actor, id, allocations.spec());
                    if (document.getStatus() != PurchaseDocumentStatus.POSTED) {
                        throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ "
                                + document.getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
                    }
                    LocalDate date = request.postingDate() != null ? request.postingDate() : calendar.today();
                    String reason = request.reason().trim();
                    applySources(document, BigDecimal.ONE.negate());
                    Instant now = Instant.now(clock);
                    costRepository.saveAll(costRepository.findOriginals(PurchaseCostAllocation.TYPE_CODE,
                            document.getId()).stream().map(entry -> entry.reversed(date, actor.id(), now)).toList());
                    journalPosting.reverse(new ReversalRequest(document.getCompanyId(),
                            PurchaseCostAllocation.TYPE_CODE, document.getId(), date,
                            "Đảo " + document.getDocumentNo() + ": " + reason, actor.id()));
                    document.markReversed(actor.id(), now, date, reason);
                    return flow.act(actor, document, "REVERSE", reason, allocations.spec());
                });
    }

    /** Khóa hóa đơn theo id tăng dần; cộng / trừ số đã phân bổ của dòng dịch vụ. */
    private void applySources(PurchaseCostAllocation document, BigDecimal sign) {
        List<Long> lineIds = document.getSources().stream().map(PurchaseCostAllocation.Source::getInvoiceLineId)
                .toList();
        for (Long invoiceId : invoiceRepository.findIdsByLineIds(lineIds)) {
            PurchaseInvoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                    .orElseThrow(BusinessException::notFound);
            for (PurchaseCostAllocation.Source source : document.getSources()) {
                if (invoice.getLines().stream().anyMatch(line -> line.getId().equals(source.getInvoiceLineId()))) {
                    invoice.recordAllocation(source.getInvoiceLineId(), source.getAmount().multiply(sign),
                            "sources[" + (source.getLineNo() - 1) + "]");
                }
            }
            invoiceRepository.save(invoice);
        }
    }

    /** Điều chỉnh giá trị nhập theo dòng phiếu nhận; phiếu phải còn ở trạng thái đã ghi sổ, nhập kho. */
    private void recordCosts(CurrentUser actor, PurchaseCostAllocation document) {
        Map<Long, PurchaseReceipt> receipts = new HashMap<>();
        receiptRepository.findByLineIds(document.getTargets().stream()
                        .map(PurchaseCostAllocation.Target::getReceiptLineId).toList())
                .forEach(receipt -> receipt.getLines().forEach(line -> receipts.put(line.getId(), receipt)));
        Instant now = Instant.now(clock);
        List<PurchaseCostEntry> entries = new ArrayList<>();
        for (PurchaseCostAllocation.Target target : document.getTargets()) {
            PurchaseReceipt receipt = receipts.get(target.getReceiptLineId());
            if (receipt == null || receipt.getStatus() != PurchaseDocumentStatus.POSTED || !receipt.isStocked()) {
                throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu nhận hàng của dòng phân bổ "
                        + target.getLineNo() + " đã đảo; lập lại phân bổ.");
            }
            if (target.getAllocatedAmount().signum() == 0) {
                continue;
            }
            entries.add(new PurchaseCostEntry(document.getCompanyId(), receipt.line(target.getReceiptLineId(),
                    "targets"), receipt.getWarehouseId(), PurchaseCostEntry.EntryType.LANDED_COST,
                    document.getPostingDate(), target.getAllocatedAmount(), new PurchaseCostEntry.Reference(
                    PurchaseCostAllocation.TYPE_CODE, document.getId(), target.getId(), document.getDocumentNo()),
                    actor.id(), now));
        }
        costRepository.saveAll(entries);
    }

    /** Một dòng hạch toán cho mỗi dòng chi phí, theo loại hàng của dịch vụ (dev: Nợ 1562 / Có 6422). */
    private List<PostingLine> journalLines(PurchaseCostAllocation document) {
        Map<Long, ProductInfo> products = access.products(document.getSources().stream()
                .map(PurchaseCostAllocation.Source::getProductId).toList());
        return document.getSources().stream().map(source -> {
            ProductInfo product = products.get(source.getProductId());
            return new PostingLine(LANDED_COST, source.getAmount(), product == null ? null : product.productTypeCode(),
                    null, null, null, null, source.getProductId(), null, null, null, source.getId(),
                    "Phân bổ chi phí mua " + document.getDocumentNo());
        }).toList();
    }

    private static String description(PurchaseCostAllocation document) {
        return document.getDescription() != null ? document.getDescription()
                : "Phân bổ chi phí mua hàng " + document.getDocumentNo();
    }
}
