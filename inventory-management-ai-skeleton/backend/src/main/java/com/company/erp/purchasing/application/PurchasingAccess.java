package com.company.erp.purchasing.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.DocumentNumberRequest;
import com.company.erp.common.application.DocumentNumberService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.TaxRateInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.WarehouseInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra dùng chung của module mua hàng: quyền, phạm vi chi nhánh (ngoài phạm vi trả 404, BR-SYS-06), nhà cung cấp,
 * hàng, kho, thuế suất hợp lệ, cấp số chứng từ, audit.
 */
@Component
class PurchasingAccess {

    private static final String SUPPLIER = "SUPPLIER";
    private static final int QUANTITY_SCALE = 3;

    private final OrganizationQueryService organization;
    private final MasterDataQueryService masterData;
    private final DocumentNumberService numberService;
    private final AuditLogService auditLog;

    PurchasingAccess(OrganizationQueryService organization, MasterDataQueryService masterData,
                     DocumentNumberService numberService, AuditLogService auditLog) {
        this.organization = organization;
        this.masterData = masterData;
        this.numberService = numberService;
        this.auditLog = auditLog;
    }

    void requirePermission(CurrentUser actor, String permission) {
        if (!actor.hasPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    boolean canAccess(CurrentUser actor, Long companyId, Long branchId) {
        return actor.dataScope().canAccessCompany(companyId) && actor.dataScope().canAccessBranch(branchId);
    }

    /** Chi nhánh lập chứng từ: trong phạm vi và đang hoạt động. */
    BranchRef usableBranch(CurrentUser actor, Long branchId) {
        BranchRef branch = organization.branch(branchId)
                .filter(found -> canAccess(actor, found.companyId(), found.id()))
                .orElseThrow(() -> BusinessException.invalidField("branchId", "Chi nhánh không tồn tại hoặc ngoài phạm vi."));
        if (!branch.active()) {
            throw BusinessException.invalidField("branchId", "Chi nhánh " + branch.code() + " đã ngừng hoạt động.");
        }
        return branch;
    }

    /**
     * Chi nhánh được xem trong công ty để lọc danh sách; {@code null} nghĩa là toàn công ty. Không có chi nhánh nào thì
     * trả tập chỉ chứa -1 (không khớp dòng nào). Công ty ngoài phạm vi: 404.
     */
    Set<Long> branchScope(CurrentUser actor, Long companyId) {
        if (!actor.dataScope().canAccessCompany(companyId)) {
            throw BusinessException.notFound();
        }
        if (actor.dataScope().hasFullCompanyAccess(companyId)) {
            return null;
        }
        Set<Long> ids = new HashSet<>(actor.dataScope().branchIds());
        ids.add(-1L);
        return ids;
    }

    /** Nhà cung cấp: cùng công ty, đang hoạt động, có vai trò nhà cung cấp (BR-MD-34). */
    PartnerInfo supplier(Long companyId, Long partnerId, String field) {
        PartnerInfo partner = masterData.partner(partnerId)
                .filter(found -> found.companyId().equals(companyId) && found.active())
                .orElseThrow(() -> BusinessException.invalidField(field, "Nhà cung cấp không tồn tại hoặc đã ngừng."));
        if (!partner.roleCodes().contains(SUPPLIER)) {
            throw BusinessException.invalidField(field, "Đối tượng " + partner.code() + " không có vai trò nhà cung cấp.");
        }
        return partner;
    }

    /** Hàng hóa cùng công ty, đang hoạt động. */
    ProductInfo product(Long companyId, Long productId, String field) {
        return masterData.product(productId)
                .filter(found -> found.companyId().equals(companyId) && found.active())
                .orElseThrow(() -> BusinessException.invalidField(field, "Hàng hóa không tồn tại hoặc đã ngừng."));
    }

    /** Hệ số quy đổi đơn vị giao dịch về đơn vị cơ sở tại ngày chứng từ; lỗi gắn vào trường của dòng. */
    BigDecimal conversionFactor(Long productId, Long unitId, LocalDate date, String field) {
        try {
            return masterData.conversionFactor(productId, unitId, date);
        } catch (BusinessException ex) {
            throw BusinessException.invalidField(field, ex.getMessage());
        }
    }

    /** Số lượng quy đổi phải biểu diễn đúng ở 3 chữ số thập phân; không làm tròn ngầm (D-05). */
    static BigDecimal baseQuantity(BigDecimal quantity, BigDecimal factor, String field) {
        BigDecimal base = quantity.multiply(factor).stripTrailingZeros();
        if (base.scale() > QUANTITY_SCALE) {
            throw BusinessException.invalidField(field, "Số lượng quy đổi về đơn vị cơ sở vượt quá 3 chữ số thập phân.");
        }
        return base.setScale(QUANTITY_SCALE);
    }

    /** Chuỗi bỏ khoảng trắng hai đầu; rỗng thành {@code null}. */
    static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Kho nhận hàng: kho vật lý đang hoạt động, thuộc chi nhánh của chứng từ. */
    WarehouseInfo receivingWarehouse(Long branchId, Long warehouseId, String field) {
        WarehouseInfo warehouse = masterData.warehouse(warehouseId)
                .filter(found -> found.branchId().equals(branchId))
                .orElseThrow(() -> BusinessException.invalidField(field, "Kho không tồn tại hoặc không thuộc chi nhánh."));
        if (!warehouse.active() || !warehouse.physical()) {
            throw BusinessException.invalidField(field, "Kho " + warehouse.code()
                    + " đã ngừng hoạt động hoặc không phải kho vật lý.");
        }
        return warehouse;
    }

    /** Thuế suất cùng công ty, có hiệu lực tại ngày chứng từ. */
    TaxRateInfo taxRate(Long companyId, Long taxRateId, LocalDate date, String field) {
        TaxRateInfo rate = masterData.taxRate(taxRateId).filter(found -> found.companyId().equals(companyId))
                .orElseThrow(() -> BusinessException.invalidField(field, "Thuế suất không tồn tại."));
        if (!rate.effectiveOn(date)) {
            throw BusinessException.invalidField(field, "Thuế suất " + rate.code() + " không có hiệu lực tại ngày "
                    + date + ".");
        }
        return rate;
    }

    /** Tra một lần cho cả danh sách (chống N+1); map nhận khóa {@code null} (trả {@code null}). */
    Map<Long, PartnerInfo> partners(Collection<Long> ids) {
        Set<Long> distinct = distinct(ids);
        return distinct.isEmpty() ? new HashMap<>() : masterData.partners(distinct).stream()
                .collect(Collectors.toMap(PartnerInfo::id, Function.identity()));
    }

    Map<Long, ProductInfo> products(Collection<Long> ids) {
        Set<Long> distinct = distinct(ids);
        return distinct.isEmpty() ? new HashMap<>() : masterData.products(distinct).stream()
                .collect(Collectors.toMap(ProductInfo::id, Function.identity()));
    }

    Map<Long, WarehouseInfo> warehouses(Collection<Long> ids) {
        Set<Long> distinct = distinct(ids);
        return distinct.isEmpty() ? new HashMap<>() : masterData.warehouses(distinct).stream()
                .collect(Collectors.toMap(WarehouseInfo::id, Function.identity()));
    }

    private static Set<Long> distinct(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        distinct.remove(null);
        return distinct;
    }

    String nextNumber(Long companyId, Long branchId, String documentType, LocalDate documentDate) {
        String branchCode = organization.branch(branchId).map(BranchRef::code)
                .orElseThrow(() -> new IllegalStateException("Thiếu chi nhánh #" + branchId));
        return numberService.nextNumber(new DocumentNumberRequest(companyId, branchId, branchCode, documentType,
                documentDate));
    }

    void audit(CurrentUser actor, String action, String entityType, Long entityId, String documentNo, String reason,
               Object before, Object after, Long companyId, Long branchId) {
        auditLog.record(new AuditEntry(actor.id(), action, entityType, entityId, documentNo, reason, before, after,
                companyId, branchId));
    }
}
