package com.company.erp.sales.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.sales.application.dto.ReasonRequest;
import com.company.erp.sales.domain.SalesDocument;
import com.company.erp.sales.domain.SalesDocumentStatus;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

/**
 * Vòng đời dùng chung của chứng từ bán hàng ({@link SalesDocument}): tìm kiếm theo phạm vi, khóa chứng từ, hủy, chống
 * xử lý trùng, audit. Không qua duyệt (D-61): chứng từ ghi sổ thẳng từ DRAFT ở dịch vụ ghi sổ từng loại. Hủy gọi
 * {@link Spec#onCancelled()} để hoàn chỉ tiêu điều phối (hàng đang giữ) trong cùng transaction.
 */
@Component
class SalesDocumentFlow {

    private static final Set<String> SORTABLE = Set.of("documentDate", "documentNo", "createdAt");

    /**
     * Cấu hình của một loại chứng từ.
     *
     * @param path        đường dẫn API (khóa chống xử lý trùng)
     * @param entityType  loại thực thể ghi audit
     * @param onCancelled hoàn chỉ tiêu điều phối khi hủy chứng từ chưa ghi sổ
     */
    record Spec<D extends SalesDocument, R>(String path, String viewPermission, String createPermission,
                                            String entityType, Function<Long, Optional<D>> lockById,
                                            JpaRepository<D, Long> repository, Function<D, R> mapper,
                                            Class<R> responseType, Consumer<D> onCancelled) {
    }

    /** Bộ lọc chung; {@code extra} thêm điều kiện riêng của loại chứng từ. */
    record Filter<D>(Long companyId, Long branchId, SalesDocumentStatus status, LocalDate dateFrom,
                     LocalDate dateTo, String keyword, Specification<D> extra) {
    }

    private final ApprovalService approvalService;
    private final SalesAccess access;
    private final IdempotencyService idempotency;

    SalesDocumentFlow(ApprovalService approvalService, SalesAccess access, IdempotencyService idempotency) {
        this.approvalService = approvalService;
        this.access = access;
        this.idempotency = idempotency;
    }

    <D extends SalesDocument> Page<D> search(CurrentUser actor, String viewPermission,
                                                JpaSpecificationExecutor<D> repository, Filter<D> filter,
                                                Pageable pageable) {
        access.requirePermission(actor, viewPermission);
        Set<Long> branches = access.branchScope(actor, filter.companyId());
        Specification<D> spec = (root, query, cb) -> {
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
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("documentDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("documentDate"), filter.dateTo()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String like = "%" + filter.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("documentNo")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        if (filter.extra() != null) {
            spec = spec.and(filter.extra());
        }
        return repository.findAll(spec, PageRequests.checked(pageable, SORTABLE,
                Sort.by(Sort.Order.desc("documentDate"), Sort.Order.desc("id"))));
    }

    <D extends SalesDocument> D visible(CurrentUser actor, D document) {
        if (!access.canAccess(actor, document.getCompanyId(), document.getBranchId())
                || !access.ownsData(actor, document.getSalesUserId(), document.getCreatedBy())) {
            throw BusinessException.notFound();
        }
        return document;
    }

    <D extends SalesDocument, R> D lock(CurrentUser actor, Long id, Spec<D, R> spec) {
        return visible(actor, spec.lockById().apply(id).orElseThrow(BusinessException::notFound));
    }

    /** Chạy lệnh có Idempotency-Key (khóa = người dùng + key + đường dẫn hành động + nội dung). */
    <R> R idempotent(CurrentUser actor, String key, String path, Object body, Class<R> type, Supplier<R> action) {
        return idempotency.execute(new IdempotentCommand(actor.id(), key, path, body), type, action);
    }

    <D extends SalesDocument, R> R cancel(CurrentUser actor, Long id, String key, ReasonRequest request,
                                             Spec<D, R> spec) {
        return idempotent(actor, key, "POST " + spec.path() + "/" + id + "/cancel", request, spec.responseType(),
                () -> {
                    access.requirePermission(actor, spec.createPermission());
                    D document = lock(actor, id, spec);
                    SalesDocumentStatus before = document.getStatus();
                    // Chứng từ gửi duyệt trước D-61: đóng yêu cầu duyệt còn treo để lịch sử duyệt nhất quán.
                    if (before == SalesDocumentStatus.SUBMITTED) {
                        approvalService.cancelPending(actor, document.typeCode(), id, request.reason());
                    }
                    document.cancel(request.reason().trim());
                    spec.onCancelled().accept(document);
                    return act(actor, document, "CANCEL", request.reason(), spec);
                });
    }

    /** Lưu, ghi audit trạng thái sau thao tác, trả DTO chi tiết. */
    <D extends SalesDocument, R> R act(CurrentUser actor, D document, String action, String reason,
                                          Spec<D, R> spec) {
        spec.repository().saveAndFlush(document);
        access.audit(actor, action, spec.entityType(), document.getId(), document.getDocumentNo(), reason, null,
                document.getStatus(), document.getCompanyId(), document.getBranchId());
        return spec.mapper().apply(document);
    }

    static void requireVersion(SalesDocument document, Integer version) {
        if (version == null) {
            throw BusinessException.invalidField("version", "Thiếu version khi sửa chứng từ.");
        }
        document.verifyVersion(version);
    }
}
