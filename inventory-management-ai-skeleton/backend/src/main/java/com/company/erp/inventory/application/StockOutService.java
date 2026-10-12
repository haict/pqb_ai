package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.InventoryReservationService.ReservationUse;
import com.company.erp.inventory.application.dto.InventoryDocumentRequest;
import com.company.erp.inventory.application.dto.InventoryLineRequest;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentLine;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xuất kho theo chứng từ nguồn của module khác: xuất bán theo đơn bán (BR-SAL-21, FEFO khi không chỉ định lô), xuất
 * trả nhà cung cấp đúng lô / serial (BR-PUR-14). Lập phiếu xuất PXK có nguồn và ghi sổ ngay trong transaction READ
 * COMMITTED của chứng từ nguồn (đã khóa trước). Giữ chỗ của chứng từ bán được dùng sau khi khóa tổng (hàng, kho) theo
 * thứ tự, để kiểm tra tồn khả dụng không tính giữ chỗ của chính chứng từ đó. Đảo chỉ qua chứng từ nguồn.
 */
@Service
public class StockOutService {

    /** Xuất bán (BR-SAL-21). */
    public static final String SALE = DocumentPostingPlanner.SALE;
    /** Xuất trả nhà cung cấp (BR-PUR-14). */
    public static final String PURCHASE_RETURN = DocumentPostingPlanner.PURCHASE_RETURN;

    private static final Logger log = LoggerFactory.getLogger(StockOutService.class);

    /**
     * Dòng xuất theo đơn vị giao dịch. {@code lotNo} trống: FEFO; chỉ định lô khác lô FEFO khi xuất bán phải có
     * {@code lotOverrideReason} (BR-INV-21). {@code qualityStatus} trống = ĐẠT.
     */
    public record StockOutLine(Long sourceLineId, Long productId, Long unitId, BigDecimal quantity, String lotNo,
                               QualityStatus qualityStatus, String lotOverrideReason, String note,
                               List<String> serialNumbers) {
    }

    /**
     * @param reservationSourceType chứng từ đã giữ chỗ (đơn bán) và phần giữ chỗ được dùng; trống nếu không có
     */
    public record StockOutCommand(String sourceType, Long sourceId, String sourceNo, String subType, Long warehouseId,
                                  Long partnerId, LocalDate documentDate, LocalDate postingDate, String note,
                                  List<StockOutLine> lines, String reservationSourceType, Long reservationSourceId,
                                  List<ReservationUse> reservationUses) {
    }

    /** Hàng đã xuất theo dòng nguồn: lô, hạn dùng, serial thực tế (biên bản giao nhận, trả hàng). */
    public record IssuedItem(Long sourceLineId, Long productId, Long lotId, String lotNo, LocalDate expiryDate,
                             String serialNo, QualityStatus qualityStatus, BigDecimal quantity) {
    }

    public record StockOutResult(Long documentId, String documentNo, List<IssuedItem> items) {
    }

    private final InventoryDocumentRepository documentRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final InventoryDocumentBuilder builder;
    private final InventoryNumbering numbering;
    private final InventoryDocumentPostingService postingService;
    private final InventoryDocumentService documentService;
    private final InventoryReservationService reservations;
    private final StockInService stockIn;
    private final StockLedgerJdbc ledger;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public StockOutService(InventoryDocumentRepository documentRepository,
                           InventoryTransactionRepository transactionRepository, InventoryLotRepository lotRepository,
                           InventorySerialRepository serialRepository, InventoryDocumentBuilder builder,
                           InventoryNumbering numbering, InventoryDocumentPostingService postingService,
                           InventoryDocumentService documentService, InventoryReservationService reservations,
                           StockInService stockIn, StockLedgerJdbc ledger, Clock clock) {
        this.documentRepository = documentRepository;
        this.transactionRepository = transactionRepository;
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.builder = builder;
        this.numbering = numbering;
        this.postingService = postingService;
        this.documentService = documentService;
        this.reservations = reservations;
        this.stockIn = stockIn;
        this.ledger = ledger;
        this.clock = clock;
    }

    /** Lập và ghi sổ PXK; lỗi dòng gắn trường {@code lines[i]} theo thứ tự dòng truyền vào. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockOutResult issue(CurrentUser actor, StockOutCommand command) {
        InventoryDocumentRequest request = new InventoryDocumentRequest(InventoryDocumentType.PXK, null,
                command.documentDate(), command.postingDate(), command.warehouseId(), null, command.partnerId(), null,
                null, command.note(), null, null, command.lines().stream()
                .map(line -> toRequest(line, lotId(line))).toList(), null);
        InventoryDocumentBuilder.Built built = builder.build(actor, request, null);
        if (command.reservationUses() != null && !command.reservationUses().isEmpty()) {
            lockSummaries(built.companyId(), command);
            reservations.consume(built.companyId(), command.reservationSourceType(), command.reservationSourceId(),
                    command.reservationUses());
        }
        InventoryDocument document = new InventoryDocument(built.companyId(), built.branchId(),
                InventoryDocumentType.PXK, numbering.next(built.companyId(), built.branchId(),
                InventoryDocumentType.PXK.name(), command.documentDate()), built.header().withSubType(
                command.subType()));
        document.replaceLines(built.lines());
        for (int i = 0; i < command.lines().size(); i++) {
            built.lines().get(i).linkSourceLine(command.lines().get(i).sourceLineId());
        }
        document.linkSource(command.sourceType(), command.sourceId(), command.sourceNo());
        Instant now = Instant.now(clock);
        document.submit(actor.id(), now);
        document.approve(actor.id(), now);
        documentRepository.saveAndFlush(document);
        postingService.postApproved(document, actor.id());
        documentRepository.saveAndFlush(document);
        documentService.audit(actor, "POST", document, null, null, command.sourceNo());
        log.info("Xuất kho {} theo {} {}", document.getDocumentNo(), command.sourceType(), command.sourceNo());
        return new StockOutResult(document.getId(), document.getDocumentNo(), issuedItems(document));
    }

    /** Đảo PXK theo chứng từ nguồn (lập PDK và ghi sổ ngay); giữ chỗ do chứng từ nguồn hoàn lại. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockInService.StockInResult reverse(CurrentUser actor, Long documentId, LocalDate postingDate,
                                                String reason) {
        return stockIn.reverse(actor, documentId, postingDate, reason);
    }

    /** Hàng đã xuất của phiếu (theo dòng nguồn). */
    @Transactional(readOnly = true)
    public List<IssuedItem> issuedItems(Long documentId) {
        return documentRepository.findById(documentId).map(this::issuedItems).orElse(List.of());
    }

    private List<IssuedItem> issuedItems(InventoryDocument document) {
        Map<Long, InventoryDocumentLine> lines = document.getLines().stream()
                .collect(Collectors.toMap(InventoryDocumentLine::getId, Function.identity()));
        List<InventoryTransaction> transactions = transactionRepository
                .findByReferenceTypeAndReferenceIdOrderByIdAsc(document.getDocumentType().name(), document.getId())
                .stream().filter(transaction -> transaction.getDirection() == MovementDirection.OUT).toList();
        Map<Long, InventoryLot> lots = lotRepository.findAllById(transactions.stream()
                        .map(InventoryTransaction::getLotId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(InventoryLot::getId, Function.identity()));
        Map<Long, InventorySerial> serials = serialRepository.findAllById(transactions.stream()
                        .map(InventoryTransaction::getSerialId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(InventorySerial::getId, Function.identity()));
        return transactions.stream().map(transaction -> {
            InventoryDocumentLine line = lines.get(transaction.getReferenceLineId());
            InventoryLot lot = transaction.getLotId() == null ? null : lots.get(transaction.getLotId());
            InventorySerial serial = transaction.getSerialId() == null ? null : serials.get(transaction.getSerialId());
            return new IssuedItem(line == null ? null : line.getSourceLineId(), transaction.getProductId(),
                    transaction.getLotId(), lot == null ? null : lot.getLotNo(),
                    lot == null ? null : lot.getExpiryDate(), serial == null ? null : serial.getSerialNo(),
                    transaction.getQualityStatus(), transaction.getQuantity());
        }).toList();
    }

    /** Khóa mọi tổng (hàng, kho) của phiếu theo thứ tự trước khi đổi giữ chỗ, cùng thứ tự với ghi sổ kho. */
    private void lockSummaries(Long companyId, StockOutCommand command) {
        Instant now = Instant.now(clock);
        new TreeSet<>(command.lines().stream().map(StockOutLine::productId).toList()).stream()
                .sorted(Comparator.naturalOrder())
                .forEach(productId -> ledger.lockSummary(companyId, productId, command.warehouseId(), now));
    }

    private Long lotId(StockOutLine line) {
        if (line.lotNo() == null || line.lotNo().isBlank()) {
            return null;
        }
        return lotRepository.findByProductIdAndLotNo(line.productId(), line.lotNo().trim())
                .map(InventoryLot::getId)
                .orElseThrow(() -> BusinessException.invalidField("lotNo", "Lô " + line.lotNo() + " không có trong kho."));
    }

    private static InventoryLineRequest toRequest(StockOutLine line, Long lotId) {
        return new InventoryLineRequest(line.productId(), line.unitId(), line.quantity(), null, null, null, lotId, null,
                null, null, line.qualityStatus(), null, null, line.lotOverrideReason(), line.note(),
                line.serialNumbers());
    }
}
