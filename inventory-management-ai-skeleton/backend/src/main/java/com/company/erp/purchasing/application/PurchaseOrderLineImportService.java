package com.company.erp.purchasing.application;

import com.company.erp.common.application.excel.ExcelSupport;
import com.company.erp.common.application.excel.RowErrors;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.UnitInfo;
import com.company.erp.purchasing.application.dto.PurchaseOrderLineImportResponse;
import com.company.erp.purchasing.application.dto.PurchaseOrderLineImportResponse.Line;
import com.company.erp.system.application.CurrentUser;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nhập dòng hàng của đơn mua từ Excel (BR-PUR-04, BR-UX-05): đọc file, tra mã hàng / ĐVT / thuế suất, kiểm tra lô,
 * hạn dùng theo cấu hình hàng và trả danh sách dòng để giao diện điền vào form. Không lưu đơn; người dùng kiểm tra rồi
 * lưu như lập tay (server kiểm tra lại toàn bộ khi lưu). Có lỗi ở bất kỳ dòng nào thì trả lỗi theo dòng Excel.
 */
@Service
public class PurchaseOrderLineImportService {

    static final List<String> HEADERS = List.of("Mã hàng*", "Mã ĐVT (trống = ĐVT cơ sở)", "Số lượng*", "Đơn giá*",
            "Chiết khấu (%)", "Mã thuế suất (trống = mặc định của hàng)", "Số lô", "Ngày sản xuất (dd/mm/yyyy)",
            "Hạn dùng (dd/mm/yyyy)", "Số serial (phân tách dấu phẩy)", "Ghi chú");
    /** Bằng giới hạn số dòng của đơn mua ({@code PurchaseOrderRequest.lines}). */
    static final int MAX_LINES = 200;
    private static final int PRODUCT = 0;
    private static final int UNIT = 1;
    private static final int QUANTITY = 2;
    private static final int UNIT_PRICE = 3;
    private static final int DISCOUNT = 4;
    private static final int TAX_RATE = 5;
    private static final int LOT_NO = 6;
    private static final int MANUFACTURE_DATE = 7;
    private static final int EXPIRY_DATE = 8;
    private static final int SERIALS = 9;
    private static final int NOTE = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PurchasingAccess access;
    private final MasterDataQueryService masterData;
    private final Clock clock;

    public PurchaseOrderLineImportService(PurchasingAccess access, MasterDataQueryService masterData, Clock clock) {
        this.access = access;
        this.masterData = masterData;
        this.clock = clock;
    }

    private record Lookups(Map<String, ProductInfo> products, Map<String, UnitInfo> units,
                           Map<String, TaxRateInfo> taxRates) {
    }

    public byte[] template() {
        List<List<String>> guide = new ArrayList<>();
        guide.add(List.of("Cột", "Giá trị hợp lệ"));
        guide.add(List.of("Nhà cung cấp, kho, hóa đơn", "Chọn trên form lập đơn mua; file chỉ chứa dòng hàng"));
        guide.add(List.of("Mã hàng, mã ĐVT, mã thuế suất", "Mã đã khai báo trong danh mục, đang hoạt động. ĐVT khác "
                + "ĐVT cơ sở phải có hệ số quy đổi"));
        guide.add(List.of("Số lượng, đơn giá", "Số lượng lớn hơn 0; đơn giá trước thuế, lớn hơn hoặc bằng 0"));
        guide.add(List.of("Chiết khấu (%)", "Từ 0 đến 100, để trống = 0"));
        guide.add(List.of("Số lô, hạn dùng", "Bắt buộc với hàng quản lý theo lô / hạn dùng; hàng nhiều lô: mỗi lô "
                + "một dòng. Hàng dịch vụ để trống"));
        guide.add(List.of("Số serial", "Hàng quản lý serial: số serial bằng số lượng theo ĐVT cơ sở"));
        guide.add(List.of("Số dòng tối đa", String.valueOf(MAX_LINES)));
        return ExcelSupport.toBytes(ExcelSupport.template("Dong don mua", HEADERS, guide));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderLineImportResponse parse(CurrentUser actor, Long companyId, LocalDate documentDate,
                                                 MultipartFile file) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        access.requirePermission(actor, "purchase-order:create");
        LocalDate date = documentDate != null ? documentDate : LocalDate.now(clock);
        try (Workbook workbook = ExcelSupport.open(file)) {
            List<Row> rows = dataRows(workbook.getSheetAt(0));
            Lookups lookups = new Lookups(
                    ExcelSupport.byCode(masterData.productsByCodes(companyId, ExcelSupport.codes(rows, PRODUCT)),
                            ProductInfo::code),
                    ExcelSupport.byCode(masterData.unitsByCodes(companyId, ExcelSupport.codes(rows, UNIT)),
                            UnitInfo::code),
                    ExcelSupport.byCode(masterData.taxRatesByCodes(companyId, ExcelSupport.codes(rows, TAX_RATE)),
                            TaxRateInfo::code));
            RowErrors errors = new RowErrors();
            List<Line> lines = new ArrayList<>();
            for (Row row : rows) {
                errors.row(row.getRowNum() + 1);
                Line line = line(row, lookups, date, errors);
                if (line != null) {
                    lines.add(line);
                }
            }
            errors.throwIfAny();
            return new PurchaseOrderLineImportResponse(lines.size(), lines);
        } catch (IOException ex) {
            throw BusinessException.invalidField("file", "Không đọc được file Excel (.xlsx).");
        }
    }

    private static List<Row> dataRows(Sheet sheet) {
        List<Row> rows = ExcelSupport.dataRows(sheet, HEADERS.size());
        if (rows.size() > MAX_LINES) {
            throw BusinessException.invalidField("file", "Đơn mua tối đa " + MAX_LINES + " dòng; file có "
                    + rows.size() + " dòng.");
        }
        return rows;
    }

    private Line line(Row row, Lookups lookups, LocalDate date, RowErrors errors) {
        ProductInfo product = product(ExcelSupport.text(row, PRODUCT), lookups, errors);
        Long unitId = unit(ExcelSupport.text(row, UNIT), product, lookups, date, errors);
        BigDecimal quantity = ExcelSupport.decimal(row, QUANTITY, errors, "quantity");
        if (quantity == null || quantity.signum() <= 0) {
            errors.add("quantity", "Số lượng bắt buộc và phải lớn hơn 0.");
        }
        BigDecimal unitPrice = ExcelSupport.decimal(row, UNIT_PRICE, errors, "unitPrice");
        if (unitPrice == null || unitPrice.signum() < 0) {
            errors.add("unitPrice", "Đơn giá bắt buộc và không được âm.");
        }
        BigDecimal discount = ExcelSupport.decimal(row, DISCOUNT, errors, "discountPercent");
        if (discount != null && (discount.signum() < 0 || discount.compareTo(HUNDRED) > 0)) {
            errors.add("discountPercent", "Chiết khấu phải từ 0 đến 100%.");
        }
        Long taxRateId = taxRate(ExcelSupport.text(row, TAX_RATE), lookups, date, errors);
        boolean service = product != null && !product.stockTracked();
        String lotNo = service ? null : ExcelSupport.nullable(row, LOT_NO);
        LocalDate manufactureDate = service ? null : ExcelSupport.date(row, MANUFACTURE_DATE, errors,
                "manufactureDate");
        LocalDate expiryDate = service ? null : ExcelSupport.date(row, EXPIRY_DATE, errors, "expiryDate");
        if (product != null) {
            requireTracking(product, lotNo, expiryDate, errors);
        }
        if (product == null || unitId == null || quantity == null || quantity.signum() <= 0 || unitPrice == null
                || unitPrice.signum() < 0) {
            return null;
        }
        List<String> serials = service ? List.of() : ExcelSupport.serials(ExcelSupport.text(row, SERIALS));
        return new Line(product.id(), product.code(), product.name(), service, unitId, quantity, unitPrice,
                discount, taxRateId != null ? taxRateId : product.defaultTaxRateId(), lotNo, manufactureDate,
                expiryDate, serials, ExcelSupport.nullable(row, NOTE));
    }

    private static ProductInfo product(String code, Lookups lookups, RowErrors errors) {
        if (code.isEmpty()) {
            errors.add("productId", "Mã hàng bắt buộc.");
            return null;
        }
        ProductInfo product = lookups.products().get(code);
        if (product == null || !product.active()) {
            errors.add("productId", "Hàng '" + code + "' không tồn tại hoặc đã ngừng hoạt động.");
            return null;
        }
        return product;
    }

    private Long unit(String code, ProductInfo product, Lookups lookups, LocalDate date, RowErrors errors) {
        if (code.isEmpty()) {
            return product == null ? null : product.baseUnitId();
        }
        UnitInfo unit = lookups.units().get(code);
        if (unit == null || !unit.active()) {
            errors.add("unitId", "Đơn vị tính '" + code + "' không tồn tại hoặc đã ngừng hoạt động.");
            return null;
        }
        if (product != null && !unit.id().equals(product.baseUnitId())) {
            try {
                masterData.conversionFactor(product.id(), unit.id(), date);
            } catch (BusinessException ex) {
                errors.add("unitId", ex.getMessage());
                return null;
            }
        }
        return unit.id();
    }

    private static Long taxRate(String code, Lookups lookups, LocalDate date, RowErrors errors) {
        if (code.isEmpty()) {
            return null;
        }
        TaxRateInfo rate = lookups.taxRates().get(code);
        if (rate == null || !rate.effectiveOn(date)) {
            errors.add("taxRateId", "Thuế suất '" + code + "' không tồn tại hoặc không có hiệu lực tại ngày đơn.");
            return null;
        }
        return rate.id();
    }

    private static void requireTracking(ProductInfo product, String lotNo, LocalDate expiryDate, RowErrors errors) {
        if (product.lotTracked() && lotNo == null) {
            errors.add("lotNo", "Hàng " + product.code() + " quản lý theo lô, phải nhập số lô.");
        }
        if (product.expiryTracked() && expiryDate == null) {
            errors.add("expiryDate", "Hàng " + product.code() + " quản lý hạn dùng, phải nhập hạn dùng.");
        }
    }
}
