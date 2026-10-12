package com.company.erp.finance.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.PeriodModule;
import com.company.erp.finance.domain.DebtEntryType;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtLedgerEntry;
import com.company.erp.finance.domain.DebtPrepayment;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.PaymentAllocation;
import com.company.erp.finance.domain.PaymentAllocationStatus;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import com.company.erp.finance.infrastructure.persistence.DebtLedgerEntryRepository;
import com.company.erp.finance.infrastructure.persistence.DebtPrepaymentRepository;
import com.company.erp.finance.infrastructure.persistence.PaymentAllocationRepository;
import com.company.erp.system.application.PeriodGuard;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai thanh toán / bù trừ công nợ (BR-AR-03, 04, BR-AP-02): phân bổ vào khoản công nợ, tiền trả trước,
 * dùng trả trước để bù trừ, hoàn khi đảo. Gọi trong transaction ghi sổ của chứng từ nguồn. Thứ tự khóa cố định:
 * dòng trả trước của đối tượng → khoản công nợ theo id tăng dần (docs/02 mục 6).
 */
@Service
public class DebtSettlementService {

    /**
     * Thanh toán / bù trừ giảm công nợ của một đối tượng trong chi nhánh.
     *
     * @param allocations     phân bổ chỉ định; {@code null} = tự động, khoản đến hạn sớm nhất trước
     * @param allowPrepayment phần chưa phân bổ ghi trả trước; {@code false} thì tổng phân bổ phải bằng số tiền
     */
    public record SettlementCommand(Long companyId, Long branchId, DebtType debtType, Long partnerId,
                                    String sourceType, Long sourceId, String sourceNo, LocalDate postingDate,
                                    BigDecimal amount, List<AllocationRequest> allocations, boolean allowPrepayment,
                                    DebtEntryType entryType, String description, Long actorId) {
    }

    /** Phân bổ chỉ định: khoản công nợ, dòng chứng từ nguồn, số tiền. */
    public record AllocationRequest(Long debtItemId, Long sourceLineId, BigDecimal amount) {
    }

    public record SettlementResult(BigDecimal allocated, BigDecimal prepayment) {
    }

    /** Dùng tiền trả trước của đối tượng để trừ vào các khoản công nợ chỉ định (bù trừ trả trước). */
    public record PrepaymentOffsetCommand(Long companyId, Long branchId, DebtType debtType, Long partnerId,
                                          String sourceType, Long sourceId, String sourceNo, LocalDate postingDate,
                                          List<AllocationRequest> allocations, String description, Long actorId) {
    }

    /** Hoàn toàn bộ phân bổ, trả trước của chứng từ nguồn vào ngày {@code postingDate}. */
    public record SettlementReversalCommand(Long companyId, Long branchId, String sourceType, Long sourceId,
                                            LocalDate postingDate, Long actorId) {
    }

    private final DebtItemRepository itemRepository;
    private final DebtLedgerEntryRepository ledgerRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final DebtPrepaymentRepository prepaymentRepository;
    private final PeriodGuard periodGuard;
    private final Clock clock;

    public DebtSettlementService(DebtItemRepository itemRepository, DebtLedgerEntryRepository ledgerRepository,
                                 PaymentAllocationRepository allocationRepository,
                                 DebtPrepaymentRepository prepaymentRepository, PeriodGuard periodGuard,
                                 Clock clock) {
        this.itemRepository = itemRepository;
        this.ledgerRepository = ledgerRepository;
        this.allocationRepository = allocationRepository;
        this.prepaymentRepository = prepaymentRepository;
        this.periodGuard = periodGuard;
        this.clock = clock;
    }

    /**
     * Phân bổ vào khoản công nợ: khóa trả trước (nếu được ghi), rồi các khoản theo id tăng dần, kiểm tra số còn lại sau
     * khi khóa (BR-GEN-52); ghi phân bổ, dòng sổ giảm công nợ; phần dư ghi trả trước (không gắn khoản công nợ).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementResult settle(SettlementCommand command) {
        periodGuard.assertOpen(command.companyId(), command.branchId(), PeriodModule.DEBT, command.postingDate());
        DebtLedgerEntry.Party party = new DebtLedgerEntry.Party(command.companyId(), command.branchId(),
                command.debtType(), command.partnerId());
        DebtPrepayment prepaymentRow = command.allowPrepayment() ? lockPrepayment(party) : null;
        List<AllocationRequest> plan = command.allocations() == null ? autoPlan(command) : manualPlan(
                command.allocations());
        Source source = new Source(party, command.sourceType(), command.sourceId(), command.sourceNo(),
                command.postingDate(), command.description(), command.actorId());
        Instant now = Instant.now(clock);
        BigDecimal allocated = allocate(source, plan, command.entryType(), now);
        BigDecimal prepayment = command.amount().subtract(allocated);
        if (prepayment.signum() < 0 || (prepayment.signum() > 0 && prepaymentRow == null)) {
            throw BusinessException.invalidField("lines", "Tổng phân bổ phải bằng số tiền chứng từ.");
        }
        if (prepayment.signum() > 0) {
            prepaymentRow.add(prepayment, now);
            prepaymentRepository.save(prepaymentRow);
            ledgerRepository.save(DebtLedgerEntry.unallocated(party, command.postingDate(), DebtEntryType.PREPAYMENT,
                    false, prepayment, source.reference(null), command.description(), command.actorId(), now));
        }
        return new SettlementResult(allocated, prepayment);
    }

    /**
     * Bù trừ trả trước với hóa đơn (BR-AR-04): khóa dòng trả trước, kiểm tra còn đủ, ghi dòng sổ dùng trả trước (không
     * gắn khoản công nợ) và phân bổ vào các khoản chỉ định. Số dư công nợ của đối tượng không đổi.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal applyPrepayment(PrepaymentOffsetCommand command) {
        periodGuard.assertOpen(command.companyId(), command.branchId(), PeriodModule.DEBT, command.postingDate());
        DebtLedgerEntry.Party party = new DebtLedgerEntry.Party(command.companyId(), command.branchId(),
                command.debtType(), command.partnerId());
        DebtPrepayment row = lockPrepayment(party);
        List<AllocationRequest> plan = manualPlan(command.allocations());
        BigDecimal total = plan.stream().map(AllocationRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = Instant.now(clock);
        row.subtract(total, now, ErrorCode.OVER_ALLOCATION, "Tiền trả trước không đủ để bù trừ "
                + total.stripTrailingZeros().toPlainString() + ".");
        prepaymentRepository.save(row);
        Source source = new Source(party, command.sourceType(), command.sourceId(), command.sourceNo(),
                command.postingDate(), command.description(), command.actorId());
        ledgerRepository.save(DebtLedgerEntry.unallocated(party, command.postingDate(), DebtEntryType.OFFSET, true,
                total, source.reference(null), command.description(), command.actorId(), now));
        allocate(source, plan, DebtEntryType.OFFSET, now);
        return total;
    }

    /**
     * Đảo thanh toán / bù trừ (BR-GEN-21): hoàn trả trước của chứng từ (đã bị bù trừ thì từ chối), hoàn số đã phân bổ
     * vào khoản công nợ, phân bổ chuyển REVERSED, dòng sổ ngược đúng từng dòng gốc của phân bổ và trả trước.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseSettlement(SettlementReversalCommand command) {
        periodGuard.assertOpen(command.companyId(), command.branchId(), PeriodModule.DEBT, command.postingDate());
        List<PaymentAllocation> allocations = allocationRepository.findBySource(command.companyId(),
                        command.sourceType(), command.sourceId()).stream()
                .filter(allocation -> allocation.getStatus() == PaymentAllocationStatus.ACTIVE)
                .toList();
        Set<Long> allocatedItems = allocations.stream().map(PaymentAllocation::getDebtItemId)
                .collect(Collectors.toCollection(TreeSet::new));
        List<DebtLedgerEntry> originals = ledgerRepository.findOriginalsByReference(command.companyId(),
                        command.sourceType(), command.sourceId()).stream()
                .filter(entry -> entry.getDebtItemId() == null || allocatedItems.contains(entry.getDebtItemId()))
                .toList();
        Instant now = Instant.now(clock);
        restorePrepayments(originals, now);
        Map<Long, DebtItem> items = itemRepository.findAllForUpdate(allocatedItems).stream()
                .collect(Collectors.toMap(DebtItem::getId, Function.identity()));
        for (PaymentAllocation allocation : allocations) {
            items.get(allocation.getDebtItemId()).unsettle(allocation.getAmount());
            allocation.reverse(command.actorId(), now);
        }
        itemRepository.saveAll(items.values());
        allocationRepository.saveAll(allocations);
        ledgerRepository.saveAll(originals.stream()
                .map(original -> original.reversed(command.postingDate(), new DebtLedgerEntry.Reference(
                        original.getReferenceType(), original.getReferenceId(), original.getReferenceLineId(),
                        original.getReferenceNo()), command.actorId(), now))
                .toList());
    }

    /** Tiền trả trước còn của đối tượng trong chi nhánh (không khóa). */
    @Transactional(readOnly = true)
    public BigDecimal prepayment(Long companyId, Long branchId, DebtType debtType, Long partnerId) {
        return prepaymentRepository.find(companyId, branchId, debtType, partnerId)
                .map(DebtPrepayment::getAmount).orElse(BigDecimal.ZERO);
    }

    /** Đảo dòng trả trước: dòng ghi tăng trả trước thì trừ lại (không đủ = đã bị bù trừ), dòng dùng thì cộng lại. */
    private void restorePrepayments(List<DebtLedgerEntry> originals, Instant now) {
        List<DebtLedgerEntry> unallocated = originals.stream().filter(entry -> entry.getDebtItemId() == null).toList();
        if (unallocated.isEmpty()) {
            return;
        }
        DebtLedgerEntry first = unallocated.get(0);
        DebtPrepayment row = lockPrepayment(new DebtLedgerEntry.Party(first.getCompanyId(), first.getBranchId(),
                first.getDebtType(), first.getPartnerId()));
        for (DebtLedgerEntry entry : unallocated) {
            BigDecimal effect = entry.getDebtType().increasesOnDebit()
                    ? entry.getCreditAmount().subtract(entry.getDebitAmount())
                    : entry.getDebitAmount().subtract(entry.getCreditAmount());
            if (effect.signum() > 0) {
                row.subtract(effect, now, ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Tiền trả trước của "
                        + entry.getReferenceNo() + " đã được bù trừ; đảo chứng từ bù trừ trước.");
            } else {
                row.add(effect.negate(), now);
            }
        }
        prepaymentRepository.save(row);
    }

    /** Dòng trả trước của đối tượng: tạo nếu chưa có rồi khóa ghi (không dùng INSERT IGNORE rồi FOR UPDATE). */
    private DebtPrepayment lockPrepayment(DebtLedgerEntry.Party party) {
        prepaymentRepository.ensureRow(party.companyId(), party.branchId(), party.debtType().name(), party.partnerId());
        return prepaymentRepository.findForUpdate(party.companyId(), party.branchId(), party.debtType(),
                        party.partnerId())
                .orElseThrow(() -> new IllegalStateException("Thiếu dòng trả trước của đối tượng #"
                        + party.partnerId()));
    }

    private record Source(DebtLedgerEntry.Party party, String type, Long id, String number, LocalDate postingDate,
                          String description, Long actorId) {

        DebtLedgerEntry.Reference reference(Long lineId) {
            return new DebtLedgerEntry.Reference(type, id, lineId, number);
        }
    }

    /** Khóa khoản công nợ theo id tăng dần, kiểm tra thuộc đúng đối tượng, ghi phân bổ và dòng sổ giảm công nợ. */
    private BigDecimal allocate(Source source, List<AllocationRequest> plan, DebtEntryType entryType, Instant now) {
        Map<Long, DebtItem> items = itemRepository.findAllForUpdate(plan.stream().map(AllocationRequest::debtItemId)
                        .collect(Collectors.toCollection(TreeSet::new))).stream()
                .collect(Collectors.toMap(DebtItem::getId, Function.identity()));
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < plan.size(); i++) {
            AllocationRequest request = plan.get(i);
            DebtItem item = requireItem(items.get(request.debtItemId()), source.party(), i);
            item.settle(request.amount());
            allocationRepository.save(PaymentAllocation.of(item, new PaymentAllocation.Source(source.type(),
                    source.id(), request.sourceLineId(), source.number()), source.postingDate(), request.amount(),
                    source.actorId(), now));
            ledgerRepository.save(DebtLedgerEntry.of(item, source.postingDate(), entryType, false, request.amount(),
                    source.reference(request.sourceLineId()), source.description(), source.actorId(), now));
            allocated = allocated.add(request.amount());
        }
        itemRepository.saveAll(items.values());
        return allocated;
    }

    /** Tự động: khoản đến hạn sớm nhất trước, rồi ngày chứng từ, rồi id (BR-AR-03, hóa đơn cũ nhất trước). */
    private List<AllocationRequest> autoPlan(SettlementCommand command) {
        List<DebtItem> open = new ArrayList<>(itemRepository.findOpenForUpdate(command.companyId(),
                command.branchId(), command.debtType(), command.partnerId()));
        open.sort(Comparator.comparing(DebtItem::getDueDate).thenComparing(DebtItem::getDocumentDate)
                .thenComparing(DebtItem::getId));
        List<AllocationRequest> plan = new ArrayList<>();
        BigDecimal left = command.amount();
        for (DebtItem item : open) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal amount = left.min(item.getRemainingAmount());
            plan.add(new AllocationRequest(item.getId(), null, amount));
            left = left.subtract(amount);
        }
        return plan;
    }

    private static List<AllocationRequest> manualPlan(List<AllocationRequest> allocations) {
        if (allocations.stream().map(AllocationRequest::debtItemId)
                .collect(Collectors.toCollection(HashSet::new)).size() != allocations.size()) {
            throw BusinessException.invalidField("lines", "Một khoản công nợ chỉ phân bổ một dòng.");
        }
        return allocations;
    }

    /** Khoản công nợ phải đúng công ty, chi nhánh, loại công nợ, đối tượng của chứng từ. */
    private static DebtItem requireItem(DebtItem item, DebtLedgerEntry.Party party, int index) {
        if (item == null || !Objects.equals(item.getCompanyId(), party.companyId())
                || !Objects.equals(item.getBranchId(), party.branchId()) || item.getDebtType() != party.debtType()
                || !Objects.equals(item.getPartnerId(), party.partnerId())) {
            throw BusinessException.invalidField("lines[" + index + "].debtItemId", "Khoản công nợ không thuộc đối "
                    + "tượng, chi nhánh, loại công nợ của chứng từ.");
        }
        return item;
    }
}
