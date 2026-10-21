package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.dto.InventoryDocumentRequest;
import com.company.erp.inventory.application.dto.InventoryLineRequest;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhập kho theo chứng từ nguồn của module khác (phiếu nhận hàng, BR-PUR-08, D-36): lập phiếu nhập kho PNK riêng, gắn
 * chứng từ / dòng nguồn, ghi sổ ngay (cùng lúc ghi sổ chứng từ nguồn) trong transaction của chứng từ nguồn. Đảo cũng
 * chỉ qua chứng từ nguồn. Chứng từ nguồn phải được khóa trước khi gọi để thứ tự khóa là: nguồn → kỳ → kho → tồn.
 * Transaction gọi phải ở mức READ COMMITTED như ghi sổ kho (ADR-08).
 */
@Service
public class StockInService {

    private static final Logger log = LoggerFactory.getLogger(StockInService.class);

    /** Dòng nhập; {@code qualityStatus} trống thì ĐẠT (D-56). */
    public record StockInLine(Long sourceLineId, Long productId, Long unitId, BigDecimal quantity, Long locationId,
                              String lotNo, LocalDate manufactureDate, LocalDate expiryDate,
                              QualityStatus qualityStatus, BigDecimal unitCost, String note,
                              List<String> serialNumbers) {
    }

    /** {@code subType}: {@code null} (nhập mua) hoặc {@link #SALES_RETURN} (hàng bán bị trả lại). */
    public record StockInCommand(String sourceType, Long sourceId, String sourceNo, Long warehouseId, Long partnerId,
                                 LocalDate documentDate, LocalDate postingDate, String note, List<StockInLine> lines,
                                 String subType) {

        public StockInCommand(String sourceType, Long sourceId, String sourceNo, Long warehouseId, Long partnerId,
                              LocalDate documentDate, LocalDate postingDate, String note, List<StockInLine> lines) {
            this(sourceType, sourceId, sourceNo, warehouseId, partnerId, documentDate, postingDate, note, lines, null);
        }
    }

    /** Loại nghiệp vụ nhập hàng bán bị trả lại (BR-SAL-32). */
    public static final String SALES_RETURN = DocumentPostingPlanner.SALES_RETURN;

    public record StockInResult(Long documentId, String documentNo) {
    }

    private final InventoryDocumentRepository documentRepository;
    private final InventoryDocumentBuilder builder;
    private final InventoryNumbering numbering;
    private final InventoryDocumentPostingService postingService;
    private final InventoryDocumentService documentService;
    private final InventoryAccess access;

    public StockInService(InventoryDocumentRepository documentRepository, InventoryDocumentBuilder builder,
                          InventoryNumbering numbering, InventoryDocumentPostingService postingService,
                          InventoryDocumentService documentService, InventoryAccess access) {
        this.documentRepository = documentRepository;
        this.builder = builder;
        this.numbering = numbering;
        this.postingService = postingService;
        this.documentService = documentService;
        this.access = access;
    }

    /**
     * Lập và ghi sổ PNK. Kiểm tra lô / hạn dùng / serial / vị trí theo hàng như phiếu nhập kho lập tay; lỗi dòng gắn
     * trường {@code lines[i]} theo thứ tự dòng truyền vào.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockInResult receive(CurrentUser actor, StockInCommand command) {
        InventoryDocumentRequest request = new InventoryDocumentRequest(InventoryDocumentType.PNK, null,
                command.documentDate(), command.postingDate(), command.warehouseId(), null, command.partnerId(), null,
                null, command.note(), null, null, command.lines().stream().map(StockInService::toRequest).toList(),
                null);
        InventoryDocumentBuilder.Built built = builder.build(actor, request, null);
        InventoryDocument document = new InventoryDocument(built.companyId(), built.branchId(),
                InventoryDocumentType.PNK, numbering.next(built.companyId(), built.branchId(),
                InventoryDocumentType.PNK.name(), command.documentDate()), built.header().withSubType(
                command.subType()));
        document.replaceLines(built.lines());
        for (int i = 0; i < command.lines().size(); i++) {
            built.lines().get(i).linkSourceLine(command.lines().get(i).sourceLineId());
        }
        document.linkSource(command.sourceType(), command.sourceId(), command.sourceNo());
        documentRepository.saveAndFlush(document);
        postingService.postDocument(document, actor.id());
        documentRepository.saveAndFlush(document);
        documentService.audit(actor, "POST", document, null, null, command.sourceNo());
        log.info("Nhập kho {} theo {} {}", document.getDocumentNo(), command.sourceType(), command.sourceNo());
        return new StockInResult(document.getId(), document.getDocumentNo());
    }

    /**
     * Đảo PNK đã nhập theo chứng từ nguồn: lập chứng từ đảo PDK và ghi sổ ngay; hàng đã được xuất thì 409
     * {@code HAS_DEPENDENT_DOCUMENTS} (BR-GEN-22).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockInResult reverse(CurrentUser actor, Long documentId, LocalDate postingDate, String reason) {
        InventoryDocument original = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(BusinessException::notFound);
        if (!access.canAccessWarehouse(actor, original.getWarehouseId())) {
            throw BusinessException.notFound();
        }
        original.requireReversible();
        InventoryDocument reversal = new InventoryDocument(original.getCompanyId(), original.getBranchId(),
                InventoryDocumentType.PDK, numbering.next(original.getCompanyId(), original.getBranchId(),
                InventoryDocumentType.PDK.name(), postingDate),
                new InventoryDocument.Header(original.getDocumentType().name(), postingDate, postingDate,
                        original.getWarehouseId(), null, null, original.getPartnerId(), null, reason,
                        "Đảo " + original.getDocumentNo(), null, null, null, original.getId()));
        reversal.linkSource(original.getSourceType(), original.getSourceId(), original.getSourceNo());
        documentRepository.saveAndFlush(reversal);
        original.linkReversal(reversal.getId());
        postingService.postDocument(reversal, actor.id());
        documentRepository.saveAndFlush(reversal);
        documentService.audit(actor, "REVERSE", original, reason, null, reversal.getDocumentNo());
        return new StockInResult(reversal.getId(), reversal.getDocumentNo());
    }

    /** Số phiếu kho theo id (hiển thị liên kết ở chứng từ nguồn). */
    @Transactional(readOnly = true)
    public Map<Long, String> documentNumbers(Collection<Long> ids) {
        return documentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InventoryDocument::getId, InventoryDocument::getDocumentNo));
    }

    private static InventoryLineRequest toRequest(StockInLine line) {
        return new InventoryLineRequest(line.productId(), line.unitId(), line.quantity(), null, line.locationId(), null,
                null, line.lotNo(), line.manufactureDate(), line.expiryDate(), line.qualityStatus(), null,
                line.unitCost(), null, line.note(), line.serialNumbers());
    }
}
