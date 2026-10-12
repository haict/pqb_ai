package com.company.erp.finance.application;

import com.company.erp.common.api.PageRequests;
import com.company.erp.common.application.IdempotencyService;
import com.company.erp.common.application.IdempotentCommand;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.finance.application.dto.CommentRequest;
import com.company.erp.finance.application.dto.ReasonRequest;
import com.company.erp.finance.domain.FinanceDocument;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.system.application.ApprovalService;
import com.company.erp.system.application.ApprovalSubmission;
import com.company.erp.system.application.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * Vòng đời dùng chung của chứng từ ghi sổ công nợ, quỹ ({@link FinanceDocument}): tìm kiếm theo phạm vi, khóa chứng từ,
 * gửi duyệt / duyệt / từ chối / hủy qua luồng duyệt (BR-SYS-11), chống xử lý trùng, audit. Chứng từ không cần duyệt
 * (chế độ NONE, hoặc CONFIGURED không có luồng khớp) chuyển thẳng APPROVED khi gửi.
 */
@Component
class FinanceDocumentFlow {

    private static final Set<String> SORTABLE = Set.of("documentDate", "documentNo", "createdAt");

    /**
     * Cấu hình của một loại chứng từ.
     *
     * @param path       đường dẫn API (khóa chống xử lý trùng)
     * @param entityType loại thực thể ghi audit
     */
    record Spec<D extends FinanceDocument, R>(String path, String viewPermission, String createPermission,
                                              String approvePermission, String entityType,
                                              Function<Long, Optional<D>> lockById, JpaRepository<D, Long> repository,
                                              Function<D, R> mapper, Class<R> responseType) {
    }

    /** Bộ lọc chung; {@code extra} thêm điều kiện riêng của loại chứng từ. */
    record Filter<D>(Long companyId, Long branchId, FinanceDocumentStatus status, Long partnerId, LocalDate dateFrom,
                     LocalDate dateTo, String keyword, Specification<D> extra) {
    }

    private final ApprovalService approvalService;
    private final FinanceAccess access;
    private final IdempotencyService idempotency;
    private final Clock clock;

    FinanceDocumentFlow(ApprovalService approvalService, FinanceAccess access, IdempotencyService idempotency,
                        Clock clock) {
        this.approvalService = approvalService;
        this.access = access;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    <D extends FinanceDocument> Page<D> search(CurrentUser actor, String viewPermission,
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
            if (filter.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), filter.branchId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.partnerId() != null) {
                predicates.add(cb.equal(root.get("partnerId"), filter.partnerId()));
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

    <D extends FinanceDocument> D visible(CurrentUser actor, D document) {
        if (!access.canAccess(actor, document.getCompanyId(), document.getBranchId())) {
            throw BusinessException.notFound();
        }
        return document;
    }

    <D extends FinanceDocument, R> D lock(CurrentUser actor, Long id, Spec<D, R> spec) {
        return visible(actor, spec.lockById().apply(id).orElseThrow(BusinessException::notFound));
    }

    /** Chạy lệnh có Idempotency-Key (khóa = người dùng + key + đường dẫn hành động + nội dung). */
    <R> R idempotent(CurrentUser actor, String key, String path, Object body, Class<R> type, Supplier<R> action) {
        return idempotency.execute(new IdempotentCommand(actor.id(), key, path, body), type, action);
    }

    <D extends FinanceDocument, R> R submit(CurrentUser actor, Long id, String key, Spec<D, R> spec) {
        return idempotent(actor, key, "POST " + spec.path() + "/" + id + "/submit", id, spec.responseType(), () -> {
            access.requirePermission(actor, spec.createPermission());
            D document = lock(actor, id, spec);
            Instant now = Instant.now(clock);
            document.submit(actor.id(), now);
            boolean required = approvalService.submit(actor, new ApprovalSubmission(document.getCompanyId(),
                    document.getBranchId(), document.typeCode(), document.getId(), document.getDocumentNo(),
                    document.approvalAmount(), null)).approvalRequired();
            if (!required) {
                document.approve(actor.id(), now);
            }
            return act(actor, document, "SUBMIT", null, spec);
        });
    }

    <D extends FinanceDocument, R> R approve(CurrentUser actor, Long id, String key, CommentRequest request,
                                             Spec<D, R> spec) {
        return idempotent(actor, key, "POST " + spec.path() + "/" + id + "/approve", request, spec.responseType(),
                () -> {
                    access.requirePermission(actor, spec.approvePermission());
                    D document = lock(actor, id, spec);
                    requireSubmitted(document);
                    String comment = request == null ? null : request.comment();
                    if (approvalService.approve(actor, document.typeCode(), id, comment).completed()) {
                        document.approve(actor.id(), Instant.now(clock));
                    }
                    return act(actor, document, "APPROVE", comment, spec);
                });
    }

    <D extends FinanceDocument, R> R reject(CurrentUser actor, Long id, String key, ReasonRequest request,
                                            Spec<D, R> spec) {
        return idempotent(actor, key, "POST " + spec.path() + "/" + id + "/reject", request, spec.responseType(),
                () -> {
                    access.requirePermission(actor, spec.approvePermission());
                    D document = lock(actor, id, spec);
                    requireSubmitted(document);
                    approvalService.reject(actor, document.typeCode(), id, request.reason());
                    document.reject(request.reason().trim());
                    return act(actor, document, "REJECT", request.reason(), spec);
                });
    }

    <D extends FinanceDocument, R> R cancel(CurrentUser actor, Long id, String key, ReasonRequest request,
                                            Spec<D, R> spec) {
        return idempotent(actor, key, "POST " + spec.path() + "/" + id + "/cancel", request, spec.responseType(),
                () -> {
                    access.requirePermission(actor, spec.createPermission());
                    D document = lock(actor, id, spec);
                    if (document.getStatus() == FinanceDocumentStatus.SUBMITTED) {
                        approvalService.cancelPending(actor, document.typeCode(), id, request.reason());
                    }
                    document.cancel(request.reason().trim());
                    return act(actor, document, "CANCEL", request.reason(), spec);
                });
    }

    /** Lưu, ghi audit trạng thái sau thao tác, trả DTO chi tiết. */
    <D extends FinanceDocument, R> R act(CurrentUser actor, D document, String action, String reason,
                                         Spec<D, R> spec) {
        spec.repository().saveAndFlush(document);
        access.audit(actor, action, spec.entityType(), document.getId(), document.getDocumentNo(), reason, null,
                document.getStatus(), document.getCompanyId(), document.getBranchId());
        return spec.mapper().apply(document);
    }

    static void requireVersion(FinanceDocument document, Integer version) {
        if (version == null) {
            throw BusinessException.invalidField("version", "Thiếu version khi sửa chứng từ.");
        }
        document.verifyVersion(version);
    }

    private static void requireSubmitted(FinanceDocument document) {
        if (document.getStatus() != FinanceDocumentStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Chứng từ " + document.getDocumentNo()
                    + " không ở trạng thái chờ duyệt.");
        }
    }
}
