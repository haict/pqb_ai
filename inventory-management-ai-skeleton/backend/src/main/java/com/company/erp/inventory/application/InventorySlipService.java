package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.application.dto.InventoryPrintCompanyResponse;
import com.company.erp.inventory.application.dto.InventorySlipResponse;
import com.company.erp.inventory.domain.InventoryDocument;
import com.company.erp.inventory.domain.InventoryDocumentLine;
import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.domain.InventoryTransaction;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.CompanyRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mẫu in phiếu nhập kho (PNK) / phiếu xuất kho (PXK, PXH) của chứng từ kho đã ghi sổ (D-78); quyền xem theo loại chứng
 * từ, ngoài phạm vi kho trả 404. Đầu phiếu theo công ty người in chọn (D-58), in theo công ty khác ghi nhật ký.
 */
@Service
public class InventorySlipService {

    static final String PRINT_AS_COMPANY = "PRINT_AS_COMPANY";
    private static final Set<InventoryDocumentType> PRINTABLE = EnumSet.of(InventoryDocumentType.PNK,
            InventoryDocumentType.PXK, InventoryDocumentType.PXH);
    private static final int PRICE_SCALE = 4;

    private final InventoryDocumentRepository documentRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryDocumentService documentService;
    private final InventoryDocumentMapper mapper;
    private final InventoryAccess access;
    private final MasterDataQueryService masterData;
    private final OrganizationQueryService organization;

    InventorySlipService(InventoryDocumentRepository documentRepository,
                         InventoryTransactionRepository transactionRepository,
                         InventoryDocumentService documentService, InventoryDocumentMapper mapper,
                         InventoryAccess access, MasterDataQueryService masterData,
                         OrganizationQueryService organization) {
        this.documentRepository = documentRepository;
        this.transactionRepository = transactionRepository;
        this.documentService = documentService;
        this.mapper = mapper;
        this.access = access;
        this.masterData = masterData;
        this.organization = organization;
    }

    /** Công ty được chọn để in đầu phiếu: đang hoạt động, trong phạm vi người dùng. */
    @Transactional(readOnly = true)
    public List<InventoryPrintCompanyResponse> printCompanies(CurrentUser actor) {
        return organization.activeCompanies(actor.dataScope().companyIds()).stream()
                .map(company -> new InventoryPrintCompanyResponse(company.id(), company.code(), company.name(),
                        company.address()))
                .toList();
    }

    /** {@code printCompanyId} trống: đầu phiếu theo công ty của chứng từ. */
    @Transactional
    public InventorySlipResponse slip(CurrentUser actor, Long id, Long printCompanyId) {
        InventoryDocument document = documentRepository.findById(id).orElseThrow(BusinessException::notFound);
        documentService.requireVisible(actor, document);
        if (!PRINTABLE.contains(document.getDocumentType())) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chỉ in phiếu nhập kho, phiếu xuất kho, "
                    + "phiếu xuất hủy.");
        }
        if (document.getStatus() != InventoryDocumentStatus.POSTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ " + document.getDocumentNo()
                    + " chưa ghi sổ (hoặc đã đảo) nên chưa in phiếu được.");
        }
        CompanyRef company = printCompany(actor, document, printCompanyId);
        PartnerInfo partner = document.getPartnerId() == null ? null
                : masterData.partner(document.getPartnerId()).orElse(null);
        Map<Long, WarehouseInfo> warehouses = masterData.warehouses(document.warehouseIds()).stream()
                .collect(Collectors.toMap(WarehouseInfo::id, Function.identity()));
        List<InventorySlipResponse.Line> lines = lines(document, warehouses);
        BigDecimal total = lines.stream().map(InventorySlipResponse.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<WarehouseInfo> slipWarehouses = document.getLines().stream().map(document::warehouseOf).distinct()
                .map(warehouses::get).filter(Objects::nonNull).toList();
        return new InventorySlipResponse(company == null ? null : company.name(),
                company == null ? null : company.address(), document.getId(), document.getDocumentType(),
                document.getDocumentNo(), document.getPostingDate(), partner == null ? null : partner.name(),
                partner == null ? null : partner.address(), partner == null ? null : partner.taxCode(),
                description(document), document.getSourceNo(),
                slipWarehouses.stream().map(WarehouseInfo::code).collect(Collectors.joining(", ")),
                slipWarehouses.stream().map(WarehouseInfo::name).collect(Collectors.joining(", ")), lines, total);
    }

    private CompanyRef printCompany(CurrentUser actor, InventoryDocument document, Long printCompanyId) {
        if (printCompanyId == null || printCompanyId.equals(document.getCompanyId())) {
            return organization.company(document.getCompanyId()).orElse(null);
        }
        CompanyRef company = organization.activeCompanies(actor.dataScope().companyIds()).stream()
                .filter(candidate -> candidate.id().equals(printCompanyId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField("companyId",
                        "Công ty in phiếu không tồn tại, đã ngừng hoạt động hoặc ngoài phạm vi của bạn."));
        access.audit(actor, PRINT_AS_COMPANY, InventoryAccess.ENTITY_DOCUMENT, document.getId(),
                document.getDocumentNo(), "In phiếu kho theo công ty " + company.code() + " - " + company.name(),
                Map.of("companyId", document.getCompanyId()), Map.of("printCompanyId", company.id()),
                document.getCompanyId(), document.getBranchId());
        return company;
    }

    private List<InventorySlipResponse.Line> lines(InventoryDocument document, Map<Long, WarehouseInfo> warehouses) {
        List<InventoryTransaction> transactions = transactionRepository
                .findByReferenceTypeAndReferenceIdOrderByIdAsc(document.getDocumentType().name(), document.getId())
                .stream().filter(transaction -> transaction.getReversalOfId() == null).toList();
        Map<Long, List<InventoryTransaction>> byLine = transactions.stream()
                .filter(transaction -> transaction.getReferenceLineId() != null)
                .collect(Collectors.groupingBy(InventoryTransaction::getReferenceLineId));
        Map<Long, ProductInfo> products = mapper.products(document.getLines().stream()
                .map(InventoryDocumentLine::getProductId).collect(Collectors.toSet()));
        Map<Long, InventoryLot> lots = mapper.lots(transactions.stream().map(InventoryTransaction::getLotId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, InventorySerial> serials = mapper.serials(transactions.stream()
                .map(InventoryTransaction::getSerialId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<InventorySlipResponse.Line> lines = new ArrayList<>();
        for (InventoryDocumentLine line : document.getLines()) {
            List<InventoryTransaction> posted = byLine.getOrDefault(line.getId(), List.of());
            ProductInfo product = products.get(line.getProductId());
            WarehouseInfo warehouse = warehouses.get(document.warehouseOf(line));
            BigDecimal amount = amount(line, posted);
            lines.add(new InventorySlipResponse.Line(lines.size() + 1, product == null ? null : product.code(),
                    product == null ? null : product.name(), warehouse == null ? null : warehouse.code(),
                    line.getUnitId(), line.getQuantity(), unitPrice(line, amount), amount,
                    posted.stream().map(transaction -> item(transaction, lots, serials)).toList()));
        }
        return lines;
    }

    /** Phiếu nhập: thành tiền của dòng; phiếu xuất: tổng giá trị trên sổ kho (chưa có giá vốn thì 0, D-51). */
    private static BigDecimal amount(InventoryDocumentLine line, List<InventoryTransaction> posted) {
        if (line.getAmount() != null) {
            return line.getAmount();
        }
        return posted.stream().map(InventoryTransaction::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal unitPrice(InventoryDocumentLine line, BigDecimal amount) {
        if (line.getUnitCost() != null) {
            return line.getUnitCost();
        }
        return line.getQuantity().signum() == 0 ? BigDecimal.ZERO
                : amount.divide(line.getQuantity(), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static InventorySlipResponse.Item item(InventoryTransaction transaction, Map<Long, InventoryLot> lots,
                                                   Map<Long, InventorySerial> serials) {
        InventoryLot lot = transaction.getLotId() == null ? null : lots.get(transaction.getLotId());
        InventorySerial serial = transaction.getSerialId() == null ? null : serials.get(transaction.getSerialId());
        return new InventorySlipResponse.Item(lot == null ? null : lot.getLotNo(),
                lot == null ? null : lot.getExpiryDate(), serial == null ? null : serial.getSerialNo(),
                transaction.getQuantity());
    }

    /** Ghi chú của phiếu; trống thì lý do, sau đó theo chứng từ nguồn hoặc loại phiếu. */
    private static String description(InventoryDocument document) {
        if (document.getNote() != null) {
            return document.getNote();
        }
        if (document.getReasonText() != null) {
            return document.getReasonText();
        }
        String text = switch (document.getDocumentType()) {
            case PNK -> "Nhập kho";
            case PXH -> "Xuất hủy";
            default -> "Xuất kho";
        };
        return document.getSourceNo() == null ? text : text + " theo " + document.getSourceNo();
    }
}
