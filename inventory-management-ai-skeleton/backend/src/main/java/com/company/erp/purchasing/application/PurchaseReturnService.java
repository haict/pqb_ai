package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.StockInService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.CommentRequest;
import com.company.erp.purchasing.application.dto.PurchaseReturnLineRequest;
import com.company.erp.purchasing.application.dto.PurchaseReturnLineResponse;
import com.company.erp.purchasing.application.dto.PurchaseReturnRequest;
import com.company.erp.purchasing.application.dto.PurchaseReturnResponse;
import com.company.erp.purchasing.application.dto.ReasonRequest;
import com.company.erp.purchasing.application.dto.ReturnableLineResponse;
import com.company.erp.purchasing.domain.InspectionResult;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseInvoice;
import com.company.erp.purchasing.domain.PurchaseInvoiceLine;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import com.company.erp.purchasing.domain.PurchaseReceiptLine;
import com.company.erp.purchasing.domain.PurchaseReturn;
import com.company.erp.purchasing.domain.PurchaseReturnLine;
import com.company.erp.purchasing.domain.PurchaseReturnType;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseInvoiceRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReceiptRepository;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseReturnRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trả hàng nhà cung cấp (THN) / giảm giá hàng mua (GGM), BR-PUR-14: lập theo dòng phiếu nhận đã nhập kho (đúng lô,
 * serial) hoặc dòng hóa đơn mua đã ghi sổ, gửi duyệt (duyệt khi có luồng), duyệt, từ chối, hủy. Ghi sổ, đảo ở
 * {@link PurchaseReturnPostingService}.
 */
@Service
public class PurchaseReturnService {

    static final String PATH = "/api/v1/purchase-returns";
    static final String VIEW = "purchase-return:view";
    static final String CREATE = "purchase-return:create";
    static final String APPROVE = "purchase-return:approve";
    static final String ENTITY = "PURCHASE_RETURN";

    public record ReturnFilter(Long companyId, Long branchId, PurchaseReturnType documentType,
                               PurchaseDocumentStatus status, Long supplierId, LocalDate dateFrom, LocalDate dateTo,
                               String keyword) {
    }

    /** Nguồn đã tra cho một chứng từ: hóa đơn (nếu có), phiếu nhận theo id dòng phiếu nhận. */
    private record Sources(PurchaseInvoice invoice, Map<Long, PurchaseReceipt> receiptsByLine) {

        PurchaseReceiptLine receiptLine(Long lineId) {
            PurchaseReceipt receipt = receiptsByLine.get(lineId);
            return receipt == null ? null : receipt.line(lineId, "lines");
        }
    }

    private final PurchaseReturnRepository repository;
    private final PurchaseInvoiceRepository invoiceRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseDocumentFlow flow;
    private final PurchasingAccess access;
    private final ApprovalService approvalService;
    private final StockInService stockIn;
    private final PurchaseDocumentFlow.Spec<PurchaseReturn, PurchaseReturnResponse> spec;

    @SuppressWarnings("java:S107")
    public PurchaseReturnService(PurchaseReturnRepository repository, PurchaseInvoiceRepository invoiceRepository,
                                 PurchaseReceiptRepository receiptRepository, PurchaseDocumentFlow flow,
                                 PurchasingAccess access, ApprovalService approvalService, StockInService stockIn) {
        this.repository = repository;
        this.invoiceRepository = invoiceRepository;
        this.receiptRepository = receiptRepository;
        this.flow = flow;
        this.access = access;
        this.approvalService = approvalService;
        this.stockIn = stockIn;
        this.spec = new PurchaseDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY,
                repository::findByIdForUpdate, repository, document -> toResponse(document, true),
                PurchaseReturnResponse.class, null, null, document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<PurchaseReturnResponse> search(CurrentUser actor, ReturnFilter filter, Pageable pageable) {
        Page<PurchaseReturn> page = flow.search(actor, VIEW, repository,
                new PurchaseDocumentFlow.Filter<PurchaseReturn>(filter.companyId(), filter.branchId(),
                        filter.status(), filter.dateFrom(), filter.dateTo(), filter.keyword(),
                        (root, query, cb) -> cb.and(
                                filter.supplierId() == null ? cb.conjunction()
                                        : cb.equal(root.get("supplierId"), filter.supplierId()),
                                filter.documentType() == null ? cb.conjunction()
                                        : cb.equal(root.get("documentType"), filter.documentType()))),
                pageable);
        Map<Long, PartnerInfo> suppliers = access.partners(page.getContent().stream()
                .map(PurchaseReturn::getSupplierId).toList());
        return page.map(document -> toResponse(document, suppliers.get(document.getSupplierId()), false));
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /**
     * Dòng còn trả / giảm giá được của nhà cung cấp trong chi nhánh: theo hóa đơn {@code invoiceId}, hoặc (trống)
     * dòng phiếu nhận đã nhập kho chưa có hóa đơn.
     */
    @Transactional(readOnly = true)
    public List<ReturnableLineResponse> returnableLines(CurrentUser actor, Long branchId, Long supplierId,
                                                        Long invoiceId) {
        access.requirePermission(actor, VIEW);
        BranchRef branch = access.usableBranch(actor, branchId);
        List<ReturnableLineResponse> rows = new ArrayList<>();
        if (invoiceId != null) {
            PurchaseInvoice invoice = postedInvoice(branch, supplierId, invoiceId);
            Map<Long, PurchaseReceipt> receipts = receiptsByLine(invoice.getLines().stream()
                    .map(PurchaseInvoiceLine::getReceiptLineId).filter(Objects::nonNull).toList());
            Map<Long, ProductInfo> products = access.products(invoice.getLines().stream()
                    .map(PurchaseInvoiceLine::getProductId).toList());
            for (PurchaseInvoiceLine line : invoice.getLines()) {
                PurchaseReceipt receipt = line.isGoods() ? receipts.get(line.getReceiptLineId()) : null;
                PurchaseReceiptLine receiptLine = receipt == null ? null : receipt.line(line.getReceiptLineId(), "x");
                BigDecimal returnable = receiptLine == null ? BigDecimal.ZERO
                        : line.returnableQuantity().min(receiptLine.returnableQuantity());
                if (returnable.signum() <= 0 && line.remainingAmount().signum() <= 0) {
                    continue;
                }
                rows.add(row(receipt, receiptLine, line, products.get(line.getProductId()), returnable,
                        line.getUnitPrice(), line.getTaxRate(), line.remainingAmount(), line.getConversionFactor(),
                        line.getUnitId()));
            }
            return rows;
        }
        List<PurchaseReceipt> receipts = receiptRepository.findStocked(branch.companyId(), branch.id(), supplierId,
                PurchaseDocumentStatus.POSTED);
        Map<Long, ProductInfo> products = access.products(receipts.stream()
                .flatMap(receipt -> receipt.getLines().stream()).map(PurchaseReceiptLine::getProductId).toList());
        for (PurchaseReceipt receipt : receipts) {
            for (PurchaseReceiptLine line : receipt.getLines()) {
                BigDecimal returnable = line.uninvoicedQuantity().min(line.returnableQuantity());
                if (!line.getInspectionResult().stocked() || returnable.signum() <= 0) {
                    continue;
                }
                rows.add(row(receipt, line, null, products.get(line.getProductId()), returnable, line.getUnitCost(),
                        null, null, line.getConversionFactor(), line.getUnitId()));
            }
        }
        return rows;
    }

    @Transactional
    public PurchaseReturnResponse create(CurrentUser actor, String idempotencyKey, PurchaseReturnRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, PurchaseReturnResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            PartnerInfo supplier = access.supplier(branch.companyId(), request.supplierId(), "supplierId");
            Sources sources = sources(branch, supplier.id(), request);
            Long warehouseId = request.documentType() == PurchaseReturnType.THN ? warehouse(sources, request) : null;
            PurchaseReturn document = new PurchaseReturn(branch.companyId(), branch.id(),
                    access.nextNumber(branch.companyId(), branch.id(), request.documentType().name(),
                            request.documentDate()), request.documentType(), supplier.id(),
                    request.purchaseInvoiceId(), warehouseId);
            fill(document, sources, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public PurchaseReturnResponse update(CurrentUser actor, Long id, PurchaseReturnRequest request) {
        access.requirePermission(actor, CREATE);
        PurchaseReturn document = flow.lock(actor, id, spec);
        PurchaseDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId()) || !document.getSupplierId().equals(request.supplierId())
                || document.getDocumentType() != request.documentType()
                || !Objects.equals(document.getPurchaseInvoiceId(), request.purchaseInvoiceId())) {
            throw BusinessException.invalidField("supplierId", "Không đổi loại, chi nhánh, nhà cung cấp, hóa đơn sau "
                    + "khi lập.");
        }
        BranchRef branch = access.usableBranch(actor, document.getBranchId());
        Sources sources = sources(branch, document.getSupplierId(), request);
        if (document.isGoodsReturn() && !document.getWarehouseId().equals(warehouse(sources, request))) {
            throw BusinessException.invalidField("lines", "Các dòng trả hàng phải cùng kho của chứng từ.");
        }
        fill(document, sources, request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public PurchaseReturnResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public PurchaseReturnResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseReturnResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public PurchaseReturnResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    PurchaseDocumentFlow.Spec<PurchaseReturn, PurchaseReturnResponse> spec() {
        return spec;
    }

    private void fill(PurchaseReturn document, Sources sources, PurchaseReturnRequest request) {
        Map<Long, ProductInfo> products = access.products(productIds(sources));
        List<PurchaseReturnLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseReturnLineRequest line = request.lines().get(i);
            String field = "lines[" + i + "]";
            if (!seen.add(line.invoiceLineId() + "/" + line.receiptLineId())) {
                throw BusinessException.invalidField(field, "Mỗi dòng nguồn chỉ xuất hiện một lần.");
            }
            lines.add(new PurchaseReturnLine(i + 1, request.documentType() == PurchaseReturnType.GGM
                    ? discountLine(sources, line, field)
                    : returnLine(sources, line, products, field)));
        }
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new PurchaseReturn.Header(request.documentDate(), postingDate,
                PurchaseRequisitionService.trim(request.description())), lines);
    }

    /** Dòng trả hàng: không vượt số còn trả được của dòng phiếu nhận (và dòng hóa đơn nếu có), đúng serial đã nhận. */
    private PurchaseReturnLine.Values returnLine(Sources sources, PurchaseReturnLineRequest line,
                                                 Map<Long, ProductInfo> products, String field) {
        if (line.quantity() == null) {
            throw BusinessException.invalidField(field + ".quantity", "Phải nhập số lượng trả.");
        }
        PurchaseInvoiceLine invoiceLine = null;
        Long receiptLineId = line.receiptLineId();
        if (sources.invoice() != null) {
            invoiceLine = invoiceLine(sources, line, field);
            if (!invoiceLine.isGoods()) {
                throw BusinessException.invalidField(field + ".invoiceLineId", "Dòng dịch vụ không trả hàng được; "
                        + "dùng giảm giá hàng mua.");
            }
            receiptLineId = invoiceLine.getReceiptLineId();
        }
        PurchaseReceiptLine receiptLine = receiptLineId == null ? null : sources.receiptLine(receiptLineId);
        if (receiptLine == null || !receiptLine.getInspectionResult().stocked()) {
            throw BusinessException.invalidField(field + ".receiptLineId", "Dòng phiếu nhận hàng không tồn tại, khác "
                    + "chi nhánh / nhà cung cấp, chưa nhập kho hoặc là hàng trả lại khi nhận.");
        }
        BigDecimal base = PurchasingAccess.baseQuantity(line.quantity(), receiptLine.getConversionFactor(),
                field + ".quantity");
        BigDecimal limit = invoiceLine != null ? invoiceLine.returnableQuantity().min(receiptLine.returnableQuantity())
                : receiptLine.uninvoicedQuantity().min(receiptLine.returnableQuantity());
        if (base.compareTo(limit) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": chỉ còn trả được "
                    + limit.stripTrailingZeros().toPlainString() + " (đơn vị cơ sở)"
                    + (invoiceLine == null ? " chưa có hóa đơn." : " theo hóa đơn."));
        }
        List<String> serials = serials(products.get(receiptLine.getProductId()), receiptLine, line, base, field);
        QualityStatus status = line.qualityStatus() != null ? line.qualityStatus()
                : receiptLine.getInspectionResult() == InspectionResult.QUARANTINE ? QualityStatus.QUARANTINE
                : QualityStatus.PASSED;
        return new PurchaseReturnLine.Values(receiptLine.getId(), invoiceLine == null ? null : invoiceLine.getId(),
                receiptLine.getProductId(), receiptLine.getUnitId(), receiptLine.getConversionFactor(),
                line.quantity(), base, receiptLine.getLotNo(), status,
                invoiceLine != null ? invoiceLine.getUnitPrice() : receiptLine.getUnitCost(), null,
                invoiceLine == null ? null : invoiceLine.getTaxRateId(),
                invoiceLine == null ? null : invoiceLine.getTaxRate(),
                PurchaseRequisitionService.trim(line.description()), serials);
    }

    /** Dòng giảm giá: theo dòng hóa đơn, không vượt giá trị còn lại (BR-PUR-14). */
    private PurchaseReturnLine.Values discountLine(Sources sources, PurchaseReturnLineRequest line, String field) {
        if (line.amount() == null) {
            throw BusinessException.invalidField(field + ".amount", "Phải nhập số tiền giảm giá.");
        }
        PurchaseInvoiceLine invoiceLine = invoiceLine(sources, line, field);
        BigDecimal amount = line.amount().setScale(0, RoundingMode.HALF_UP);
        if (amount.compareTo(invoiceLine.remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + invoiceLine.getLineNo()
                    + " của hóa đơn chỉ còn giá trị " + invoiceLine.remainingAmount().stripTrailingZeros()
                    .toPlainString() + ".");
        }
        return new PurchaseReturnLine.Values(invoiceLine.getReceiptLineId(), invoiceLine.getId(),
                invoiceLine.getProductId(), invoiceLine.getUnitId(), invoiceLine.getConversionFactor(), null, null,
                null, null, null, amount, invoiceLine.getTaxRateId(), invoiceLine.getTaxRate(),
                PurchaseRequisitionService.trim(line.description()), List.of());
    }

    private static PurchaseInvoiceLine invoiceLine(Sources sources, PurchaseReturnLineRequest line, String field) {
        if (line.invoiceLineId() == null) {
            throw BusinessException.invalidField(field + ".invoiceLineId", "Chọn dòng hóa đơn.");
        }
        return sources.invoice().getLines().stream().filter(candidate -> candidate.getId().equals(line.invoiceLineId()))
                .findFirst().orElseThrow(() -> BusinessException.invalidField(field + ".invoiceLineId",
                        "Dòng không thuộc hóa đơn " + sources.invoice().getDocumentNo() + "."));
    }

    /** Hàng quản lý serial: đúng số lượng, thuộc serial đã nhận ở dòng phiếu nhận (BR-PUR-14). */
    private static List<String> serials(ProductInfo product, PurchaseReceiptLine receiptLine,
                                        PurchaseReturnLineRequest line, BigDecimal base, String field) {
        List<String> serials = line.serialNumbers() == null ? List.of()
                : line.serialNumbers().stream().map(String::trim).toList();
        if (product == null || !product.serialTracked()) {
            if (!serials.isEmpty()) {
                throw BusinessException.invalidField(field + ".serialNumbers", "Hàng không quản lý serial.");
            }
            return serials;
        }
        if (new HashSet<>(serials).size() != serials.size()
                || base.compareTo(BigDecimal.valueOf(serials.size())) != 0) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Số serial phải bằng số lượng trả và không "
                    + "trùng.");
        }
        if (!receiptLine.getSerialNumbers().containsAll(serials)) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Serial không thuộc dòng phiếu nhận gốc.");
        }
        return serials;
    }

    private Sources sources(BranchRef branch, Long supplierId, PurchaseReturnRequest request) {
        PurchaseInvoice invoice = null;
        if (request.purchaseInvoiceId() != null) {
            invoice = postedInvoice(branch, supplierId, request.purchaseInvoiceId());
        } else if (request.documentType() == PurchaseReturnType.GGM) {
            throw BusinessException.invalidField("purchaseInvoiceId", "Giảm giá hàng mua phải theo hóa đơn mua.");
        }
        List<Long> receiptLineIds = invoice != null
                ? invoice.getLines().stream().map(PurchaseInvoiceLine::getReceiptLineId).filter(Objects::nonNull).toList()
                : request.lines().stream().map(PurchaseReturnLineRequest::receiptLineId).filter(Objects::nonNull).toList();
        Map<Long, PurchaseReceipt> receipts = receiptsByLine(receiptLineIds);
        receipts.values().removeIf(receipt -> !receipt.getCompanyId().equals(branch.companyId())
                || !receipt.getBranchId().equals(branch.id()) || !receipt.getSupplierId().equals(supplierId)
                || receipt.getStatus() != PurchaseDocumentStatus.POSTED || !receipt.isStocked());
        return new Sources(invoice, receipts);
    }

    private PurchaseInvoice postedInvoice(BranchRef branch, Long supplierId, Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .filter(found -> found.getCompanyId().equals(branch.companyId())
                        && found.getBranchId().equals(branch.id()) && found.getSupplierId().equals(supplierId)
                        && found.getStatus() == PurchaseDocumentStatus.POSTED)
                .orElseThrow(() -> BusinessException.invalidField("purchaseInvoiceId", "Hóa đơn mua không tồn tại, "
                        + "khác chi nhánh / nhà cung cấp hoặc chưa ghi sổ."));
    }

    private Map<Long, PurchaseReceipt> receiptsByLine(List<Long> receiptLineIds) {
        Map<Long, PurchaseReceipt> byLine = new HashMap<>();
        if (!receiptLineIds.isEmpty()) {
            receiptRepository.findByLineIds(receiptLineIds).forEach(receipt -> receipt.getLines()
                    .forEach(line -> byLine.put(line.getId(), receipt)));
        }
        return byLine;
    }

    /** Kho xuất trả: kho của phiếu nhận; mọi dòng phải cùng kho (một phiếu xuất). */
    private static Long warehouse(Sources sources, PurchaseReturnRequest request) {
        Set<Long> warehouses = new HashSet<>();
        for (PurchaseReturnLineRequest line : request.lines()) {
            Long receiptLineId = line.receiptLineId();
            if (sources.invoice() != null && line.invoiceLineId() != null) {
                receiptLineId = sources.invoice().getLines().stream()
                        .filter(candidate -> candidate.getId().equals(line.invoiceLineId())).findFirst()
                        .map(PurchaseInvoiceLine::getReceiptLineId).orElse(null);
            }
            PurchaseReceipt receipt = receiptLineId == null ? null : sources.receiptsByLine().get(receiptLineId);
            if (receipt != null) {
                warehouses.add(receipt.getWarehouseId());
            }
        }
        if (warehouses.size() > 1) {
            throw BusinessException.invalidField("lines", "Các dòng trả hàng phải cùng một kho; lập phiếu riêng cho "
                    + "từng kho.");
        }
        return warehouses.stream().findFirst().orElseThrow(() -> BusinessException.invalidField(
                "lines[0].receiptLineId", "Dòng phiếu nhận hàng không hợp lệ."));
    }

    private static List<Long> productIds(Sources sources) {
        List<Long> ids = new ArrayList<>();
        sources.receiptsByLine().values().forEach(receipt -> receipt.getLines()
                .forEach(line -> ids.add(line.getProductId())));
        if (sources.invoice() != null) {
            sources.invoice().getLines().forEach(line -> ids.add(line.getProductId()));
        }
        return ids;
    }

    private static ReturnableLineResponse row(PurchaseReceipt receipt, PurchaseReceiptLine receiptLine,
                                              PurchaseInvoiceLine invoiceLine, ProductInfo product,
                                              BigDecimal returnableBase, BigDecimal unitPrice, BigDecimal taxRate,
                                              BigDecimal remainingAmount, BigDecimal factor, Long unitId) {
        return new ReturnableLineResponse(receipt == null ? null : receipt.getId(),
                receipt == null ? null : receipt.getDocumentNo(), receipt == null ? null : receipt.getDocumentDate(),
                receiptLine == null ? null : receiptLine.getId(), receipt == null ? null : receipt.getWarehouseId(),
                invoiceLine == null ? null : invoiceLine.getId(), product == null ? null : product.id(),
                product == null ? null : product.code(), product == null ? null : product.name(), unitId,
                receiptLine == null ? null : receiptLine.getLotNo(),
                receiptLine == null ? null : receiptLine.getExpiryDate(),
                receiptLine == null ? null : receiptLine.getInspectionResult() == InspectionResult.QUARANTINE
                        ? QualityStatus.QUARANTINE : QualityStatus.PASSED,
                returnableBase.divide(factor, 3, RoundingMode.DOWN), unitPrice, taxRate, remainingAmount,
                receiptLine == null ? List.of() : List.copyOf(receiptLine.getSerialNumbers()));
    }

    PurchaseReturnResponse toResponse(PurchaseReturn document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getSupplierId())).get(document.getSupplierId()),
                detailed);
    }

    private PurchaseReturnResponse toResponse(PurchaseReturn document, PartnerInfo supplier, boolean detailed) {
        List<PurchaseReturnLineResponse> lines = List.of();
        PurchaseInvoice invoice = null;
        String inventoryNo = null;
        if (detailed) {
            invoice = document.getPurchaseInvoiceId() == null ? null
                    : invoiceRepository.findById(document.getPurchaseInvoiceId()).orElse(null);
            Map<Long, PurchaseReceipt> receipts = receiptsByLine(document.getLines().stream()
                    .map(PurchaseReturnLine::getReceiptLineId).filter(Objects::nonNull).toList());
            Map<Long, ProductInfo> products = access.products(document.getLines().stream()
                    .map(PurchaseReturnLine::getProductId).toList());
            lines = document.getLines().stream().map(line -> {
                ProductInfo product = products.get(line.getProductId());
                PurchaseReceipt receipt = line.getReceiptLineId() == null ? null : receipts.get(line.getReceiptLineId());
                return new PurchaseReturnLineResponse(line.getId(), line.getLineNo(), line.getReceiptLineId(),
                        receipt == null ? null : receipt.getDocumentNo(), line.getInvoiceLineId(), line.getProductId(),
                        product == null ? null : product.code(), product == null ? null : product.name(),
                        line.getUnitId(), line.getQuantity(), line.getBaseQuantity(), line.getLotNo(),
                        line.getQualityStatus(), line.getUnitPrice(), line.getAmount(), line.getTaxRate(),
                        line.getTaxAmount(), line.getDescription(), List.copyOf(line.getSerialNumbers()));
            }).toList();
            if (document.getInventoryDocumentId() != null) {
                inventoryNo = stockIn.documentNumbers(List.of(document.getInventoryDocumentId()))
                        .get(document.getInventoryDocumentId());
            }
        }
        return new PurchaseReturnResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentType(), document.getDocumentDate(),
                document.getPostingDate(), document.getSupplierId(), supplier == null ? null : supplier.code(),
                supplier == null ? null : supplier.name(), document.getPurchaseInvoiceId(),
                invoice == null ? null : invoice.getDocumentNo(), invoice == null ? null : invoice.invoiceLabel(),
                document.getWarehouseId(), document.getInventoryDocumentId(), inventoryNo, document.getDescription(),
                document.getAmount(), document.getTaxAmount(), document.getTotalAmount(), document.getStatus(),
                document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(),
                document.getReverseDate(), document.getReason(), document.getCreatedBy(), document.getVersion(), lines,
                detailed ? approvalService.history(document.typeCode(), document.getId()) : List.of());
    }
}
