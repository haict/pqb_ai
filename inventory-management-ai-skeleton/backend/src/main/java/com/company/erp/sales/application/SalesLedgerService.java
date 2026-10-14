package com.company.erp.sales.application;

import com.company.erp.accounting.application.JournalQueryService;
import com.company.erp.accounting.application.JournalQueryService.SourceAccounts;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.CatalogLookupService;
import com.company.erp.masterdata.application.CatalogLookupService.CatalogItemInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.SalesLedgerResponse;
import com.company.erp.sales.application.dto.SalesLedgerResponse.Group;
import com.company.erp.sales.application.dto.SalesLedgerResponse.Line;
import com.company.erp.sales.application.dto.SalesLedgerResponse.ProductBlock;
import com.company.erp.sales.application.dto.SalesLedgerResponse.Totals;
import com.company.erp.sales.domain.SalesLedgerGrouping;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.domain.SalesReturnType;
import com.company.erp.sales.infrastructure.persistence.SalesLedgerJdbc;
import com.company.erp.sales.infrastructure.persistence.SalesLedgerJdbc.Filter;
import com.company.erp.sales.infrastructure.persistence.SalesLedgerJdbc.Row;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.OrganizationQueryService.CompanyRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Sổ chi tiết bán hàng (mẫu S16-DNN theo TT133 hiện trạng H-04, BR-RPT-26): từng hàng hóa, các dòng đơn bán / trả lại /
 * giảm giá đã ghi sổ trong kỳ, TK đối ứng từ bút toán gốc, cộng phát sinh, doanh thu thuần. Mỗi dòng truy ngược tới
 * chứng từ (business-integrity mục 11). Giá vốn, lãi gộp: chưa có nguồn (giá vốn ghi sổ ở Giai đoạn 8).
 */
@Service
public class SalesLedgerService {

    static final String VIEW = "sales-ledger:view";
    /** Giới hạn số hàng / khách chọn trong một lần xem (tham số đi trên URL của GET). */
    static final int MAX_SELECTED = 200;
    private static final int UNIT_PRICE_SCALE = 4;

    private final SalesLedgerJdbc ledger;
    private final SalesAccess access;
    private final CatalogLookupService catalogs;
    private final JournalQueryService journals;
    private final OrganizationQueryService organization;
    private final Clock clock;

    public SalesLedgerService(SalesLedgerJdbc ledger, SalesAccess access, CatalogLookupService catalogs,
                              JournalQueryService journals, OrganizationQueryService organization, Clock clock) {
        this.ledger = ledger;
        this.access = access;
        this.catalogs = catalogs;
        this.journals = journals;
        this.organization = organization;
        this.clock = clock;
    }

    /** Điều kiện xem sổ; danh sách {@code null} hoặc rỗng: không lọc. */
    public record Criteria(Long companyId, Long branchId, LocalDate dateFrom, LocalDate dateTo, Long salesUserId,
                           Long customerGroupId, Long productCategoryId, List<Long> productIds,
                           List<Long> customerIds, SalesLedgerGrouping groupBy) {
    }

    @Transactional(readOnly = true)
    public SalesLedgerResponse ledger(CurrentUser actor, Criteria criteria) {
        validate(actor, criteria);
        Long owner = actor.dataScope().ownDataOnly() ? actor.id() : null;
        List<Row> rows = ledger.lines(new Filter(criteria.companyId(), criteria.branchId(), criteria.dateFrom(),
                criteria.dateTo(), criteria.salesUserId(), owner, criteria.productIds(), criteria.customerIds()));
        Map<Long, ProductInfo> products = access.products(rows.stream().map(Row::productId).toList());
        Map<Long, PartnerInfo> customers = access.partners(rows.stream().map(Row::customerId).toList());
        rows = filterGroups(criteria, rows, products, customers);

        Map<Long, String> offsets = offsetAccounts(criteria.companyId(), rows);
        Map<Long, String> units = names(catalogs.units(distinct(rows.stream().map(Row::unitId).toList())));
        SalesLedgerGrouping grouping = criteria.groupBy() == null ? SalesLedgerGrouping.NONE : criteria.groupBy();
        Map<Long, CatalogItemInfo> categories = grouping == SalesLedgerGrouping.PRODUCT_CATEGORY
                ? catalogs.productCategories(distinct(products.values().stream().map(ProductInfo::categoryId).toList()))
                .stream().collect(Collectors.toMap(CatalogItemInfo::id, Function.identity()))
                : Map.of();

        // Khóa nhóm có thể null (không nhóm, hàng chưa phân nhóm) nên bọc Optional: groupingBy không nhận khóa null.
        Map<Optional<Long>, List<Row>> byGroup = rows.stream().collect(Collectors.groupingBy(
                row -> Optional.ofNullable(groupKey(grouping, row, products)), LinkedHashMap::new,
                Collectors.toList()));
        List<Group> groups = byGroup.entrySet().stream()
                .map(entry -> group(grouping, entry.getKey().orElse(null), entry.getValue(), products, customers,
                        categories, offsets, units))
                .sorted(Comparator.comparing(Group::code, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        CompanyRef company = organization.company(criteria.companyId()).orElse(null);
        return new SalesLedgerResponse(company == null ? null : company.name(),
                company == null ? null : company.address(), criteria.dateFrom(), criteria.dateTo(), grouping.name(),
                groups, totals(groups.stream().map(Group::totals).toList()), false, clock.instant());
    }

    /** Quyền, khoảng ngày, phạm vi chi nhánh (như báo cáo doanh số), điều kiện lọc thuộc công ty. */
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
        requireLimit("customerIds", criteria.customerIds());
        if (criteria.salesUserId() != null) {
            access.user(criteria.companyId(), criteria.salesUserId(), "salesUserId");
        }
        if (criteria.customerGroupId() != null && catalogs.partnerGroup(criteria.customerGroupId())
                .filter(group -> group.companyId().equals(criteria.companyId())).isEmpty()) {
            throw BusinessException.invalidField("customerGroupId", "Nhóm khách hàng không tồn tại.");
        }
    }

    private static void requireLimit(String field, List<Long> ids) {
        if (ids != null && ids.size() > MAX_SELECTED) {
            throw BusinessException.invalidField(field, "Chọn tối đa " + MAX_SELECTED + " mục mỗi lần xem sổ.");
        }
    }

    /** Lọc nhóm khách, nhóm hàng (kể cả nhóm con) sau khi tra danh mục một lần cho cả danh sách. */
    private List<Row> filterGroups(Criteria criteria, List<Row> rows, Map<Long, ProductInfo> products,
                                   Map<Long, PartnerInfo> customers) {
        Set<Long> categoryIds = criteria.productCategoryId() == null ? null
                : catalogs.categoryWithDescendants(criteria.companyId(), criteria.productCategoryId());
        if (categoryIds != null && categoryIds.isEmpty()) {
            throw BusinessException.invalidField("productCategoryId", "Nhóm hàng không tồn tại.");
        }
        Long groupId = criteria.customerGroupId();
        return rows.stream()
                .filter(row -> groupId == null || Optional.ofNullable(customers.get(row.customerId()))
                        .map(PartnerInfo::partnerGroupId).filter(groupId::equals).isPresent())
                .filter(row -> categoryIds == null || Optional.ofNullable(products.get(row.productId()))
                        .map(ProductInfo::categoryId).filter(categoryIds::contains).isPresent())
                .toList();
    }

    /**
     * TK đối ứng của dòng: đơn bán lấy tài khoản Nợ (phải thu), trả lại / giảm giá lấy tài khoản Có của bút toán gốc
     * (bên đối ứng với doanh thu). Dòng đảo dùng tài khoản của chứng từ gốc. Khóa: loại chứng từ + ID dòng.
     */
    private Map<Long, String> offsetAccounts(Long companyId, List<Row> rows) {
        Map<Long, String> result = new HashMap<>();
        Map<String, List<Row>> byType = rows.stream().collect(Collectors.groupingBy(Row::documentType));
        byType.forEach((type, typeRows) -> {
            Map<Long, SourceAccounts> accounts = journals.accountsBySourceLines(companyId, type,
                    distinct(typeRows.stream().map(Row::documentId).toList()));
            boolean debitSide = SalesOrder.TYPE_CODE.equals(type);
            typeRows.forEach(row -> {
                SourceAccounts found = accounts.get(row.lineId());
                if (found != null) {
                    result.put(lineKey(row), String.join(", ",
                            debitSide ? found.debitCodes() : found.creditCodes()));
                }
            });
        });
        return result;
    }

    /** ID dòng của đơn bán và của trả lại / giảm giá thuộc hai bảng khác nhau nên khóa kèm dấu loại chứng từ. */
    private static Long lineKey(Row row) {
        return SalesOrder.TYPE_CODE.equals(row.documentType()) ? row.lineId() : -row.lineId();
    }

    private static Long groupKey(SalesLedgerGrouping grouping, Row row, Map<Long, ProductInfo> products) {
        return switch (grouping) {
            case NONE -> null;
            case CUSTOMER -> row.customerId();
            case PRODUCT_CATEGORY -> {
                ProductInfo product = products.get(row.productId());
                yield product == null ? null : product.categoryId();
            }
        };
    }

    private Group group(SalesLedgerGrouping grouping, Long key, List<Row> rows, Map<Long, ProductInfo> products,
                        Map<Long, PartnerInfo> customers, Map<Long, CatalogItemInfo> categories,
                        Map<Long, String> offsets, Map<Long, String> units) {
        Map<Long, List<Row>> byProduct = rows.stream().collect(Collectors.groupingBy(Row::productId,
                LinkedHashMap::new, Collectors.toList()));
        List<ProductBlock> blocks = byProduct.entrySet().stream().map(entry -> {
            ProductInfo product = products.get(entry.getKey());
            List<Line> lines = entry.getValue().stream()
                    .map(row -> line(row, customers.get(row.customerId()), offsets.get(lineKey(row)),
                            units.get(row.unitId())))
                    .toList();
            return new ProductBlock(entry.getKey(), product == null ? null : product.code(),
                    product == null ? null : product.name(), lines, lineTotals(lines));
        }).sorted(Comparator.comparing(ProductBlock::productCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Totals totals = totals(blocks.stream().map(ProductBlock::totals).toList());
        return switch (grouping) {
            case NONE -> new Group(null, null, null, blocks, totals);
            case CUSTOMER -> {
                PartnerInfo customer = customers.get(key);
                yield new Group(key, customer == null ? null : customer.code(),
                        customer == null ? null : customer.name(), blocks, totals);
            }
            case PRODUCT_CATEGORY -> {
                CatalogItemInfo category = key == null ? null : categories.get(key);
                yield new Group(key, category == null ? null : category.code(),
                        category == null ? "Chưa phân nhóm" : category.name(), blocks, totals);
            }
        };
    }

    /**
     * Dòng sổ: đơn bán vào cột doanh thu (đơn giá = thành tiền sau chiết khấu / số lượng), trả lại / giảm giá vào cột
     * giảm trừ "Khác"; dòng đảo đổi dấu. Cột giảm trừ "Thuế" (TTĐB, xuất khẩu...) chưa có nghiệp vụ nên để trống.
     */
    private static Line line(Row row, PartnerInfo customer, String offsetAccount, String unitName) {
        BigDecimal sign = row.reversal() ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        boolean sale = SalesOrder.TYPE_CODE.equals(row.documentType());
        BigDecimal unitPrice = sale && row.quantity() != null && row.quantity().signum() != 0
                ? row.amount().divide(row.quantity(), UNIT_PRICE_SCALE, RoundingMode.HALF_UP) : null;
        return new Line(row.entryDate(), row.documentType(), row.documentId(), row.documentNo(), row.documentDate(),
                row.invoiceDate(), row.invoiceNo(), description(row, customer), offsetAccount, unitName,
                sale && row.quantity() != null ? row.quantity().multiply(sign) : null, unitPrice,
                sale ? row.amount().multiply(sign) : null, null,
                sale ? null : row.amount().multiply(sign), row.reversal());
    }

    private static String description(Row row, PartnerInfo customer) {
        String text = row.description();
        if (text == null || text.isBlank()) {
            String customerName = customer == null ? "" : " " + customer.name();
            if (SalesOrder.TYPE_CODE.equals(row.documentType())) {
                text = "Bán hàng" + customerName + (row.invoiceNo() == null ? "" : " theo hóa đơn " + row.invoiceNo());
            } else if (SalesReturnType.TLB.name().equals(row.documentType())) {
                text = "Hàng bán bị trả lại" + customerName;
            } else {
                text = "Giảm giá hàng bán" + customerName;
            }
        }
        return row.reversal() ? "Đảo " + row.documentNo() + ": " + text : text;
    }

    private static Totals lineTotals(List<Line> lines) {
        BigDecimal amount = sum(lines.stream().map(Line::amount).toList());
        BigDecimal tax = sum(lines.stream().map(Line::taxDeduction).toList());
        BigDecimal other = sum(lines.stream().map(Line::otherDeduction).toList());
        return new Totals(sum(lines.stream().map(Line::quantity).toList()), amount, tax, other,
                amount.subtract(tax).subtract(other), null, null);
    }

    private static Totals totals(List<Totals> parts) {
        BigDecimal amount = sum(parts.stream().map(Totals::amount).toList());
        BigDecimal tax = sum(parts.stream().map(Totals::taxDeduction).toList());
        BigDecimal other = sum(parts.stream().map(Totals::otherDeduction).toList());
        return new Totals(sum(parts.stream().map(Totals::quantity).toList()), amount, tax, other,
                amount.subtract(tax).subtract(other), null, null);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<Long, String> names(List<CatalogItemInfo> items) {
        return items.stream().collect(Collectors.toMap(CatalogItemInfo::id, CatalogItemInfo::name));
    }

    private static List<Long> distinct(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
