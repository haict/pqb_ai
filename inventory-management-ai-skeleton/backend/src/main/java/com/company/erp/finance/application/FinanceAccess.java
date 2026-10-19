package com.company.erp.finance.application;

import com.company.erp.common.application.AuditEntry;
import com.company.erp.common.application.AuditLogService;
import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.application.DocumentNumberRequest;
import com.company.erp.common.application.DocumentNumberService;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ReferenceInfo;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra dùng chung của module công nợ, quỹ: quyền, phạm vi chi nhánh (ngoài phạm vi trả 404, BR-SYS-06), đối tượng
 * công nợ hợp lệ, cấp số chứng từ, audit.
 */
@Component
class FinanceAccess {

    static final String ENTITY_DEBT_OPENING = "DEBT_OPENING";
    static final String ENTITY_CASH_ACCOUNT = "CASH_ACCOUNT";
    static final String ENTITY_CASH_DOCUMENT = "CASH_DOCUMENT";
    private static final String COST_ITEM = "COST_ITEM";
    private static final Map<DebtType, String> ROLE_LABELS = Map.of(DebtType.RECEIVABLE, "khách hàng",
            DebtType.PAYABLE, "nhà cung cấp", DebtType.ADVANCE, "nhân viên");

    private final OrganizationQueryService organization;
    private final MasterDataQueryService masterData;
    private final DocumentNumberService numberService;
    private final AuditLogService auditLog;
    private final BusinessCalendar calendar;

    FinanceAccess(OrganizationQueryService organization, MasterDataQueryService masterData,
                  DocumentNumberService numberService, AuditLogService auditLog, BusinessCalendar calendar) {
        this.organization = organization;
        this.masterData = masterData;
        this.numberService = numberService;
        this.auditLog = auditLog;
        this.calendar = calendar;
    }

    /** Hôm nay theo lịch nghiệp vụ (ngày đảo mặc định). */
    LocalDate today() {
        return calendar.today();
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

    /**
     * Đối tượng công nợ: cùng công ty, đang hoạt động, có vai trò phù hợp (P-07): phải thu với khách hàng / bên mời
     * thầu / đại lý / nhân viên, phải trả với nhà cung cấp / nhân viên, tạm ứng với nhân viên.
     */
    PartnerInfo debtPartner(Long companyId, Long partnerId, DebtType debtType, String field) {
        PartnerInfo partner = masterData.partner(partnerId)
                .filter(found -> found.companyId().equals(companyId) && found.active())
                .orElseThrow(() -> BusinessException.invalidField(field, "Đối tượng không tồn tại hoặc đã ngừng."));
        String roleError = debtRoleError(partner, debtType);
        if (roleError != null) {
            throw BusinessException.invalidField(field, roleError);
        }
        return partner;
    }

    /** Thông báo lỗi khi đối tượng không có vai trò phù hợp loại công nợ (P-07); hợp lệ thì {@code null}. */
    static String debtRoleError(PartnerInfo partner, DebtType debtType) {
        Set<String> allowed = switch (debtType) {
            case RECEIVABLE -> Set.of("CUSTOMER", "TENDER_OWNER", "AGENT", "EMPLOYEE");
            case PAYABLE -> Set.of("SUPPLIER", "EMPLOYEE");
            case ADVANCE -> Set.of("EMPLOYEE");
        };
        return partner.roleCodes().stream().anyMatch(allowed::contains) ? null
                : "Đối tượng " + partner.code() + " không có vai trò " + ROLE_LABELS.get(debtType) + ".";
    }

    /** Đối tượng bất kỳ vai trò: cùng công ty, đang hoạt động. */
    PartnerInfo activePartner(Long companyId, Long partnerId, String field) {
        return masterData.partner(partnerId)
                .filter(found -> found.companyId().equals(companyId) && found.active())
                .orElseThrow(() -> BusinessException.invalidField(field, "Đối tượng không tồn tại hoặc đã ngừng."));
    }

    /** Khoản mục thu chi (danh mục phụ COST_ITEM): cùng công ty, đang hoạt động. */
    ReferenceInfo costItem(Long companyId, Long costItemId, String field) {
        return masterData.referenceItem(costItemId)
                .filter(found -> found.companyId().equals(companyId) && found.active()
                        && COST_ITEM.equals(found.categoryCode()))
                .orElseThrow(() -> BusinessException.invalidField(field, "Khoản mục không tồn tại hoặc đã ngừng."));
    }

    /** Tra đối tượng một lần cho cả danh sách (chống N+1). */
    Map<Long, PartnerInfo> partners(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        distinct.remove(null);
        return distinct.isEmpty() ? Map.of() : masterData.partners(distinct).stream()
                .collect(Collectors.toMap(PartnerInfo::id, Function.identity()));
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
