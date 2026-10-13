package com.company.erp.masterdata.application;

import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.application.excel.ExcelSupport;
import com.company.erp.common.application.excel.RowErrors;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.dto.ImportResultResponse;
import com.company.erp.masterdata.domain.CodeNameEntity;
import com.company.erp.masterdata.domain.CustomerType;
import com.company.erp.masterdata.domain.Partner;
import com.company.erp.masterdata.domain.PartnerDetails;
import com.company.erp.masterdata.domain.PartnerKind;
import com.company.erp.masterdata.domain.PartnerRole;
import com.company.erp.masterdata.domain.Product;
import com.company.erp.masterdata.domain.ProductDetails;
import com.company.erp.masterdata.domain.ProductType;
import com.company.erp.masterdata.infrastructure.persistence.BrandRepository;
import com.company.erp.masterdata.infrastructure.persistence.CodeNameRepository;
import com.company.erp.masterdata.infrastructure.persistence.CountryRepository;
import com.company.erp.masterdata.infrastructure.persistence.ManufacturerRepository;
import com.company.erp.masterdata.infrastructure.persistence.PartnerRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductCategoryRepository;
import com.company.erp.masterdata.infrastructure.persistence.ProductRepository;
import com.company.erp.masterdata.infrastructure.persistence.UnitRepository;
import com.company.erp.system.application.CurrentUser;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nhập hàng hóa, đối tác từ Excel (BR-UX-05, BR-MIG-01). Kiểm tra mọi dòng trước; có lỗi thì báo theo dòng và không
 * ghi dòng nào (tất cả hoặc không). Gửi lại cùng file với cùng Idempotency-Key không nhập lần hai.
 */
@Service
public class MasterDataImportService {

    private static final Pattern PRODUCT_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,50}");
    private static final Pattern TAX_CODE = Pattern.compile("[0-9]{10}(-[0-9]{3})?");
    private static final Map<String, PartnerRole> ROLE_CODES = Map.of(
            "KH", PartnerRole.CUSTOMER, "NCC", PartnerRole.SUPPLIER, "DL", PartnerRole.AGENT,
            "BMT", PartnerRole.TENDER_OWNER, "NV", PartnerRole.EMPLOYEE, "KHAC", PartnerRole.OTHER);

    static final List<String> PRODUCT_HEADERS = List.of("Mã hàng*", "Mã cũ (MISA)", "Tên hàng*", "Tên thương mại",
            "Mã nhóm hàng*", "Loại hàng*", "Mã ĐVT cơ sở*", "Mã nhãn hiệu", "Mã nhà sản xuất", "Xuất xứ (ISO)",
            "Model", "Quy cách", "Theo lô (C/K)", "Theo hạn dùng (C/K)", "Theo serial (C/K)", "Phân loại TTBYT",
            "Số lưu hành", "Hiệu lực số lưu hành (dd/mm/yyyy)", "Bảo hành (tháng)");
    static final List<String> PARTNER_HEADERS = List.of("Mã đối tác*", "Mã cũ (MISA)", "Tên*", "Tên viết tắt",
            "Vai trò* (KH,NCC,DL,BMT,NV,KHAC)", "Loại (TC/CN)", "Mã số thuế", "Địa chỉ", "Điện thoại", "Email",
            "Loại khách", "Số ngày nợ", "Hạn mức tín dụng", "Số ngày thanh toán NCC");

    private final ProductRepository productRepository;
    private final PartnerRepository partnerRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final BrandRepository brandRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final CountryRepository countryRepository;
    private final MasterDataGuard guard;
    private final IdempotencyService idempotency;

    public MasterDataImportService(ProductRepository productRepository, PartnerRepository partnerRepository,
                                   ProductCategoryRepository categoryRepository, UnitRepository unitRepository,
                                   BrandRepository brandRepository, ManufacturerRepository manufacturerRepository,
                                   CountryRepository countryRepository, MasterDataGuard guard,
                                   IdempotencyService idempotency) {
        this.productRepository = productRepository;
        this.partnerRepository = partnerRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.brandRepository = brandRepository;
        this.manufacturerRepository = manufacturerRepository;
        this.countryRepository = countryRepository;
        this.guard = guard;
        this.idempotency = idempotency;
    }

    public byte[] productTemplate() {
        List<List<String>> guide = new ArrayList<>();
        guide.add(List.of("Cột", "Giá trị hợp lệ"));
        guide.add(List.of("Loại hàng", "MACHINE (máy móc), CONSUMABLE (vật tư tiêu hao), REAGENT (thuốc thử - hóa chất), "
                + "SPARE_PART (linh kiện), TOOL (dụng cụ), SERVICE (dịch vụ), OTHER (khác)"));
        guide.add(List.of("Theo lô / hạn dùng / serial", "C = có, K hoặc để trống = không. Theo hạn dùng phải theo lô."));
        guide.add(List.of("Phân loại TTBYT", "A, B, C, D hoặc để trống"));
        guide.add(List.of("Mã nhóm, ĐVT, nhãn hiệu, NSX", "Mã đã khai báo trong danh mục, đang hoạt động"));
        guide.add(List.of("Số dòng tối đa", String.valueOf(ExcelSupport.MAX_ROWS)));
        return ExcelSupport.toBytes(ExcelSupport.template("Hang hoa", PRODUCT_HEADERS, guide));
    }

    public byte[] partnerTemplate() {
        List<List<String>> guide = new ArrayList<>();
        guide.add(List.of("Cột", "Giá trị hợp lệ"));
        guide.add(List.of("Vai trò", "Một hoặc nhiều, phân tách dấu phẩy: KH (khách hàng), NCC (nhà cung cấp), "
                + "DL (đại lý), BMT (bên mời thầu), NV (nhân viên), KHAC (khác)"));
        guide.add(List.of("Loại", "TC (tổ chức, mặc định), CN (cá nhân)"));
        guide.add(List.of("Loại khách", "PUBLIC_HOSPITAL, MEDICAL_CENTER, PRIVATE_CLINIC, PHARMACY, ENTERPRISE, AGENT, "
                + "INDIVIDUAL hoặc để trống"));
        guide.add(List.of("Mã số thuế", "10 số hoặc 10 số-3 số"));
        guide.add(List.of("Số dòng tối đa", String.valueOf(ExcelSupport.MAX_ROWS)));
        return ExcelSupport.toBytes(ExcelSupport.template("Doi tac", PARTNER_HEADERS, guide));
    }

    @Transactional
    public ImportResultResponse importProducts(CurrentUser actor, Long companyId, String idempotencyKey,
                                               MultipartFile file) {
        guard.requireCompany(actor, companyId);
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/products/import",
                Map.of("companyId", companyId, "sha256", ExcelSupport.sha256(file)));
        return idempotency.execute(command, ImportResultResponse.class, () -> {
            List<Product> products = parseProducts(companyId, file);
            productRepository.saveAllAndFlush(products);
            ImportResultResponse result = new ImportResultResponse(products.size());
            guard.audit(actor, MasterDataAudit.IMPORT, MasterDataAudit.PRODUCT, null, file.getOriginalFilename(),
                    null, result, companyId);
            return result;
        });
    }

    @Transactional
    public ImportResultResponse importPartners(CurrentUser actor, Long companyId, String idempotencyKey,
                                               MultipartFile file) {
        guard.requireCompany(actor, companyId);
        IdempotentCommand command = new IdempotentCommand(actor.id(), idempotencyKey, "POST /api/v1/partners/import",
                Map.of("companyId", companyId, "sha256", ExcelSupport.sha256(file)));
        return idempotency.execute(command, ImportResultResponse.class, () -> {
            List<Partner> partners = parsePartners(companyId, file);
            partnerRepository.saveAllAndFlush(partners);
            ImportResultResponse result = new ImportResultResponse(partners.size());
            guard.audit(actor, MasterDataAudit.IMPORT, MasterDataAudit.PARTNER, null, file.getOriginalFilename(),
                    null, result, companyId);
            return result;
        });
    }

    private List<Product> parseProducts(Long companyId, MultipartFile file) {
        RowErrors errors = new RowErrors();
        List<Product> products = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();
        try (Workbook workbook = ExcelSupport.open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            ExcelSupport.requireRowCount(sheet);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (ExcelSupport.isBlankRow(row, PRODUCT_HEADERS.size())) {
                    continue;
                }
                errors.row(index + 1);
                Product product = parseProduct(companyId, row, errors, codesInFile);
                if (product != null) {
                    products.add(product);
                }
            }
        } catch (IOException ex) {
            throw BusinessException.invalidField("file", "Không đọc được file Excel (.xlsx).");
        }
        ExcelSupport.requireNotEmpty(products.isEmpty() && codesInFile.isEmpty());
        errors.throwIfAny();
        return products;
    }

    private Product parseProduct(Long companyId, Row row, RowErrors errors, Set<String> codesInFile) {
        String code = ExcelSupport.text(row, 0);
        if (!PRODUCT_CODE.matcher(code).matches()) {
            errors.add("code", "Mã hàng bắt buộc, 1-50 ký tự chữ, số, _ . -");
        } else if (!codesInFile.add(code)) {
            errors.add("code", "Mã hàng '" + code + "' trùng trong file.");
        } else if (productRepository.existsByCompanyIdAndCode(companyId, code)) {
            errors.add("code", "Mã hàng '" + code + "' đã tồn tại.");
        }
        String name = ExcelSupport.text(row, 2);
        if (name.isEmpty()) {
            errors.add("name", "Tên hàng bắt buộc.");
        }
        Long categoryId = reference(categoryRepository, companyId, ExcelSupport.text(row, 4), true, errors, "categoryId",
                "Nhóm hàng");
        ProductType type = enumValue(ProductType.class, ExcelSupport.text(row, 5), true, errors, "productType",
                "Loại hàng");
        Long unitId = reference(unitRepository, companyId, ExcelSupport.text(row, 6), true, errors, "baseUnitId",
                "Đơn vị tính");
        Long brandId = reference(brandRepository, companyId, ExcelSupport.text(row, 7), false, errors, "brandId",
                "Nhãn hiệu");
        Long manufacturerId = reference(manufacturerRepository, companyId, ExcelSupport.text(row, 8), false, errors,
                "manufacturerId", "Nhà sản xuất");
        String country = ExcelSupport.nullable(row, 9);
        if (country != null && countryRepository.findById(country).isEmpty()) {
            errors.add("originCountryCode", "Xuất xứ '" + country + "' không có trong danh mục quốc gia.");
        }
        boolean lot = ExcelSupport.flag(row, 12, errors, "lotTracked");
        boolean expiry = ExcelSupport.flag(row, 13, errors, "expiryTracked");
        boolean serial = ExcelSupport.flag(row, 14, errors, "serialTracked");
        ProductDetails details = new ProductDetails(ExcelSupport.nullable(row, 1), name,
                null, categoryId, brandId, manufacturerId, country, ExcelSupport.nullable(row, 10),
                ExcelSupport.nullable(row, 11), null, ExcelSupport.nullable(row, 15), ExcelSupport.nullable(row, 16),
                null, ExcelSupport.date(row, 17, errors, "registrationValidTo"), null, null, false, false,
                ExcelSupport.integer(row, 18, errors, "warrantyMonths"), null, false, false, null, null);
        if (name.isEmpty() || categoryId == null || type == null || unitId == null) {
            return null;
        }
        try {
            return new Product(companyId, code, type, unitId, lot, expiry, serial, details);
        } catch (BusinessException ex) {
            errors.add(ex.violations().isEmpty() ? "row" : ex.violations().get(0).field(), ex.getMessage());
            return null;
        }
    }

    private List<Partner> parsePartners(Long companyId, MultipartFile file) {
        RowErrors errors = new RowErrors();
        List<Partner> partners = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();
        try (Workbook workbook = ExcelSupport.open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            ExcelSupport.requireRowCount(sheet);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (ExcelSupport.isBlankRow(row, PARTNER_HEADERS.size())) {
                    continue;
                }
                errors.row(index + 1);
                Partner partner = parsePartner(companyId, row, errors, codesInFile);
                if (partner != null) {
                    partners.add(partner);
                }
            }
        } catch (IOException ex) {
            throw BusinessException.invalidField("file", "Không đọc được file Excel (.xlsx).");
        }
        ExcelSupport.requireNotEmpty(partners.isEmpty() && codesInFile.isEmpty());
        errors.throwIfAny();
        return partners;
    }

    private Partner parsePartner(Long companyId, Row row, RowErrors errors, Set<String> codesInFile) {
        String code = ExcelSupport.text(row, 0);
        if (!PRODUCT_CODE.matcher(code).matches()) {
            errors.add("code", "Mã đối tác bắt buộc, 1-50 ký tự chữ, số, _ . -");
        } else if (!codesInFile.add(code)) {
            errors.add("code", "Mã đối tác '" + code + "' trùng trong file.");
        } else if (partnerRepository.existsByCompanyIdAndCode(companyId, code)) {
            errors.add("code", "Mã đối tác '" + code + "' đã tồn tại.");
        }
        String name = ExcelSupport.text(row, 2);
        if (name.isEmpty()) {
            errors.add("name", "Tên đối tác bắt buộc.");
        }
        Set<PartnerRole> roles = roles(ExcelSupport.text(row, 4), errors);
        String kindText = ExcelSupport.text(row, 5).toUpperCase(Locale.ROOT);
        PartnerKind kind = kindText.equals("CN") ? PartnerKind.INDIVIDUAL : PartnerKind.ORGANIZATION;
        if (!kindText.isEmpty() && !kindText.equals("CN") && !kindText.equals("TC")) {
            errors.add("partnerKind", "Loại đối tác chỉ gồm TC hoặc CN.");
        }
        String taxCode = ExcelSupport.nullable(row, 6);
        if (taxCode != null && !TAX_CODE.matcher(taxCode).matches()) {
            errors.add("taxCode", "Mã số thuế gồm 10 số hoặc 10 số-3 số.");
        }
        boolean supplierMissingIdentity = roles.contains(PartnerRole.SUPPLIER)
                && (taxCode == null || ExcelSupport.nullable(row, 7) == null);
        if (supplierMissingIdentity) {
            errors.add(taxCode == null ? "taxCode" : "address", "Nhà cung cấp phải có mã số thuế và địa chỉ.");
        }
        CustomerType customerType = enumValue(CustomerType.class, ExcelSupport.text(row, 10), false, errors,
                "customerType", "Loại khách");
        Integer paymentTermDays = ExcelSupport.integer(row, 11, errors, "paymentTermDays");
        BigDecimal creditLimit = ExcelSupport.decimal(row, 12, errors, "creditLimit");
        Integer supplierTermDays = ExcelSupport.integer(row, 13, errors, "supplierPaymentTermDays");
        if (name.isEmpty() || roles.isEmpty() || supplierMissingIdentity) {
            return null;
        }
        PartnerDetails details = PartnerService.details(ExcelSupport.nullable(row, 1), name,
                ExcelSupport.nullable(row, 3), kind, taxCode, ExcelSupport.nullable(row, 7),
                ExcelSupport.nullable(row, 8), ExcelSupport.nullable(row, 9), null, null, roles, customerType, null,
                null, paymentTermDays, creditLimit, null, null, supplierTermDays, null, null, null, null, null);
        return new Partner(companyId, code, details);
    }

    private static Set<PartnerRole> roles(String text, RowErrors errors) {
        Set<PartnerRole> roles = EnumSet.noneOf(PartnerRole.class);
        for (String part : text.split(",")) {
            String key = part.trim().toUpperCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            PartnerRole role = ROLE_CODES.get(key);
            if (role == null) {
                errors.add("roles", "Vai trò '" + key + "' không hợp lệ.");
            } else {
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            errors.add("roles", "Vai trò bắt buộc.");
        }
        return roles;
    }

    private static <E extends CodeNameEntity> Long reference(CodeNameRepository<E> repository, Long companyId,
                                                             String code, boolean required, RowErrors errors,
                                                             String field, String label) {
        if (code.isEmpty()) {
            if (required) {
                errors.add(field, label + " bắt buộc.");
            }
            return null;
        }
        return repository.findByCompanyIdAndCode(companyId, code)
                .filter(CodeNameEntity::isActive)
                .map(CodeNameEntity::getId)
                .orElseGet(() -> {
                    errors.add(field, label + " mã '" + code + "' không tồn tại hoặc đã ngừng hoạt động.");
                    return null;
                });
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String text, boolean required, RowErrors errors,
                                                   String field, String label) {
        if (text.isEmpty()) {
            if (required) {
                errors.add(field, label + " bắt buộc.");
            }
            return null;
        }
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add(field, label + " '" + text + "' không hợp lệ.");
            return null;
        }
    }
}
