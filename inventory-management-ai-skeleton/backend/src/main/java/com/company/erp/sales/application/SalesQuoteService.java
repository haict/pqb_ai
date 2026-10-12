package com.company.erp.sales.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.CommentRequest;
import com.company.erp.sales.application.dto.PriceSuggestionResponse;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.SalesLineRequest;
import com.company.erp.sales.application.dto.SalesQuoteRequest;
import com.company.erp.sales.application.dto.SalesQuoteResponse;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.sales.domain.SalesQuote;
import com.company.erp.sales.domain.SalesQuoteLine;
import com.company.erp.sales.infrastructure.persistence.SalesQuoteRepository;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import com.company.erp.system.application.OrganizationQueryService.UserRef;
import com.company.erp.system.application.ParameterService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo giá (BG, BR-SAL-01..05): lập theo giá ưu tiên (BR-SAL-02), gửi duyệt (giá dưới sàn / chiết khấu vượt ngưỡng phải
 * duyệt, BR-SAL-03), gửi khách, ghi nhận khách chấp nhận / từ chối, hết hạn, phiên bản mới. Chuyển thành đơn bán ở
 * {@link SalesOrderService#createFromQuote}.
 */
@Service
public class SalesQuoteService {

    static final String PATH = "/api/v1/sales-quotes";
    static final String VIEW = "sales-quote:view";
    static final String CREATE = "sales-quote:create";
    static final String APPROVE = "sales-quote:approve";
    static final String ENTITY = "SALES_QUOTE";
    static final String VALIDITY_DAYS = "sales.quote-validity-days";

    public record QuoteFilter(Long companyId, Long branchId, SalesDocumentStatus status, Long customerId,
                              LocalDate dateFrom, LocalDate dateTo, String keyword) {
    }

    private final SalesQuoteRepository repository;
    private final SalesDocumentFlow flow;
    private final SalesAccess access;
    private final SalesPricing pricing;
    private final ApprovalService approvalService;
    private final ParameterService parameters;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final SalesDocumentFlow.Spec<SalesQuote, SalesQuoteResponse> spec;

    @SuppressWarnings("java:S107")
    public SalesQuoteService(SalesQuoteRepository repository, SalesDocumentFlow flow, SalesAccess access,
                             SalesPricing pricing, ApprovalService approvalService, ParameterService parameters,
                             BusinessCalendar calendar, Clock clock) {
        this.repository = repository;
        this.flow = flow;
        this.access = access;
        this.pricing = pricing;
        this.approvalService = approvalService;
        this.parameters = parameters;
        this.calendar = calendar;
        this.clock = clock;
        this.spec = new SalesDocumentFlow.Spec<>(PATH, VIEW, CREATE, APPROVE, ENTITY, repository::findByIdForUpdate,
                repository, document -> toResponse(document, true), SalesQuoteResponse.class,
                this::priceApprovalRequired, null, document -> { }, document -> { });
    }

    @Transactional(readOnly = true)
    public Page<SalesQuoteResponse> search(CurrentUser actor, QuoteFilter filter, Pageable pageable) {
        Page<SalesQuote> page = flow.search(actor, VIEW, repository, new SalesDocumentFlow.Filter<SalesQuote>(
                filter.companyId(), filter.branchId(), filter.status(), filter.dateFrom(), filter.dateTo(),
                filter.keyword(), filter.customerId() == null ? null
                : (root, query, cb) -> cb.equal(root.get("customerId"), filter.customerId())), pageable);
        Map<Long, PartnerInfo> customers = access.partners(page.getContent().stream()
                .map(SalesQuote::getCustomerId).toList());
        Map<Long, UserRef> users = access.users(page.getContent().stream().map(SalesQuote::getSalesUserId).toList());
        return page.map(document -> toResponse(document, customers.get(document.getCustomerId()),
                users.get(document.getSalesUserId()), false));
    }

    @Transactional(readOnly = true)
    public SalesQuoteResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(flow.visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)), true);
    }

    /** Giá đề xuất cho một dòng (BR-SAL-02): cần quyền xem báo giá hoặc đơn bán. */
    @Transactional(readOnly = true)
    public PriceSuggestionResponse price(CurrentUser actor, Long customerId, Long productId, Long unitId,
                                         BigDecimal quantity, LocalDate date) {
        if (!actor.hasPermission(VIEW) && !actor.hasPermission(SalesOrderService.VIEW)) {
            access.requirePermission(actor, VIEW);
        }
        return pricing.suggest(actor, customerId, productId, unitId, quantity, date == null ? calendar.today() : date);
    }

    @Transactional
    public SalesQuoteResponse create(CurrentUser actor, String idempotencyKey, SalesQuoteRequest request) {
        return flow.idempotent(actor, idempotencyKey, "POST " + PATH, request, SalesQuoteResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            CustomerProfile customer = access.customer(actor, branch.companyId(), request.customerId(), "customerId");
            SalesQuote document = new SalesQuote(branch.companyId(), branch.id(), access.nextNumber(branch.companyId(),
                    branch.id(), SalesQuote.TYPE_CODE, request.documentDate()), customer.id());
            document.assignSalesUser(salesUser(actor, customer));
            fill(document, customer, request);
            document = repository.saveAndFlush(document);
            return flow.act(actor, document, "CREATE", null, spec);
        });
    }

    @Transactional
    public SalesQuoteResponse update(CurrentUser actor, Long id, SalesQuoteRequest request) {
        access.requirePermission(actor, CREATE);
        SalesQuote document = flow.lock(actor, id, spec);
        SalesDocumentFlow.requireVersion(document, request.version());
        if (!document.getBranchId().equals(request.branchId())
                || !document.getCustomerId().equals(request.customerId())) {
            throw BusinessException.invalidField("customerId", "Không đổi chi nhánh, khách hàng sau khi lập.");
        }
        fill(document, access.customer(actor, document.getCompanyId(), document.getCustomerId(), "customerId"),
                request);
        return flow.act(actor, document, "UPDATE", null, spec);
    }

    @Transactional
    public SalesQuoteResponse submit(CurrentUser actor, Long id, String key) {
        return flow.submit(actor, id, key, spec);
    }

    @Transactional
    public SalesQuoteResponse approve(CurrentUser actor, Long id, String key, CommentRequest request) {
        return flow.approve(actor, id, key, request, spec);
    }

    @Transactional
    public SalesQuoteResponse reject(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.reject(actor, id, key, request, spec);
    }

    @Transactional
    public SalesQuoteResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.cancel(actor, id, key, request, spec);
    }

    /** Đã gửi khách (chỉ báo giá đã duyệt giá, BR-SAL-03). */
    @Transactional
    public SalesQuoteResponse markSent(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/send", id, SalesQuoteResponse.class, () -> {
            access.requirePermission(actor, CREATE);
            SalesQuote document = flow.lock(actor, id, spec);
            document.markSent(Instant.now(clock));
            return flow.act(actor, document, "SEND", null, spec);
        });
    }

    @Transactional
    public SalesQuoteResponse accept(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/accept", id, SalesQuoteResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    SalesQuote document = flow.lock(actor, id, spec);
                    document.accept(calendar.today(), Instant.now(clock));
                    return flow.act(actor, document, "ACCEPT", null, spec);
                });
    }

    @Transactional
    public SalesQuoteResponse decline(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/decline", request, SalesQuoteResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    SalesQuote document = flow.lock(actor, id, spec);
                    document.decline(request.reason().trim(), Instant.now(clock));
                    return flow.act(actor, document, "DECLINE", request.reason(), spec);
                });
    }

    /** Phiên bản mới (BR-SAL-01): sao nội dung sang báo giá nháp mới, phiên bản cũ đóng lại. */
    @Transactional
    public SalesQuoteResponse revise(CurrentUser actor, Long id, String key) {
        return flow.idempotent(actor, key, "POST " + PATH + "/" + id + "/revise", id, SalesQuoteResponse.class,
                () -> {
                    access.requirePermission(actor, CREATE);
                    SalesQuote previous = flow.lock(actor, id, spec);
                    LocalDate today = calendar.today();
                    SalesQuote revision = SalesQuote.revisionOf(previous, access.nextNumber(previous.getCompanyId(),
                            previous.getBranchId(), SalesQuote.TYPE_CODE, today));
                    List<SalesQuoteLine> lines = new ArrayList<>();
                    previous.getLines().forEach(line -> lines.add(new SalesQuoteLine(lines.size() + 1,
                            line.values())));
                    revision.replaceContent(new SalesQuote.Header(today, previous.getContactName(),
                            validUntil(previous.getCompanyId(), today, null), previous.getDeliveryTerms(),
                            previous.getWarrantyTerms(), previous.getPaymentTerms(), previous.getDescription()),
                            lines);
                    revision = repository.saveAndFlush(revision);
                    previous.supersede(revision.getDocumentNo(), actor.id(), Instant.now(clock));
                    flow.act(actor, previous, "REVISE", "Phiên bản mới " + revision.getDocumentNo(), spec);
                    return flow.act(actor, revision, "CREATE", null, spec);
                });
    }

    /** Báo giá đã duyệt / đã gửi quá hạn hiệu lực chuyển EXPIRED (tác vụ định kỳ). */
    @Transactional
    public int expireDue() {
        LocalDate today = calendar.today();
        int count = 0;
        for (Long id : repository.findExpiredIds(today)) {
            SalesQuote quote = repository.findByIdForUpdate(id).orElseThrow();
            if (quote.expireIfDue(today)) {
                count++;
            }
        }
        return count;
    }

    SalesDocumentFlow.Spec<SalesQuote, SalesQuoteResponse> spec() {
        return spec;
    }

    /** Lý do phải duyệt giá lúc gửi duyệt; có lý do thì bắt buộc qua luồng duyệt (BR-SAL-03). */
    private boolean priceApprovalRequired(SalesQuote document) {
        List<String> reasons = pricing.priceReasons(document.getCompanyId(), document.getLines());
        document.recordApprovalReasons(reasons);
        return !reasons.isEmpty();
    }

    private void fill(SalesQuote document, CustomerProfile customer, SalesQuoteRequest request) {
        List<SalesQuoteLine> lines = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            SalesLineRequest line = request.lines().get(i);
            lines.add(new SalesQuoteLine(i + 1, pricing.line(document.getCompanyId(), customer, line,
                    request.documentDate(), "lines[" + i + "]")));
        }
        document.replaceContent(new SalesQuote.Header(request.documentDate(), SalesAccess.trim(request.contactName()),
                validUntil(document.getCompanyId(), request.documentDate(), request.validUntil()),
                SalesAccess.trim(request.deliveryTerms()), SalesAccess.trim(request.warrantyTerms()),
                SalesAccess.trim(request.paymentTerms()), SalesAccess.trim(request.description())), lines);
    }

    private LocalDate validUntil(Long companyId, LocalDate date, LocalDate requested) {
        return requested != null ? requested : date.plusDays(parameters.integerValue(companyId, VALIDITY_DAYS));
    }

    /** Nhân viên phụ trách: người lập nếu chỉ xem dữ liệu của mình, không thì nhân viên của khách, rồi người lập. */
    static Long salesUser(CurrentUser actor, CustomerProfile customer) {
        if (actor.dataScope().ownDataOnly() || customer.salesUserId() == null) {
            return actor.id();
        }
        return customer.salesUserId();
    }

    SalesQuoteResponse toResponse(SalesQuote document, boolean detailed) {
        return toResponse(document, access.partners(List.of(document.getCustomerId())).get(document.getCustomerId()),
                document.getSalesUserId() == null ? null
                        : access.users(List.of(document.getSalesUserId())).get(document.getSalesUserId()), detailed);
    }

    private SalesQuoteResponse toResponse(SalesQuote document, PartnerInfo customer, UserRef salesUser,
                                          boolean detailed) {
        Map<Long, ProductInfo> products = detailed ? access.products(document.getLines().stream()
                .map(SalesQuoteLine::getProductId).toList()) : Map.of();
        return new SalesQuoteResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentDate(), document.getRevisionNo(),
                document.getPreviousQuoteId(), document.getCustomerId(), customer == null ? null : customer.code(),
                customer == null ? null : customer.name(), document.getContactName(), document.getValidUntil(),
                document.getDeliveryTerms(), document.getWarrantyTerms(), document.getPaymentTerms(),
                document.getSalesUserId(), salesUser == null ? null : salesUser.fullName(), document.getDescription(),
                document.getAmount(), document.getDiscountAmount(), document.getTaxAmount(),
                document.getTotalAmount(), document.getApprovalReasons(), document.getSalesOrderId(),
                document.getStatus(), document.getSubmittedAt(), document.getApprovedAt(), document.getSentAt(),
                document.getRespondedAt(), document.getClosedAt(), document.getReason(), document.getCreatedBy(),
                document.getVersion(), detailed ? document.getLines().stream()
                .map(line -> SalesLineMapper.toResponse(line, products.get(line.getProductId()), null, null))
                .toList() : List.of(),
                detailed ? approvalService.history(SalesQuote.TYPE_CODE, document.getId()) : List.of());
    }
}
