package com.company.erp.finance.application;

import com.company.erp.common.domain.PeriodModule;
import com.company.erp.finance.domain.DebtEntryType;
import com.company.erp.finance.application.DebtSettlementService.AllocationRequest;
import com.company.erp.finance.application.DebtSettlementService.SettlementCommand;
import com.company.erp.finance.application.DebtSettlementService.SettlementReversalCommand;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtItemStatus;
import com.company.erp.finance.domain.DebtLedgerEntry;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import com.company.erp.finance.infrastructure.persistence.DebtLedgerEntryRepository;
import com.company.erp.system.application.PeriodGuard;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai ghi sổ công nợ (ADR-12): chứng từ phát sinh công nợ (công nợ đầu kỳ, hóa đơn bán / mua ở Giai đoạn 5,
 * 6), tạm ứng, điều chỉnh tăng) gọi trong transaction ghi sổ của mình. Thanh toán / bù trừ ở
 * {@link DebtSettlementService}. Không module nào ghi {@code debt_items} / sổ chi tiết công nợ trực tiếp.
 */
@Service
public class DebtPostingService {

    /**
     * Khoản công nợ mới của một chứng từ nguồn (hoặc một dòng của nó).
     *
     * @param documentDate ngày hóa đơn / chứng từ, gốc tính tuổi nợ
     * @param postingDate  ngày hạch toán vào sổ công nợ (kiểm tra kỳ công nợ)
     */
    public record DebtItemCommand(Long companyId, Long branchId, DebtType debtType, Long partnerId, String sourceType,
                                  Long sourceId, Long sourceLineId, String sourceNo, String invoiceNo,
                                  LocalDate documentDate, LocalDate dueDate, BigDecimal amount, LocalDate postingDate,
                                  DebtEntryType entryType, Long salesUserId, String description, Long actorId) {
    }

    /** Đảo toàn bộ khoản công nợ của chứng từ nguồn vào ngày {@code postingDate}. */
    public record DebtReversalCommand(Long companyId, Long branchId, String sourceType, Long sourceId,
                                      LocalDate postingDate, String reversalNo, Long actorId) {
    }

    /** Phía công nợ của hóa đơn, cho module khác gọi mà không phụ thuộc enum domain của công nợ. */
    public enum InvoiceSide {
        RECEIVABLE,
        PAYABLE
    }

    /** Khoản công nợ theo hóa đơn mua / bán (một khoản cho cả hóa đơn). */
    public record InvoiceDebtCommand(Long companyId, Long branchId, InvoiceSide side, Long partnerId,
                                     String sourceType, Long sourceId, String sourceNo, String invoiceNo,
                                     LocalDate invoiceDate, LocalDate dueDate, BigDecimal amount,
                                     LocalDate postingDate, Long salesUserId, String description, Long actorId) {
    }

    /** Loại giảm công nợ theo hóa đơn: trả hàng hoặc giảm giá (mua / bán). */
    public enum CreditKind {
        RETURN,
        DISCOUNT
    }

    /**
     * Giảm công nợ của hóa đơn gốc bởi chứng từ trả hàng / giảm giá (BR-PUR-14, BR-SAL-32, BR-SAL-36): trừ vào khoản
     * công nợ của hóa đơn tối đa số còn lại; phần vượt (hóa đơn đã thanh toán) ghi thành trả trước của đối tượng để
     * bù trừ hoặc hoàn tiền sau.
     */
    public record InvoiceCreditCommand(Long companyId, Long branchId, InvoiceSide side, Long partnerId,
                                       String invoiceSourceType, Long invoiceSourceId, String sourceType,
                                       Long sourceId, String sourceNo, LocalDate postingDate, BigDecimal amount,
                                       CreditKind kind, String description, Long actorId) {
    }

    private final DebtItemRepository itemRepository;
    private final DebtLedgerEntryRepository ledgerRepository;
    private final DebtSettlementService settlement;
    private final PeriodGuard periodGuard;
    private final Clock clock;

    public DebtPostingService(DebtItemRepository itemRepository, DebtLedgerEntryRepository ledgerRepository,
                              DebtSettlementService settlement, PeriodGuard periodGuard, Clock clock) {
        this.itemRepository = itemRepository;
        this.ledgerRepository = ledgerRepository;
        this.settlement = settlement;
        this.periodGuard = periodGuard;
        this.clock = clock;
    }

    /** Giảm công nợ theo hóa đơn gốc; trả số đã trừ vào hóa đơn (phần còn lại là trả trước). */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reduceInvoice(InvoiceCreditCommand command) {
        DebtType debtType = DebtType.valueOf(command.side().name());
        // Không khóa ở đây: settle khóa trả trước rồi khoản công nợ (cùng thứ tự với thu / chi) và kiểm tra lại.
        List<DebtItem> items = itemRepository.findBySource(command.companyId(), command.invoiceSourceType(),
                command.invoiceSourceId());
        List<AllocationRequest> plan = new ArrayList<>();
        BigDecimal left = command.amount();
        for (DebtItem item : items) {
            BigDecimal take = left.min(item.getRemainingAmount());
            if (item.getStatus() == DebtItemStatus.REVERSED || take.signum() <= 0 || item.getDebtType() != debtType) {
                continue;
            }
            plan.add(new AllocationRequest(item.getId(), null, take));
            left = left.subtract(take);
        }
        return settlement.settle(new SettlementCommand(command.companyId(), command.branchId(), debtType,
                command.partnerId(), command.sourceType(), command.sourceId(), command.sourceNo(),
                command.postingDate(), command.amount(), plan, true, command.kind() == CreditKind.RETURN
                ? DebtEntryType.RETURN : DebtEntryType.DISCOUNT, command.description(), command.actorId()))
                .allocated();
    }

    /** Đảo chứng từ giảm công nợ (hoàn phần đã trừ vào hóa đơn và phần trả trước, BR-GEN-21). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseReduction(DebtReversalCommand command) {
        settlement.reverseSettlement(new SettlementReversalCommand(command.companyId(), command.branchId(),
                command.sourceType(), command.sourceId(), command.postingDate(), command.actorId()));
    }

    /** Ghi khoản công nợ và dòng sổ chi tiết làm tăng công nợ. Trả id khoản công nợ. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long record(DebtItemCommand command) {
        periodGuard.assertOpen(command.companyId(), command.branchId(), PeriodModule.DEBT, command.postingDate());
        DebtItem item = itemRepository.saveAndFlush(DebtItem.open(new DebtItem.Source(command.companyId(),
                command.branchId(), command.debtType(), command.partnerId(), command.sourceType(), command.sourceId(),
                command.sourceLineId(), command.sourceNo(), command.invoiceNo(), command.documentDate(),
                command.dueDate(), command.amount(), command.salesUserId(), command.description())));
        ledgerRepository.save(DebtLedgerEntry.of(item, command.postingDate(), command.entryType(), true,
                command.amount(), new DebtLedgerEntry.Reference(command.sourceType(), command.sourceId(),
                        command.sourceLineId(), command.sourceNo()), command.description(), command.actorId(),
                Instant.now(clock)));
        return item.getId();
    }

    /** Ghi công nợ theo hóa đơn (dòng sổ loại {@code INVOICE}, D-34, D-35). Trả id khoản công nợ. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long recordInvoice(InvoiceDebtCommand command) {
        return record(new DebtItemCommand(command.companyId(), command.branchId(), DebtType.valueOf(command.side().name()),
                command.partnerId(), command.sourceType(), command.sourceId(), null, command.sourceNo(),
                command.invoiceNo(), command.invoiceDate(), command.dueDate(), command.amount(), command.postingDate(),
                DebtEntryType.INVOICE, command.salesUserId(), command.description(), command.actorId()));
    }

    /**
     * Đảo công nợ của chứng từ nguồn: khóa các khoản theo id tăng dần; khoản đã thanh toán / đối trừ thì từ chối
     * ({@code HAS_DEPENDENT_DOCUMENTS}); sinh dòng sổ ngược đúng từng dòng gốc (BR-GEN-21).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(DebtReversalCommand command) {
        periodGuard.assertOpen(command.companyId(), command.branchId(), PeriodModule.DEBT, command.postingDate());
        List<DebtItem> items = itemRepository.findBySourceForUpdate(command.companyId(), command.sourceType(),
                command.sourceId());
        if (items.isEmpty()) {
            throw new IllegalStateException("Chứng từ " + command.sourceType() + "#" + command.sourceId()
                    + " chưa ghi sổ công nợ");
        }
        items.forEach(DebtItem::reverse);
        itemRepository.saveAllAndFlush(items);
        Instant now = Instant.now(clock);
        List<DebtLedgerEntry> originals = ledgerRepository.findOriginals(items.stream().map(DebtItem::getId).toList(),
                command.sourceType(), command.sourceId());
        ledgerRepository.saveAll(originals.stream()
                .map(original -> original.reversed(command.postingDate(), new DebtLedgerEntry.Reference(
                        command.sourceType(), command.sourceId(), original.getReferenceLineId(),
                        command.reversalNo()), command.actorId(), now))
                .toList());
    }
}
