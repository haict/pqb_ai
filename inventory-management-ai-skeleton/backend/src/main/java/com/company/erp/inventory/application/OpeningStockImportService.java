package com.company.erp.inventory.application;

import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.application.excel.ExcelSupport;
import com.company.erp.common.application.excel.RowErrors;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.FieldViolation;
import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.application.dto.InventoryDocumentRequest;
import com.company.erp.inventory.application.dto.InventoryDocumentResponse;
import com.company.erp.inventory.application.dto.InventoryLineRequest;
import com.company.erp.inventory.application.dto.OpeningStockImportResponse;
import com.company.erp.inventory.domain.InventoryDocumentType;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.UnitInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.CurrentUser;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nhập tồn kho đầu kỳ từ Excel (BR-INV-03, BR-UX-05): mỗi kho trong file thành một phiếu nhập kho nghiệp vụ
 * {@code OPENING} ở trạng thái nháp, sau đó ghi sổ như phiếu lập tay. Có lỗi ở bất kỳ dòng nào thì
 * không lập phiếu nào; lỗi báo theo dòng Excel.
 */
@Service
public class OpeningStockImportService {

    static final List<String> HEADERS = List.of("Mã kho*", "Mã hàng*", "Mã ĐVT (trống = ĐVT cơ sở)", "Số lượng*",
            "Đơn giá*", "Số lô", "Ngày sản xuất (dd/mm/yyyy)", "Hạn dùng (dd/mm/yyyy)",
            "Số serial (phân tách dấu phẩy)", "Trạng thái chất lượng", "Ghi chú");
    /** Danh sách chọn cột trạng thái: mã + diễn giải, khi nhập chỉ đọc phần mã; để trống là ĐẠT (D-56). */
    static final List<String> QUALITY_OPTIONS = List.of("PASSED (đạt)", "PENDING_INSPECTION (chờ kiểm tra)",
            "QUARANTINE (cách ly)", "FAILED (không đạt)", "EXPIRED (hết hạn)");
    private static final int WAREHOUSE = 0;
    private static final int PRODUCT = 1;
    private static final int UNIT = 2;
    private static final int QUANTITY = 3;
    private static final int UNIT_COST = 4;
    private static final int LOT_NO = 5;
    private static final int MANUFACTURE_DATE = 6;
    private static final int EXPIRY_DATE = 7;
    private static final int SERIALS = 8;
    private static final int QUALITY = 9;
    private static final int NOTE = 10;
    /** Giới hạn số dòng của một chứng từ kho ({@link InventoryDocumentRequest}); kho nhiều dòng hơn được tách phiếu. */
    private static final int LINES_PER_DOCUMENT = 500;
    private static final Pattern LINE_FIELD = Pattern.compile("^lines\\[(\\d+)]\\.?(.*)$");

    private final InventoryDocumentService documentService;
    private final InventoryAccess access;
    private final MasterDataQueryService masterData;
    private final IdempotencyService idempotency;

    public OpeningStockImportService(InventoryDocumentService documentService, InventoryAccess access,
                                     MasterDataQueryService masterData, IdempotencyService idempotency) {
        this.documentService = documentService;
        this.access = access;
        this.masterData = masterData;
        this.idempotency = idempotency;
    }

    private record ParsedLine(int excelRow, Long warehouseId, InventoryLineRequest line) {
    }

    private record Lookups(Map<String, WarehouseInfo> warehouses, Map<String, ProductInfo> products,
                           Map<String, UnitInfo> units) {
    }

    public byte[] template() {
        List<List<String>> guide = new ArrayList<>();
        guide.add(List.of("Cột", "Giá trị hợp lệ"));
        guide.add(List.of("Mã kho, mã hàng, mã ĐVT", "Mã đã khai báo trong danh mục, đang hoạt động. Kho phải là kho "
                + "vật lý trong phạm vi được phép. Mỗi kho trong file lập một phiếu nhập kho đầu kỳ (nháp)."));
        guide.add(List.of("Số lượng", "Lớn hơn 0, theo ĐVT của dòng"));
        guide.add(List.of("Đơn giá", "Giá vốn đầu kỳ của một ĐVT, lớn hơn hoặc bằng 0"));
        guide.add(List.of("Số lô, hạn dùng", "Bắt buộc với hàng quản lý theo lô / hạn dùng"));
        guide.add(List.of("Số serial", "Bắt buộc với hàng quản lý serial; số serial bằng số lượng theo ĐVT cơ sở"));
        guide.add(List.of("Trạng thái chất lượng", "Chọn trong danh sách: " + String.join(", ", QUALITY_OPTIONS)
                + ". Để trống = PASSED (đạt)."));
        guide.add(List.of("Ngày chứng từ", "Chọn trên màn hình khi nhập file"));
        guide.add(List.of("Số dòng tối đa", String.valueOf(ExcelSupport.MAX_ROWS)));
        Workbook workbook = ExcelSupport.template("Ton dau ky", HEADERS, guide);
        ExcelSupport.dropdown(workbook, QUALITY, QUALITY_OPTIONS);
        return ExcelSupport.toBytes(workbook);
    }

    @Transactional
    public OpeningStockImportResponse importFile(CurrentUser actor, Long companyId, LocalDate documentDate,
                                                 String idempotencyKey, MultipartFile file) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        access.requirePermission(actor, InventoryDocumentType.PNK.permission("create"));
        if (documentDate == null) {
            throw BusinessException.invalidField("documentDate", "Chọn ngày chứng từ.");
        }
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey,
                "POST /api/v1/inventory-documents/opening-import",
                Map.of("companyId", companyId, "documentDate", documentDate.toString(),
                        "sha256", ExcelSupport.sha256(file)));
        return idempotency.execute(command, OpeningStockImportResponse.class, () -> {
            List<ParsedLine> lines = parse(companyId, file);
            return createDocuments(actor, documentDate, file.getOriginalFilename(), lines);
        });
    }

    private List<ParsedLine> parse(Long companyId, MultipartFile file) {
        try (Workbook workbook = ExcelSupport.open(file)) {
            List<Row> rows = ExcelSupport.dataRows(workbook.getSheetAt(0), HEADERS.size());
            Lookups lookups = lookups(companyId, rows);
            RowErrors errors = new RowErrors();
            List<ParsedLine> parsed = new ArrayList<>();
            for (Row row : rows) {
                errors.row(row.getRowNum() + 1);
                ParsedLine line = parseRow(row, lookups, errors);
                if (line != null) {
                    parsed.add(line);
                }
            }
            errors.throwIfAny();
            return parsed;
        } catch (IOException ex) {
            throw BusinessException.invalidField("file", "Không đọc được file Excel (.xlsx).");
        }
    }

    private Lookups lookups(Long companyId, List<Row> rows) {
        return new Lookups(
                ExcelSupport.byCode(masterData.warehousesByCodes(companyId, ExcelSupport.codes(rows, WAREHOUSE)),
                        WarehouseInfo::code),
                ExcelSupport.byCode(masterData.productsByCodes(companyId, ExcelSupport.codes(rows, PRODUCT)),
                        ProductInfo::code),
                ExcelSupport.byCode(masterData.unitsByCodes(companyId, ExcelSupport.codes(rows, UNIT)),
                        UnitInfo::code));
    }

    private static ParsedLine parseRow(Row row, Lookups lookups, RowErrors errors) {
        WarehouseInfo warehouse = warehouse(ExcelSupport.text(row, WAREHOUSE), lookups, errors);
        ProductInfo product = product(ExcelSupport.text(row, PRODUCT), lookups, errors);
        Long unitId = unit(ExcelSupport.text(row, UNIT), product, lookups, errors);
        BigDecimal quantity = ExcelSupport.decimal(row, QUANTITY, errors, "quantity");
        if (quantity == null || quantity.signum() <= 0) {
            errors.add("quantity", "Số lượng bắt buộc và phải lớn hơn 0.");
        }
        BigDecimal unitCost = ExcelSupport.decimal(row, UNIT_COST, errors, "unitCost");
        if (unitCost == null || unitCost.signum() < 0) {
            errors.add("unitCost", "Đơn giá bắt buộc và không được âm.");
        }
        LocalDate manufactureDate = ExcelSupport.date(row, MANUFACTURE_DATE, errors, "manufactureDate");
        LocalDate expiryDate = ExcelSupport.date(row, EXPIRY_DATE, errors, "expiryDate");
        QualityStatus quality = quality(ExcelSupport.text(row, QUALITY), errors);
        if (warehouse == null || product == null || unitId == null || quantity == null || quantity.signum() <= 0
                || unitCost == null || unitCost.signum() < 0) {
            return null;
        }
        InventoryLineRequest line = new InventoryLineRequest(product.id(), unitId, quantity, null, null, null, null,
                ExcelSupport.nullable(row, LOT_NO), manufactureDate, expiryDate, quality, null, unitCost, null,
                ExcelSupport.nullable(row, NOTE), serials(ExcelSupport.text(row, SERIALS)));
        return new ParsedLine(row.getRowNum() + 1, warehouse.id(), line);
    }

    private static WarehouseInfo warehouse(String code, Lookups lookups, RowErrors errors) {
        if (code.isEmpty()) {
            errors.add("warehouseId", "Mã kho bắt buộc.");
            return null;
        }
        WarehouseInfo warehouse = lookups.warehouses().get(code);
        if (warehouse == null || !warehouse.active() || !warehouse.physical()) {
            errors.add("warehouseId", "Kho '" + code + "' không tồn tại, đã ngừng hoạt động hoặc là kho ảo.");
            return null;
        }
        return warehouse;
    }

    private static ProductInfo product(String code, Lookups lookups, RowErrors errors) {
        if (code.isEmpty()) {
            errors.add("productId", "Mã hàng bắt buộc.");
            return null;
        }
        ProductInfo product = lookups.products().get(code);
        if (product == null) {
            errors.add("productId", "Hàng '" + code + "' không tồn tại.");
        }
        return product;
    }

    private static Long unit(String code, ProductInfo product, Lookups lookups, RowErrors errors) {
        if (code.isEmpty()) {
            return product == null ? null : product.baseUnitId();
        }
        UnitInfo unit = lookups.units().get(code);
        if (unit == null || !unit.active()) {
            errors.add("unitId", "Đơn vị tính '" + code + "' không tồn tại hoặc đã ngừng hoạt động.");
            return null;
        }
        return unit.id();
    }

    private static QualityStatus quality(String text, RowErrors errors) {
        if (text.isEmpty()) {
            return QualityStatus.PASSED;
        }
        try {
            QualityStatus status = QualityStatus.valueOf(ExcelSupport.leadingCode(text).toUpperCase(Locale.ROOT));
            if (status != QualityStatus.RECALLED) {
                return status;
            }
        } catch (IllegalArgumentException ex) {
            // Báo lỗi bên dưới.
        }
        errors.add("qualityStatus", "Trạng thái chất lượng '" + text + "' không hợp lệ.");
        return null;
    }

    private static List<String> serials(String text) {
        return text.isEmpty() ? null : ExcelSupport.serials(text);
    }

    /**
     * Lập phiếu nháp theo từng kho. Lỗi nghiệp vụ của phiếu (lô, hạn dùng, serial, quy đổi ĐVT...) được đổi về dòng
     * Excel; gom lỗi mọi phiếu rồi mới báo để người dùng sửa một lần.
     */
    private OpeningStockImportResponse createDocuments(CurrentUser actor, LocalDate documentDate, String fileName,
                                                       List<ParsedLine> lines) {
        Map<Long, List<ParsedLine>> byWarehouse = lines.stream()
                .collect(Collectors.groupingBy(ParsedLine::warehouseId, LinkedHashMap::new, Collectors.toList()));
        RowErrors errors = new RowErrors();
        List<InventoryDocumentResponse> created = new ArrayList<>();
        for (Map.Entry<Long, List<ParsedLine>> entry : byWarehouse.entrySet()) {
            List<ParsedLine> warehouseLines = entry.getValue();
            for (int from = 0; from < warehouseLines.size(); from += LINES_PER_DOCUMENT) {
                List<ParsedLine> chunk = warehouseLines.subList(from,
                        Math.min(from + LINES_PER_DOCUMENT, warehouseLines.size()));
                InventoryDocumentRequest request = new InventoryDocumentRequest(InventoryDocumentType.PNK,
                        DocumentPostingPlanner.OPENING, documentDate, null, entry.getKey(), null, null, null, null,
                        fileName == null ? null : "Nhập từ file " + fileName, null, null,
                        chunk.stream().map(ParsedLine::line).toList(), null);
                try {
                    created.add(documentService.createDraft(actor, request));
                } catch (BusinessException ex) {
                    report(ex, chunk, errors);
                }
            }
        }
        errors.throwIfAny();
        return new OpeningStockImportResponse(lines.size(), created.stream().map(InventoryDocumentResponse::id).toList(),
                created.stream().map(InventoryDocumentResponse::documentNo).toList());
    }

    private static void report(BusinessException ex, List<ParsedLine> chunk, RowErrors errors) {
        List<FieldViolation> violations = ex.violations().isEmpty()
                ? List.of(new FieldViolation("row", ex.getMessage())) : ex.violations();
        for (FieldViolation violation : violations) {
            Matcher matcher = LINE_FIELD.matcher(violation.field());
            int index = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
            String column = matcher.matches() && !matcher.group(2).isEmpty() ? matcher.group(2) : violation.field();
            errors.row(chunk.get(Math.min(index, chunk.size() - 1)).excelRow());
            errors.add(column, violation.message());
        }
    }
}
