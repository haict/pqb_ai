package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.dto.InventoryDocumentRequest;
import com.company.erp.inventory.application.dto.InventoryLineRequest;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentLine;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.RecallRepository;
import com.company.erp.inventory.infrastructure.persistence.StockIssuePolicyRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.LocationInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra và dựng đầu / dòng chứng từ kho từ request: kho trong phạm vi, hàng quản lý tồn, quy đổi đơn vị (BR-MD-21),
 * lô / hạn dùng / serial theo cấu hình hàng (BR-INV-01, BR-INV-02), lý do bắt buộc cho điều chỉnh, hủy, chuyển trạng
 * thái (BR-INV-32, BR-INV-33). Tồn khả dụng không kiểm tra ở đây mà tại thời điểm ghi sổ (BR-INV-20).
 */
@Component
class InventoryDocumentBuilder {

    static final int QUANTITY_SCALE = 3;

    /** Đầu và dòng đã kiểm tra, cùng chi nhánh sở hữu (theo kho). */
    record Built(Long companyId, Long branchId, InventoryDocument.Header header,
                 List<InventoryDocumentLine> lines) {
    }

    private final InventoryAccess access;
    private final MasterDataQueryService masterData;
    private final InventoryLotRepository lotRepository;
    private final RecallRepository recallRepository;
    private final StockIssuePolicyRepository policyRepository;

    InventoryDocumentBuilder(InventoryAccess access, MasterDataQueryService masterData,
                             InventoryLotRepository lotRepository, RecallRepository recallRepository,
                             StockIssuePolicyRepository policyRepository) {
        this.access = access;
        this.masterData = masterData;
        this.lotRepository = lotRepository;
        this.recallRepository = recallRepository;
        this.policyRepository = policyRepository;
    }

    Built build(CurrentUser actor, InventoryDocumentRequest request, Long documentId) {
        InventoryDocumentType type = request.documentType();
        if (type == InventoryDocumentType.PDK) {
            throw BusinessException.invalidField("documentType", "Chứng từ đảo chỉ lập bằng thao tác đảo chứng từ gốc.");
        }
        WarehouseInfo warehouse = access.usableWarehouse(actor, request.warehouseId(), "warehouseId");
        String subType = subType(type, request.subType());
        WarehouseInfo destination = null;
        Long transitId = null;
        if (type == InventoryDocumentType.PCK) {
            destination = destination(warehouse, request.destinationWarehouseId());
            transitId = destination.branchId().equals(warehouse.branchId()) ? null : transit(warehouse.branchId());
        }
        requireHeaderReferences(request, warehouse.companyId(), type, subType, documentId);
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        InventoryDocument.Header header = new InventoryDocument.Header(subType, request.documentDate(), postingDate,
                warehouse.id(), destination == null ? null : destination.id(), transitId, request.partnerId(),
                request.reasonId(), trim(request.reasonText()), trim(request.note()), request.recallId(), null,
                request.issuePolicyId(), null);
        List<InventoryDocumentLine> lines = lines(type, request, warehouse, destination);
        return new Built(warehouse.companyId(), warehouse.branchId(), header, lines);
    }

    private static String subType(InventoryDocumentType type, String subType) {
        String value = trim(subType);
        if (value == null) {
            return null;
        }
        if (type == InventoryDocumentType.PNK && DocumentPostingPlanner.RECALL_IN.equals(value)) {
            return value;
        }
        throw BusinessException.invalidField("subType", "Loại nghiệp vụ '" + value + "' không áp dụng cho " + type
                + "; điều chỉnh từ kiểm kê chỉ sinh từ phiếu kiểm kê.");
    }

    private WarehouseInfo destination(WarehouseInfo source, Long destinationId) {
        if (destinationId == null) {
            throw BusinessException.invalidField("destinationWarehouseId", "Phải chọn kho đích.");
        }
        WarehouseInfo destination = masterData.warehouse(destinationId)
                .filter(found -> found.companyId().equals(source.companyId()) && found.active())
                .orElseThrow(() -> BusinessException.invalidField("destinationWarehouseId",
                        "Kho đích không tồn tại, khác công ty hoặc đã ngừng hoạt động."));
        if (destination.id().equals(source.id())) {
            throw BusinessException.invalidField("destinationWarehouseId", "Kho đích phải khác kho nguồn.");
        }
        return destination;
    }

    /** Chuyển khác chi nhánh đi qua kho "đang vận chuyển" của chi nhánh nguồn (BR-INV-30). */
    private Long transit(Long sourceBranchId) {
        return masterData.inTransitWarehouse(sourceBranchId)
                .map(WarehouseInfo::id)
                .orElseThrow(() -> BusinessException.invalidField("destinationWarehouseId",
                        "Chi nhánh nguồn chưa khai báo kho \"đang vận chuyển\" để chuyển khác chi nhánh."));
    }

    private void requireHeaderReferences(InventoryDocumentRequest request, Long companyId, InventoryDocumentType type,
                                         String subType, Long documentId) {
        if (request.postingDate() != null && request.postingDate().isBefore(request.documentDate())) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        boolean reasonRequired = type == InventoryDocumentType.PDC || type == InventoryDocumentType.PXH
                || type == InventoryDocumentType.PCL;
        if (reasonRequired && request.reasonId() == null && trim(request.reasonText()) == null) {
            throw BusinessException.invalidField("reasonText", "Phải ghi lý do cho chứng từ này.");
        }
        if (request.reasonId() != null) {
            masterData.referenceItem(request.reasonId())
                    .filter(item -> item.companyId().equals(companyId) && item.active())
                    .orElseThrow(() -> BusinessException.invalidField("reasonId", "Lý do không hợp lệ."));
        }
        if (DocumentPostingPlanner.RECALL_IN.equals(subType) && request.partnerId() == null) {
            throw BusinessException.invalidField("partnerId", "Nhập thu hồi phải chọn khách hàng trả hàng.");
        }
        if (request.partnerId() != null) {
            masterData.partner(request.partnerId())
                    .filter(partner -> partner.companyId().equals(companyId) && partner.active())
                    .orElseThrow(() -> BusinessException.invalidField("partnerId", "Đối tác không hợp lệ."));
        }
        if (request.recallId() != null && recallRepository.findById(request.recallId())
                .filter(recall -> recall.getCompanyId().equals(companyId)).isEmpty()) {
            throw BusinessException.invalidField("recallId", "Phiếu thu hồi không tồn tại.");
        }
        if (request.issuePolicyId() != null && policyRepository.findById(request.issuePolicyId())
                .filter(policy -> policy.getCompanyId().equals(companyId))
                .filter(policy -> policy.usableBy(documentId))
                .isEmpty()) {
            throw BusinessException.invalidField("issuePolicyId", "Chính sách xuất vượt tồn không tồn tại, chưa "
                    + "duyệt hoặc đã dùng cho chứng từ khác (mỗi chính sách dùng cho một chứng từ, D-53).");
        }
    }

    private List<InventoryDocumentLine> lines(InventoryDocumentType type, InventoryDocumentRequest request,
                                              WarehouseInfo warehouse, WarehouseInfo destination) {
        if (request.lines().isEmpty()) {
            throw BusinessException.invalidField("lines", "Chứng từ phải có ít nhất một dòng.");
        }
        Map<Long, ProductInfo> products = masterData.products(request.lines().stream()
                        .map(InventoryLineRequest::productId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductInfo::id, Function.identity()));
        List<InventoryDocumentLine> lines = new ArrayList<>();
        for (int index = 0; index < request.lines().size(); index++) {
            InventoryLineRequest line = request.lines().get(index);
            String field = "lines[" + index + "]";
            ProductInfo product = product(products.get(line.productId()), warehouse.companyId(), field);
            lines.add(new InventoryDocumentLine(index + 1,
                    lineValues(type, request, line, product, warehouse, destination, field)));
        }
        return lines;
    }

    private InventoryDocumentLine.LineValues lineValues(InventoryDocumentType type, InventoryDocumentRequest request,
                                                        InventoryLineRequest line, ProductInfo product,
                                                        WarehouseInfo warehouse, WarehouseInfo destination,
                                                        String field) {
        BigDecimal factor = masterData.conversionFactor(product.id(), line.unitId(), request.documentDate());
        BigDecimal baseQuantity = baseQuantity(line.quantity(), factor, field);
        MovementDirection direction = direction(type, line, field);
        boolean inbound = direction == MovementDirection.IN;
        requireLocation(line.locationId(), warehouse.id(), field + ".locationId");
        if (destination != null) {
            requireLocation(line.destinationLocationId(), destination.id(), field + ".destinationLocationId");
        }
        requireLot(product, line, inbound, field);
        List<String> serials = serials(product, line, baseQuantity, field);
        requireStatuses(type, line, field);
        if (inbound && line.unitCost() == null) {
            throw BusinessException.invalidField(field + ".unitCost", "Phải nhập đơn giá cho dòng nhập kho.");
        }
        return new InventoryDocumentLine.LineValues(product.id(), line.unitId(), factor, line.quantity(),
                baseQuantity, type == InventoryDocumentType.PDC ? direction : null, line.locationId(),
                line.destinationLocationId(), line.lotId(), inbound && line.lotId() == null ? trim(line.lotNo()) : null,
                inbound ? line.manufactureDate() : null, inbound ? line.expiryDate() : null, line.qualityStatus(),
                line.targetQualityStatus(), inbound ? line.unitCost() : null, trim(line.lotOverrideReason()),
                trim(line.note()), serials);
    }

    private static ProductInfo product(ProductInfo product, Long companyId, String field) {
        if (product == null || !product.companyId().equals(companyId)) {
            throw BusinessException.invalidField(field + ".productId", "Hàng hóa không tồn tại.");
        }
        if (!product.active()) {
            throw BusinessException.invalidField(field + ".productId", "Hàng " + product.code() + " đã ngừng hoạt động.");
        }
        if (!product.stockTracked()) {
            throw BusinessException.invalidField(field + ".productId", "Hàng " + product.code()
                    + " là dịch vụ, không quản lý tồn kho.");
        }
        return product;
    }

    /** Số lượng quy đổi phải biểu diễn đúng ở 3 chữ số thập phân; không làm tròn ngầm (D-05). */
    private static BigDecimal baseQuantity(BigDecimal quantity, BigDecimal factor, String field) {
        BigDecimal base = quantity.multiply(factor).stripTrailingZeros();
        if (base.scale() > QUANTITY_SCALE) {
            throw BusinessException.invalidField(field + ".quantity",
                    "Số lượng quy đổi về đơn vị cơ sở vượt quá 3 chữ số thập phân.");
        }
        return base.setScale(QUANTITY_SCALE);
    }

    private static MovementDirection direction(InventoryDocumentType type, InventoryLineRequest line, String field) {
        return switch (type) {
            case PNK -> MovementDirection.IN;
            case PDC -> {
                if (line.direction() == null) {
                    throw BusinessException.invalidField(field + ".direction", "Phải chọn tăng hay giảm tồn.");
                }
                yield line.direction();
            }
            default -> MovementDirection.OUT;
        };
    }

    private void requireLocation(Long locationId, Long warehouseId, String field) {
        if (locationId == null) {
            return;
        }
        boolean valid = masterData.location(locationId)
                .filter(found -> found.warehouseId().equals(warehouseId))
                .filter(LocationInfo::active)
                .isPresent();
        if (!valid) {
            throw BusinessException.invalidField(field, "Vị trí không thuộc kho hoặc đã ngừng hoạt động.");
        }
    }

    private void requireLot(ProductInfo product, InventoryLineRequest line, boolean inbound, String field) {
        if (!product.lotTracked()) {
            if (line.lotId() != null || trim(line.lotNo()) != null) {
                throw BusinessException.invalidField(field + ".lotNo", "Hàng " + product.code() + " không quản lý lô.");
            }
            return;
        }
        if (line.lotId() != null) {
            lotRepository.findById(line.lotId()).filter(lot -> lot.getProductId().equals(product.id()))
                    .orElseThrow(() -> BusinessException.invalidField(field + ".lotId", "Lô không thuộc hàng "
                            + product.code() + "."));
            return;
        }
        if (!inbound) {
            return;
        }
        if (trim(line.lotNo()) == null) {
            throw BusinessException.invalidField(field + ".lotNo", "Hàng " + product.code() + " phải nhập số lô.");
        }
        if (product.expiryTracked() && line.expiryDate() == null) {
            throw BusinessException.invalidField(field + ".expiryDate", "Hàng " + product.code()
                    + " phải nhập hạn dùng.");
        }
        if (line.manufactureDate() != null && line.expiryDate() != null
                && !line.expiryDate().isAfter(line.manufactureDate())) {
            throw BusinessException.invalidField(field + ".expiryDate", "Hạn dùng phải sau ngày sản xuất.");
        }
    }

    /** Hàng quản lý serial: mỗi serial một đơn vị cơ sở, không trùng (BR-INV-02). */
    private static List<String> serials(ProductInfo product, InventoryLineRequest line, BigDecimal baseQuantity,
                                        String field) {
        List<String> serials = line.serialNumbers() == null ? List.of()
                : line.serialNumbers().stream().map(String::trim).toList();
        if (!product.serialTracked()) {
            if (!serials.isEmpty()) {
                throw BusinessException.invalidField(field + ".serialNumbers", "Hàng " + product.code()
                        + " không quản lý serial.");
            }
            return serials;
        }
        Set<String> distinct = new HashSet<>(serials);
        if (distinct.size() != serials.size()) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Danh sách serial bị trùng.");
        }
        if (baseQuantity.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Số serial (" + serials.size()
                    + ") phải bằng số lượng theo đơn vị cơ sở (" + baseQuantity.stripTrailingZeros().toPlainString()
                    + ").");
        }
        return serials;
    }

    private static void requireStatuses(InventoryDocumentType type, InventoryLineRequest line, String field) {
        if (type != InventoryDocumentType.PCL) {
            return;
        }
        if (line.qualityStatus() == null || line.targetQualityStatus() == null) {
            throw BusinessException.invalidField(field + ".targetQualityStatus",
                    "Phải chọn trạng thái hiện tại và trạng thái mới.");
        }
        if (line.qualityStatus() == line.targetQualityStatus()) {
            throw BusinessException.invalidField(field + ".targetQualityStatus", "Trạng thái mới phải khác hiện tại.");
        }
        if (line.targetQualityStatus() == QualityStatus.PASSED && line.qualityStatus() == QualityStatus.EXPIRED) {
            throw BusinessException.invalidField(field + ".targetQualityStatus",
                    "Hàng hết hạn không chuyển về ĐẠT được (BR-INV-04).");
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
