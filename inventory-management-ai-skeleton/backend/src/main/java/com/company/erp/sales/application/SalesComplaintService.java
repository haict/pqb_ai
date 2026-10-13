package com.company.erp.sales.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.masterdata.application.MasterDataQueryService.CustomerProfile;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.InspectComplaintRequest;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.application.dto.ResolveComplaintRequest;
import com.company.erp.sales.application.dto.SalesComplaintRequest;
import com.company.erp.sales.application.dto.SalesComplaintResponse;
import com.company.erp.sales.domain.ComplaintStatus;
import com.company.erp.sales.domain.SalesComplaint;
import com.company.erp.sales.domain.SalesOrder;
import com.company.erp.sales.infrastructure.persistence.SalesComplaintRepository;
import com.company.erp.sales.infrastructure.persistence.SalesOrderRepository;
import com.company.erp.sales.infrastructure.persistence.SalesReturnRepository;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiếp nhận hàng lỗi khách báo (BR-SAL-34): tiếp nhận (khách, chứng từ gốc, hàng, lô, serial), kiểm tra, quyết định
 * đổi / sửa / trả / bảo hành / từ chối (kèm chứng từ trả hàng của cùng khách khi đổi / trả), hủy. Không ghi sổ.
 */
@Service
public class SalesComplaintService {

    static final String PATH = "/api/v1/sales-complaints";
    static final String VIEW = "sales-complaint:view";
    static final String CREATE = "sales-complaint:create";
    static final String RESOLVE = "sales-complaint:resolve";
    static final String ENTITY = "SALES_COMPLAINT";
    private static final Set<String> SORTABLE = Set.of("receivedDate", "complaintNo", "createdAt");

    public record ComplaintFilter(Long companyId, Long branchId, ComplaintStatus status, Long customerId,
                                  String keyword) {
    }

    private final SalesComplaintRepository repository;
    private final SalesOrderRepository orderRepository;
    private final SalesReturnRepository returnRepository;
    private final SalesAccess access;
    private final IdempotencyService idempotency;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public SalesComplaintService(SalesComplaintRepository repository, SalesOrderRepository orderRepository,
                                 SalesReturnRepository returnRepository,
                                 SalesAccess access, IdempotencyService idempotency, Clock clock) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.access = access;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<SalesComplaintResponse> search(CurrentUser actor, ComplaintFilter filter, Pageable pageable) {
        access.requirePermission(actor, VIEW);
        Set<Long> branches = access.branchScope(actor, filter.companyId());
        Specification<SalesComplaint> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (branches != null) {
                predicates.add(root.get("branchId").in(branches));
            }
            if (actor.dataScope().ownDataOnly()) {
                predicates.add(cb.or(cb.equal(root.get("salesUserId"), actor.id()),
                        cb.equal(root.get("createdBy"), actor.id())));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String like = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("complaintNo")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<SalesComplaint> page = repository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                Sort.by(Sort.Order.desc("receivedDate"), Sort.Order.desc("id"))));
        Map<Long, PartnerInfo> customers = access.partners(page.getContent().stream()
                .map(SalesComplaint::getCustomerId).toList());
        Map<Long, ProductInfo> products = access.products(page.getContent().stream()
                .map(SalesComplaint::getProductId).toList());
        return page.map(complaint -> toResponse(complaint, customers.get(complaint.getCustomerId()),
                products.get(complaint.getProductId())));
    }

    @Transactional(readOnly = true)
    public SalesComplaintResponse get(CurrentUser actor, Long id) {
        access.requirePermission(actor, VIEW);
        return toResponse(visible(actor, repository.findById(id).orElseThrow(BusinessException::notFound)));
    }

    @Transactional
    public SalesComplaintResponse create(CurrentUser actor, String key, SalesComplaintRequest request) {
        return idempotent(actor, key, "POST " + PATH, request, () -> {
            access.requirePermission(actor, CREATE);
            BranchRef branch = access.usableBranch(actor, request.branchId());
            CustomerProfile customer = access.customer(actor, branch.companyId(), request.customerId(), "customerId");
            SalesComplaint complaint = new SalesComplaint(branch.companyId(), branch.id(), access.nextNumber(
                    branch.companyId(), branch.id(), SalesComplaint.TYPE_CODE, request.receivedDate()), customer.id(),
                    SalesAccess.salesUser(actor, customer), details(branch.companyId(), customer.id(), request));
            complaint = repository.saveAndFlush(complaint);
            return act(actor, complaint, "CREATE", null);
        });
    }

    @Transactional
    public SalesComplaintResponse update(CurrentUser actor, Long id, SalesComplaintRequest request) {
        access.requirePermission(actor, CREATE);
        SalesComplaint complaint = lock(actor, id);
        if (request.version() == null) {
            throw BusinessException.invalidField("version", "Thiếu version khi sửa.");
        }
        complaint.verifyVersion(request.version());
        if (!complaint.getCustomerId().equals(request.customerId())) {
            throw BusinessException.invalidField("customerId", "Không đổi khách sau khi tiếp nhận.");
        }
        complaint.update(details(complaint.getCompanyId(), complaint.getCustomerId(), request));
        return act(actor, complaint, "UPDATE", null);
    }

    @Transactional
    public SalesComplaintResponse inspect(CurrentUser actor, Long id, String key, InspectComplaintRequest request) {
        return idempotent(actor, key, "POST " + PATH + "/" + id + "/inspect", request, () -> {
            access.requirePermission(actor, RESOLVE);
            SalesComplaint complaint = lock(actor, id);
            complaint.inspect(request.result().trim(), actor.id(), Instant.now(clock));
            return act(actor, complaint, "INSPECT", request.result());
        });
    }

    @Transactional
    public SalesComplaintResponse resolve(CurrentUser actor, Long id, String key, ResolveComplaintRequest request) {
        return idempotent(actor, key, "POST " + PATH + "/" + id + "/resolve", request, () -> {
            access.requirePermission(actor, RESOLVE);
            SalesComplaint complaint = lock(actor, id);
            if (request.salesReturnId() != null) {
                returnRepository.findById(request.salesReturnId())
                        .filter(found -> found.getCustomerId().equals(complaint.getCustomerId()))
                        .orElseThrow(() -> BusinessException.invalidField("salesReturnId",
                                "Chứng từ trả hàng không tồn tại hoặc khác khách."));
            }
            complaint.resolve(request.decision(), SalesAccess.trim(request.note()), request.salesReturnId(), actor.id(),
                    Instant.now(clock));
            return act(actor, complaint, "RESOLVE", request.decision().name());
        });
    }

    @Transactional
    public SalesComplaintResponse cancel(CurrentUser actor, Long id, String key, ReasonRequest request) {
        return idempotent(actor, key, "POST " + PATH + "/" + id + "/cancel", request, () -> {
            access.requirePermission(actor, CREATE);
            SalesComplaint complaint = lock(actor, id);
            complaint.cancel(request.reason().trim());
            return act(actor, complaint, "CANCEL", request.reason());
        });
    }

    private SalesComplaint.Details details(Long companyId, Long customerId, SalesComplaintRequest request) {
        access.product(companyId, request.productId(), "productId");
        if (request.salesOrderId() != null) {
            orderRepository.findById(request.salesOrderId()).filter(found -> found.getCustomerId().equals(customerId))
                    .map(SalesOrder::getId).orElseThrow(() -> BusinessException.invalidField("salesOrderId",
                            "Đơn bán không tồn tại hoặc khác khách."));
        }
        return new SalesComplaint.Details(request.receivedDate(), request.salesOrderId(),
                request.productId(), SalesAccess.trim(request.lotNo()), SalesAccess.trim(request.serialNo()),
                request.quantity(), request.description().trim());
    }

    private SalesComplaint lock(CurrentUser actor, Long id) {
        return visible(actor, repository.findByIdForUpdate(id).orElseThrow(BusinessException::notFound));
    }

    private SalesComplaint visible(CurrentUser actor, SalesComplaint complaint) {
        if (!access.canAccess(actor, complaint.getCompanyId(), complaint.getBranchId())
                || !access.ownsData(actor, complaint.getSalesUserId(), complaint.getCreatedBy())) {
            throw BusinessException.notFound();
        }
        return complaint;
    }

    private SalesComplaintResponse idempotent(CurrentUser actor, String key, String path, Object body,
                                              Supplier<SalesComplaintResponse> action) {
        return idempotency.execute(new IdempotentCommand(actor.id(), key, path, body), SalesComplaintResponse.class,
                action);
    }

    private SalesComplaintResponse act(CurrentUser actor, SalesComplaint complaint, String action, String reason) {
        repository.saveAndFlush(complaint);
        access.audit(actor, action, ENTITY, complaint.getId(), complaint.getComplaintNo(), reason, null,
                complaint.getStatus(), complaint.getCompanyId(), complaint.getBranchId());
        return toResponse(complaint);
    }

    private SalesComplaintResponse toResponse(SalesComplaint complaint) {
        return toResponse(complaint, access.partners(List.of(complaint.getCustomerId())).get(complaint.getCustomerId()),
                access.products(List.of(complaint.getProductId())).get(complaint.getProductId()));
    }

    private static SalesComplaintResponse toResponse(SalesComplaint complaint, PartnerInfo customer,
                                                     ProductInfo product) {
        return new SalesComplaintResponse(complaint.getId(), complaint.getCompanyId(), complaint.getBranchId(),
                complaint.getComplaintNo(), complaint.getReceivedDate(), complaint.getCustomerId(),
                customer == null ? null : customer.code(), customer == null ? null : customer.name(),
                complaint.getSalesOrderId(), complaint.getProductId(),
                product == null ? null : product.code(), product == null ? null : product.name(),
                complaint.getLotNo(), complaint.getSerialNo(), complaint.getQuantity(), complaint.getDescription(),
                complaint.getInspectionResult(), complaint.getDecision(), complaint.getResolutionNote(),
                complaint.getSalesReturnId(), complaint.getStatus(), complaint.getInspectedAt(),
                complaint.getResolvedAt(), complaint.getCreatedBy(), complaint.getVersion());
    }
}
