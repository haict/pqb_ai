package com.company.erp.purchasing.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.CatalogLookupService;
import com.company.erp.masterdata.application.CatalogLookupService.CatalogItemInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.purchasing.application.dto.PurchaseLedgerResponse;
import com.company.erp.purchasing.application.dto.PurchaseLedgerResponse.Line;
import com.company.erp.purchasing.application.dto.PurchaseLedgerResponse.Totals;
import com.company.erp.purchasing.domain.PurchaseOrder;
import com.company.erp.purchasing.domain.PurchaseReturnType;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseLedgerJdbc;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseLedgerJdbc.Filter;
import com.company.erp.purchasing.infrastructure.persistence.PurchaseLedgerJdbc.Row;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.OrganizationQueryService.CompanyRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sổ chi tiết mua hàng (BR-RPT, H-06): dòng đơn mua, trả hàng, giảm giá hàng mua đã ghi sổ trong kỳ, kèm dòng tổng;
 * mỗi dòng truy ngược tới chứng từ (business-integrity mục 11). "NV mua hàng" là người lập chứng từ vì đơn mua chưa
 * có trường nhân viên mua hàng.
 */
@Service
public class PurchaseLedgerService {

    static final String VIEW = "purchase-ledger:view";
    /** Giới hạn số hàng / NCC chọn trong một lần xem (tham số đi trên URL của GET). */
    static final int MAX_SELECTED = 200;

    private final PurchaseLedgerJdbc ledger;
    private final PurchasingAccess access;
    private final CatalogLookupService catalogs;
    private final OrganizationQueryService organization;
    private final Clock clock;

    public PurchaseLedgerService(PurchaseLedgerJdbc ledger, PurchasingAccess access, CatalogLookupService catalogs,
                                 OrganizationQueryService organization, Clock clock) {
        this.ledger = ledger;
        this.access = access;
        this.catalogs = catalogs;
        this.organization = organization;
        this.clock = clock;
    }

    /** Điều kiện xem sổ; danh sách {@code null} hoặc rỗng: không lọc. */
    public record Criteria(Long companyId, Long branchId, LocalDate dateFrom, LocalDate dateTo, Long purchaserId,
                           Long supplierGroupId, Long productCategoryId, List<Long> productIds,
                           List<Long> supplierIds) {
    }

    @Transactional(readOnly = true)
    public PurchaseLedgerResponse ledger(CurrentUser actor, Criteria criteria) {
        validate(actor, criteria);
        List<Row> rows = ledger.lines(new Filter(criteria.companyId(), criteria.branchId(), criteria.dateFrom(),
                criteria.dateTo(), criteria.purchaserId(), criteria.productIds(), criteria.supplierIds()));
        Map<Long, ProductInfo> products = access.products(rows.stream().map(Row::productId).toList());
        Map<Long, PartnerInfo> suppliers = access.partners(rows.stream().map(Row::supplierId).toList());
        rows = filterGroups(criteria, rows, products, suppliers);
        Map<Long, String> units = catalogs.units(distinct(rows.stream().map(Row::unitId).toList())).stream()
                .collect(Collectors.toMap(CatalogItemInfo::id, CatalogItemInfo::name));
        List<Line> lines = rows.stream().map(row -> line(row, products.get(row.productId()), units.get(row.unitId())))
                .toList();
        CompanyRef company = organization.company(criteria.companyId()).orElse(null);
        return new PurchaseLedgerResponse(company == null ? null : company.name(),
                company == null ? null : company.address(), criteria.dateFrom(), criteria.dateTo(), lines,
                totals(lines), clock.instant());
    }

    /** Quyền, khoảng ngày, phạm vi chi nhánh (như sổ chi tiết bán hàng), điều kiện lọc thuộc công ty. */
    private void validate(CurrentUser actor, Criteria criteria) {
        access.requirePermission(actor, VIEW);
        if (criteria.dateFrom().isAfter(criteria.dateTo())) {
            throw BusinessException.invalidField("dateFrom", "Từ ngày phải trước đến ngày.");
        }
        if (criteria.branchId() == null) {
            if (!actor.dataScope().hasFullCompanyAccess(criteria.companyId())) {
                throw BusinessException.invalidField("branchId", "Chọn chi nhánh trong phạm vi của bạn.");
            }
        } else {
            BranchRef branch = access.usableBranch(actor, criteria.branchId());
            if (!branch.companyId().equals(criteria.companyId())) {
                throw BusinessException.notFound();
            }
        }
        requireLimit("productIds", criteria.productIds());
        requireLimit("supplierIds", criteria.supplierIds());
        if (criteria.purchaserId() != null && organization.user(criteria.purchaserId())
                .filter(user -> user.companyId().equals(criteria.companyId())).isEmpty()) {
            throw BusinessException.invalidField("purchaserId", "Người dùng không tồn tại.");
        }
        if (criteria.supplierGroupId() != null && catalogs.partnerGroup(criteria.supplierGroupId())
                .filter(group -> group.companyId().equals(criteria.companyId())).isEmpty()) {
            throw BusinessException.invalidField("supplierGroupId", "Nhóm nhà cung cấp không tồn tại.");
        }
    }

    private static void requireLimit(String field, List<Long> ids) {
        if (ids != null && ids.size() > MAX_SELECTED) {
            throw BusinessException.invalidField(field, "Chọn tối đa " + MAX_SELECTED + " mục mỗi lần xem sổ.");
        }
    }

    /** Lọc nhóm NCC, nhóm hàng (kể cả nhóm con) sau khi tra danh mục một lần cho cả danh sách. */
    private List<Row> filterGroups(Criteria criteria, List<Row> rows, Map<Long, ProductInfo> products,
                                   Map<Long, PartnerInfo> suppliers) {
        Set<Long> categoryIds = criteria.productCategoryId() == null ? null
                : catalogs.categoryWithDescendants(criteria.companyId(), criteria.productCategoryId());
        if (categoryIds != null && categoryIds.isEmpty()) {
            throw BusinessException.invalidField("productCategoryId", "Nhóm hàng không tồn tại.");
        }
        Long groupId = criteria.supplierGroupId();
        return rows.stream()
                .filter(row -> groupId == null || Optional.ofNullable(suppliers.get(row.supplierId()))
                        .map(PartnerInfo::partnerGroupId).filter(groupId::equals).isPresent())
                .filter(row -> categoryIds == null || Optional.ofNullable(products.get(row.productId()))
                        .map(ProductInfo::categoryId).filter(categoryIds::contains).isPresent())
                .toList();
    }

    /** Đơn mua vào cột mua, trả hàng vào cột trả lại, giảm giá vào cột giảm giá; dòng đảo đổi dấu. */
    private static Line line(Row row, ProductInfo product, String unitName) {
        BigDecimal sign = row.reversal() ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        boolean purchase = PurchaseOrder.TYPE_CODE.equals(row.documentType());
        boolean goodsReturn = PurchaseReturnType.THN.name().equals(row.documentType());
        boolean priceReduction = PurchaseReturnType.GGM.name().equals(row.documentType());
        return new Line(row.entryDate(), row.documentType(), row.documentId(), row.documentNo(), row.documentDate(),
                row.invoiceDate(), row.invoiceNo(), row.productId(), product == null ? null : product.code(),
                product == null ? null : product.name(), unitName,
                purchase ? signed(row.quantity(), sign) : null,
                purchase || goodsReturn ? row.unitPrice() : null,
                purchase ? signed(row.amount(), sign) : null,
                purchase ? signed(row.discount(), sign) : null,
                goodsReturn ? signed(row.quantity(), sign) : null,
                goodsReturn ? signed(row.amount(), sign) : null,
                priceReduction ? signed(row.amount(), sign) : null,
                row.reversal());
    }

    private static BigDecimal signed(BigDecimal value, BigDecimal sign) {
        return value == null ? null : value.multiply(sign);
    }

    private static Totals totals(List<Line> lines) {
        return new Totals(lines.size(), sum(lines, Line::purchaseQuantity), sum(lines, Line::purchaseAmount),
                sum(lines, Line::discountAmount), sum(lines, Line::returnQuantity), sum(lines, Line::returnAmount),
                sum(lines, Line::priceReductionAmount));
    }

    private static BigDecimal sum(List<Line> lines, Function<Line, BigDecimal> value) {
        return lines.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<Long> distinct(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
