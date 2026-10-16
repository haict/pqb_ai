package com.company.erp.inventory.application;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentLine;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.domain.StockKey;
import com.company.erp.inventory.domain.TransactionType;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.LocationInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chuyển dòng chứng từ kho thành biến động ghi sổ theo loại chứng từ (ma trận ảnh hưởng chứng từ, BR mục 5.3).
 * Không tự ghi sổ; {@link InventoryPostingService} khóa, kiểm tra và ghi.
 */
@Component
class DocumentPostingPlanner {

    static final String RECALL_IN = "RECALL_IN";
    /** Phiếu nhập tồn kho đầu kỳ khi chuyển đổi dữ liệu (BR-MIG-02); ghi sổ như phiếu nhập không loại nghiệp vụ. */
    static final String OPENING = "OPENING";
    static final String COUNT = "COUNT";
    /** Loại nghiệp vụ của phiếu kho do chứng từ nguồn module khác sinh ({@link StockOutService}, {@link StockInService}). */
    static final String SALE = "SALE";
    static final String PURCHASE_RETURN = "PURCHASE_RETURN";
    static final String SALES_RETURN = "SALES_RETURN";

    private final MasterDataQueryService masterData;
    private final TrackedItemResolver items;

    DocumentPostingPlanner(MasterDataQueryService masterData, TrackedItemResolver items) {
        this.masterData = masterData;
        this.items = items;
    }

    /** Biến động của chứng từ một bước: nhập, xuất nội bộ, xuất hủy, điều chỉnh. */
    List<StockMovement> plan(InventoryDocument document, Long actorId) {
        Map<Long, ProductInfo> products = products(document);
        List<StockMovement> movements = new ArrayList<>();
        for (InventoryDocumentLine line : document.getLines()) {
            ProductInfo product = products.get(line.getProductId());
            switch (document.getDocumentType()) {
                case PNK -> movements.addAll(receipt(document, line, product, actorId, receiptStatus(document, line),
                        receiptType(document)));
                case PXK -> movements.addAll(sourceIssue(document, line, product));
                case PXH -> movements.addAll(issue(document, line, product, TransactionType.SCRAP,
                        StockMovement.OutRule.DISPOSABLE, null));
                case PDC -> movements.addAll(adjustment(document, line, product, actorId));
                default -> throw new IllegalStateException("Loại chứng từ ghi sổ riêng: " + document.getDocumentType());
            }
        }
        return movements;
    }

    /** Phần xuất của chuyển kho (bước 1). */
    List<StockMovement> transferOuts(InventoryDocument document) {
        Map<Long, ProductInfo> products = products(document);
        List<StockMovement> movements = new ArrayList<>();
        for (InventoryDocumentLine line : document.getLines()) {
            QualityStatus status = line.getQualityStatus() == null ? QualityStatus.PASSED : line.getQualityStatus();
            StockMovement.OutRule rule = status == QualityStatus.PASSED ? StockMovement.OutRule.SELLABLE
                    : StockMovement.OutRule.EXACT_STATUS;
            movements.addAll(issue(document, line, products.get(line.getProductId()), TransactionType.TRANSFER_OUT,
                    rule, status));
        }
        return movements;
    }

    /**
     * Phần nhập của chuyển kho tương ứng đúng từng phần đã xuất (cùng lô, serial, trạng thái, BR-INV-30), vào kho đích
     * (cùng chi nhánh) hoặc kho "đang vận chuyển" (khác chi nhánh).
     */
    List<StockMovement> transferIns(InventoryDocument document, List<PostedMovement> outs, Long targetWarehouseId,
                                    boolean useLineDestination) {
        Map<Long, InventoryDocumentLine> lines = document.getLines().stream()
                .collect(Collectors.toMap(InventoryDocumentLine::getId, Function.identity()));
        Long defaultLocation = defaultLocation(targetWarehouseId);
        List<StockMovement> movements = new ArrayList<>();
        for (PostedMovement out : outs) {
            InventoryDocumentLine line = lines.get(out.lineId());
            Long location = useLineDestination && line.getDestinationLocationId() != null
                    ? line.getDestinationLocationId() : defaultLocation;
            StockKey key = out.key().withPlace(targetWarehouseId, location);
            movements.add(new StockMovement(out.lineId(), MovementDirection.IN, TransactionType.TRANSFER_IN,
                    key.productId(), key.warehouseId(), key.locationId(), key.lotId(), key.serialId(),
                    key.qualityStatus(), out.quantity(), null, null, null, false));
        }
        return movements;
    }

    /** Nhận hàng chuyển kho khác chi nhánh (bước 2): xuất khỏi kho "đang vận chuyển", nhập kho đích. */
    List<StockMovement> receive(InventoryDocument document, List<InventoryTransaction> shipped) {
        Map<Long, InventoryDocumentLine> lines = document.getLines().stream()
                .collect(Collectors.toMap(InventoryDocumentLine::getId, Function.identity()));
        Long destinationDefault = defaultLocation(document.getDestinationWarehouseId());
        List<StockMovement> movements = new ArrayList<>();
        for (InventoryTransaction transaction : shipped) {
            if (transaction.getDirection() != MovementDirection.IN
                    || !transaction.getWarehouseId().equals(document.getTransitWarehouseId())) {
                continue;
            }
            StockKey key = transaction.key();
            movements.add(new StockMovement(transaction.getReferenceLineId(), MovementDirection.OUT,
                    TransactionType.TRANSFER_OUT, key.productId(), key.warehouseId(), key.locationId(), key.lotId(),
                    key.serialId(), key.qualityStatus(), transaction.getQuantity(), null,
                    StockMovement.OutRule.EXACT_STATUS, null, true));
            InventoryDocumentLine line = lines.get(transaction.getReferenceLineId());
            Long location = line != null && line.getDestinationLocationId() != null
                    ? line.getDestinationLocationId() : destinationDefault;
            movements.add(new StockMovement(transaction.getReferenceLineId(), MovementDirection.IN,
                    TransactionType.TRANSFER_IN, key.productId(), document.getDestinationWarehouseId(), location,
                    key.lotId(), key.serialId(), key.qualityStatus(), transaction.getQuantity(), null, null, null,
                    false));
        }
        return movements;
    }

    /**
     * Đảo: dòng ngược chiều đúng từng dòng sổ gốc, cùng kho, vị trí, lô, serial, trạng thái (BR-GEN-21). Bỏ cặp dòng
     * qua kho "đang vận chuyển" của chuyển kho đã nhận vì chúng triệt tiêu nhau.
     */
    List<StockMovement> reversal(List<InventoryTransaction> originals, Long transitWarehouseId) {
        List<StockMovement> movements = new ArrayList<>();
        for (InventoryTransaction original : originals) {
            if (original.getReversalOfId() != null || Objects.equals(original.getWarehouseId(), transitWarehouseId)) {
                continue;
            }
            StockKey key = original.key();
            boolean wasIn = original.getDirection() == MovementDirection.IN;
            movements.add(new StockMovement(original.getReferenceLineId(),
                    wasIn ? MovementDirection.OUT : MovementDirection.IN, TransactionType.REVERSAL, key.productId(),
                    key.warehouseId(), key.locationId(), key.lotId(), key.serialId(), key.qualityStatus(),
                    original.getQuantity(), wasIn ? null : original.getUnitCost(),
                    wasIn ? StockMovement.OutRule.EXACT_STATUS : null, original.getId(), true));
        }
        return movements;
    }

    private List<StockMovement> receipt(InventoryDocument document, InventoryDocumentLine line, ProductInfo product,
                                        Long actorId, QualityStatus status, TransactionType type) {
        String field = "lines[" + (line.getLineNo() - 1) + "]";
        Long lotId = items.lotForReceipt(document.getCompanyId(), product, line.getLotId(), line.getLotNo(),
                line.getManufactureDate(), line.getExpiryDate(), actorId, field + ".lotNo");
        Long location = line.getLocationId() != null ? line.getLocationId() : defaultLocation(document.getWarehouseId());
        if (product.serialTracked()) {
            List<Long> serials = items.serialsForReceipt(document.getCompanyId(), product, line.getSerialNumbers(),
                    lotId, actorId);
            return serials.stream()
                    .map(serialId -> new StockMovement(line.getId(), MovementDirection.IN, type, product.id(),
                            document.getWarehouseId(), location, lotId, serialId, status, BigDecimal.ONE,
                            unitCostPerBase(line), null, null, false))
                    .toList();
        }
        return List.of(new StockMovement(line.getId(), MovementDirection.IN, type, product.id(),
                document.getWarehouseId(), location, lotId, null, status, line.getBaseQuantity(), unitCostPerBase(line),
                null, null, false));
    }

    private List<StockMovement> issue(InventoryDocument document, InventoryDocumentLine line, ProductInfo product,
                                      TransactionType type, StockMovement.OutRule rule, QualityStatus status) {
        boolean justified = line.getLotOverrideReason() != null && !line.getLotOverrideReason().isBlank();
        if (product.serialTracked()) {
            return items.serialsForIssue(product, line.getSerialNumbers()).stream()
                    .map(serialId -> new StockMovement(line.getId(), MovementDirection.OUT, type, product.id(),
                            document.getWarehouseId(), line.getLocationId(), line.getLotId(), serialId, status,
                            BigDecimal.ONE, null, rule, null, true))
                    .toList();
        }
        return List.of(new StockMovement(line.getId(), MovementDirection.OUT, type, product.id(),
                document.getWarehouseId(), line.getLocationId(), line.getLotId(), null, status, line.getBaseQuantity(),
                null, rule, null, justified));
    }

    /**
     * Phiếu xuất: nội bộ / xuất bán (chỉ hàng ĐẠT, kiểm tra tồn khả dụng); trả nhà cung cấp hàng không ĐẠT thì xuất
     * đúng trạng thái của dòng (hàng cách ly trả lại, BR-PUR-14).
     */
    private List<StockMovement> sourceIssue(InventoryDocument document, InventoryDocumentLine line,
                                            ProductInfo product) {
        if (PURCHASE_RETURN.equals(document.getSubType())) {
            QualityStatus status = line.getQualityStatus() == null ? QualityStatus.PASSED : line.getQualityStatus();
            return issue(document, line, product, TransactionType.PURCHASE_RETURN, status == QualityStatus.PASSED
                    ? StockMovement.OutRule.SELLABLE : StockMovement.OutRule.EXACT_STATUS,
                    status == QualityStatus.PASSED ? null : status);
        }
        return issue(document, line, product, SALE.equals(document.getSubType()) ? TransactionType.SALES_ISSUE
                : TransactionType.INTERNAL_ISSUE, StockMovement.OutRule.SELLABLE, null);
    }

    private static TransactionType receiptType(InventoryDocument document) {
        if (RECALL_IN.equals(document.getSubType())) {
            return TransactionType.RECALL_IN;
        }
        return SALES_RETURN.equals(document.getSubType()) ? TransactionType.SALES_RETURN : TransactionType.OPENING;
    }

    private List<StockMovement> adjustment(InventoryDocument document, InventoryDocumentLine line,
                                           ProductInfo product, Long actorId) {
        boolean fromCount = COUNT.equals(document.getSubType());
        QualityStatus status = line.getQualityStatus() == null ? QualityStatus.PASSED : line.getQualityStatus();
        if (line.getDirection() == MovementDirection.IN) {
            return receipt(document, line, product, actorId, status,
                    fromCount ? TransactionType.COUNT_GAIN : TransactionType.ADJUST_IN);
        }
        return issue(document, line, product, fromCount ? TransactionType.COUNT_LOSS : TransactionType.ADJUST_OUT,
                StockMovement.OutRule.EXACT_STATUS, status);
    }

    /** Chuyển trạng thái chất lượng, phần xuất khỏi trạng thái hiện tại (bước 1). */
    List<StockMovement> statusOuts(InventoryDocument document) {
        Map<Long, ProductInfo> products = products(document);
        List<StockMovement> movements = new ArrayList<>();
        for (InventoryDocumentLine line : document.getLines()) {
            movements.addAll(issue(document, line, products.get(line.getProductId()), TransactionType.STATUS_OUT,
                    StockMovement.OutRule.EXACT_STATUS, line.getQualityStatus()));
        }
        return movements;
    }

    /** Phần nhập lại đúng vị trí, lô, serial vừa xuất với trạng thái mới (BR-INV-03). */
    List<StockMovement> statusIns(InventoryDocument document, List<PostedMovement> outs) {
        Map<Long, InventoryDocumentLine> lines = document.getLines().stream()
                .collect(Collectors.toMap(InventoryDocumentLine::getId, Function.identity()));
        return outs.stream()
                .map(out -> {
                    StockKey key = out.key().withStatus(lines.get(out.lineId()).getTargetQualityStatus());
                    return new StockMovement(out.lineId(), MovementDirection.IN, TransactionType.STATUS_IN,
                            key.productId(), key.warehouseId(), key.locationId(), key.lotId(), key.serialId(),
                            key.qualityStatus(), out.quantity(), null, null, null, false);
                })
                .toList();
    }

    /** Thu hồi về: trạng thái THU HỒI; nhập khác: theo dòng, mặc định ĐẠT (D-56). */
    private static QualityStatus receiptStatus(InventoryDocument document, InventoryDocumentLine line) {
        if (RECALL_IN.equals(document.getSubType())) {
            return QualityStatus.RECALLED;
        }
        return line.getQualityStatus() != null ? line.getQualityStatus() : QualityStatus.PASSED;
    }

    /** Đơn giá theo đơn vị cơ sở = đơn giá theo đơn vị giao dịch / hệ số quy đổi. */
    private static BigDecimal unitCostPerBase(InventoryDocumentLine line) {
        if (line.getUnitCost() == null) {
            return null;
        }
        return line.getUnitCost().divide(line.getConversionFactor(), 4, java.math.RoundingMode.HALF_UP);
    }

    Long defaultLocation(Long warehouseId) {
        return masterData.defaultLocation(warehouseId).map(LocationInfo::id)
                .orElseThrow(() -> new IllegalStateException("Kho #" + warehouseId + " chưa có vị trí mặc định"));
    }

    private Map<Long, ProductInfo> products(InventoryDocument document) {
        return masterData.products(document.getLines().stream().map(InventoryDocumentLine::getProductId)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductInfo::id, Function.identity()));
    }
}
