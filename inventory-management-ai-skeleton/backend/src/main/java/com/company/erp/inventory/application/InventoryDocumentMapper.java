package com.company.erp.inventory.application;

import com.company.erp.inventory.application.dto.InventoryDocumentResponse;
import com.company.erp.inventory.application.dto.InventoryDocumentSummary;
import com.company.erp.inventory.application.dto.InventoryLineResponse;
import com.company.erp.inventory.application.dto.InventoryTransactionResponse;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentLine;
import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Chuyển chứng từ kho, sổ nhật ký sang DTO kèm tên hàng, số lô, serial (entity không ra khỏi module). */
@Component
class InventoryDocumentMapper {

    static final String REVERSAL_PERMISSION = "inventory-reversal:create";

    private final MasterDataQueryService masterData;
    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryDocumentRepository documentRepository;
    private final ApprovalService approvalService;

    InventoryDocumentMapper(MasterDataQueryService masterData, InventoryLotRepository lotRepository,
                            InventorySerialRepository serialRepository,
                            InventoryTransactionRepository transactionRepository,
                            InventoryDocumentRepository documentRepository, ApprovalService approvalService) {
        this.masterData = masterData;
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.transactionRepository = transactionRepository;
        this.documentRepository = documentRepository;
        this.approvalService = approvalService;
    }

    InventoryDocumentSummary toSummary(InventoryDocument document) {
        return new InventoryDocumentSummary(document.getId(), document.getDocumentType(), document.getSubType(),
                document.getDocumentNo(), document.getDocumentDate(), document.getPostingDate(), document.getStatus(),
                document.getBranchId(), document.getWarehouseId(), document.getDestinationWarehouseId(),
                document.getPartnerId(), document.getTotalAmount(), document.getLines().size(),
                document.getUpdatedAt());
    }

    InventoryDocumentResponse toResponse(InventoryDocument document, CurrentUser viewer) {
        List<InventoryTransaction> transactions = transactionRepository
                .findByReferenceTypeAndReferenceIdOrderByIdAsc(document.getDocumentType().name(), document.getId());
        Set<Long> productIds = Stream.concat(document.getLines().stream().map(InventoryDocumentLine::getProductId),
                transactions.stream().map(InventoryTransaction::getProductId)).collect(Collectors.toSet());
        Map<Long, ProductInfo> products = products(productIds);
        Set<Long> lotIds = Stream.concat(document.getLines().stream().map(InventoryDocumentLine::getLotId),
                transactions.stream().map(InventoryTransaction::getLotId)).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, InventoryLot> lots = lots(lotIds);
        Map<Long, InventorySerial> serials = serials(transactions.stream().map(InventoryTransaction::getSerialId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        List<InventoryLineResponse> lines = document.getLines().stream()
                .map(line -> toLine(line, products.get(line.getProductId()), lots.get(line.getLotId())))
                .toList();
        List<InventoryTransactionResponse> ledger = transactions.stream()
                .map(transaction -> toTransaction(transaction, products, lots, serials))
                .toList();
        return new InventoryDocumentResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentType(), document.getSubType(), document.getDocumentNo(),
                document.getDocumentDate(), document.getPostingDate(), document.getStatus(),
                document.getWarehouseId(), document.getDestinationWarehouseId(), document.getTransitWarehouseId(),
                document.getPartnerId(), document.getReasonId(), document.getReasonText(), document.getNote(),
                document.getRecallId(), document.getStockCountId(), document.getIssuePolicyId(),
                document.getReversalOfId(), documentNo(document.getReversalOfId()), document.getReversedById(),
                documentNo(document.getReversedById()), document.getTotalAmount(), document.getCreatedBy(),
                document.getCreatedAt(), document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(),
                document.getReceivedAt(), document.getReceivePostingDate(), document.getCancelledAt(),
                document.getCancelReason(), document.getVersion(), lines, ledger,
                approvalService.history(document.getDocumentType().name(), document.getId()),
                allowedActions(document, viewer));
    }

    InventoryTransactionResponse toTransaction(InventoryTransaction transaction, Map<Long, ProductInfo> products,
                                               Map<Long, InventoryLot> lots, Map<Long, InventorySerial> serials) {
        ProductInfo product = products.get(transaction.getProductId());
        InventoryLot lot = transaction.getLotId() == null ? null : lots.get(transaction.getLotId());
        InventorySerial serial = transaction.getSerialId() == null ? null : serials.get(transaction.getSerialId());
        return new InventoryTransactionResponse(transaction.getId(), transaction.getPostingDate(),
                transaction.getPostedAt(), transaction.getProductId(), product == null ? null : product.code(),
                product == null ? null : product.name(), transaction.getWarehouseId(), transaction.getLocationId(),
                transaction.getLotId(), lot == null ? null : lot.getLotNo(), lot == null ? null : lot.getExpiryDate(),
                transaction.getSerialId(), serial == null ? null : serial.getSerialNo(),
                transaction.getQualityStatus(), transaction.getTransactionType(), transaction.getDirection(),
                transaction.getQuantity(), transaction.getUnitCost(), transaction.getAmount(),
                transaction.getPartnerId(), transaction.getReferenceType(), transaction.getReferenceId(),
                transaction.getReferenceNo(), transaction.getReversalOfId());
    }

    Map<Long, ProductInfo> products(Collection<Long> ids) {
        return masterData.products(ids).stream().collect(Collectors.toMap(ProductInfo::id, Function.identity()));
    }

    Map<Long, InventoryLot> lots(Collection<Long> ids) {
        return lotRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InventoryLot::getId, Function.identity()));
    }

    Map<Long, InventorySerial> serials(Collection<Long> ids) {
        return serialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InventorySerial::getId, Function.identity()));
    }

    private static InventoryLineResponse toLine(InventoryDocumentLine line, ProductInfo product, InventoryLot lot) {
        return new InventoryLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                product == null ? null : product.code(), product == null ? null : product.name(), line.getUnitId(),
                line.getConversionFactor(), line.getQuantity(), line.getBaseQuantity(), line.getDirection(),
                line.getLocationId(), line.getDestinationLocationId(), line.getLotId(),
                lot != null ? lot.getLotNo() : line.getLotNo(), line.getManufactureDate(),
                lot != null ? lot.getExpiryDate() : line.getExpiryDate(), line.getQualityStatus(),
                line.getTargetQualityStatus(), line.getUnitCost(), line.getAmount(), line.getLotOverrideReason(),
                line.getNote(), line.getSerialNumbers());
    }

    private String documentNo(Long documentId) {
        return documentId == null ? null
                : documentRepository.findById(documentId).map(InventoryDocument::getDocumentNo).orElse(null);
    }

    /** Hành động theo trạng thái và quyền; chỉ để ẩn/hiện nút, server vẫn kiểm tra khi thực hiện. */
    static Set<String> allowedActions(InventoryDocument document, CurrentUser viewer) {
        InventoryDocumentType type = document.getDocumentType();
        boolean canCreate = viewer.hasPermission(type.permission("create"));
        boolean canApprove = viewer.hasPermission(type.permission("approve"));
        boolean canPost = viewer.hasPermission(type.permission("post"));
        boolean systemGenerated = type == InventoryDocumentType.PDK || document.getStockCountId() != null;
        Set<String> actions = new LinkedHashSet<>();
        InventoryDocumentStatus status = document.getStatus();
        switch (status) {
            case DRAFT, REJECTED -> {
                if (canCreate && !systemGenerated) {
                    actions.add("update");
                }
                addIf(actions, canCreate, "submit", "cancel");
            }
            case SUBMITTED -> {
                addIf(actions, canApprove, "approve", "reject");
                addIf(actions, canCreate, "cancel");
            }
            case APPROVED -> {
                addIf(actions, canPost, "post");
                addIf(actions, canCreate, "cancel");
            }
            case IN_TRANSIT -> addIf(actions, canPost, "receive");
            case POSTED -> {
                if (type != InventoryDocumentType.PDK && document.getReversedById() == null) {
                    addIf(actions, viewer.hasPermission(REVERSAL_PERMISSION), "reverse");
                }
            }
            default -> {
                // CANCELLED, REVERSED: không còn hành động.
            }
        }
        return new HashSet<>(actions);
    }

    private static void addIf(Set<String> actions, boolean condition, String... names) {
        if (condition) {
            actions.addAll(List.of(names));
        }
    }
}
