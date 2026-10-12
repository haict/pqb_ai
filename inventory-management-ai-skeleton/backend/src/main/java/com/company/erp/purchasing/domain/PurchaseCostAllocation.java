package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Phân bổ chi phí mua hàng (PBC, BR-PUR-12, 16): chi phí từ dòng dịch vụ của hóa đơn mua đã ghi sổ (vận chuyển, bảo
 * hiểm, bốc xếp...) phân bổ vào dòng phiếu nhận đã nhập kho theo giá trị hoặc số lượng. Ghi sổ sinh điều chỉnh giá trị
 * nhập theo dòng phiếu nhận ({@link PurchaseCostEntry} loại LANDED_COST); giá vốn tính lại ở Giai đoạn 8.
 */
@Entity
@Table(name = "purchase_cost_allocations")
public class PurchaseCostAllocation extends PurchaseDocument {

    public static final String TYPE_CODE = "PBC";

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_method", nullable = false)
    private CostAllocationMethod method;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_date")
    private LocalDate reverseDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("lineNo")
    private List<Source> sources = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "allocation_id", nullable = false)
    @OrderBy("lineNo")
    private List<Target> targets = new ArrayList<>();

    protected PurchaseCostAllocation() {
    }

    public PurchaseCostAllocation(Long companyId, Long branchId, String documentNo) {
        super(companyId, branchId, documentNo);
    }

    /** Dòng chi phí: dòng dịch vụ hóa đơn mua và số tiền phân bổ (trước thuế). */
    public record SourceValues(Long invoiceLineId, Long productId, BigDecimal amount) {
    }

    /** Dòng đích: dòng phiếu nhận, số lượng nhập (đơn vị cơ sở) và giá trị nhập tạm tính làm cơ sở. */
    public record TargetValues(Long receiptLineId, Long productId, BigDecimal baseQuantity, BigDecimal basisValue) {
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return amount;
    }

    @Override
    protected boolean fulfilled() {
        return false;
    }

    @Override
    protected boolean hasProgress() {
        return false;
    }

    /**
     * Thay nội dung và tính lại phân bổ: tỷ lệ theo cơ sở, làm tròn đến đồng, dòng đích cuối nhận phần dư để tổng phân
     * bổ bằng tổng chi phí.
     */
    public void replaceContent(LocalDate documentDate, LocalDate postingDate, CostAllocationMethod method,
                               String description, List<SourceValues> newSources, List<TargetValues> newTargets) {
        if (newSources.isEmpty()) {
            throw BusinessException.invalidField("sources", "Phải chọn ít nhất một dòng chi phí.");
        }
        if (newTargets.isEmpty()) {
            throw BusinessException.invalidField("receiptIds", "Phải chọn phiếu nhận hàng đã nhập kho để phân bổ.");
        }
        if (postingDate.isBefore(documentDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        replaceHeader(documentDate, description);
        this.postingDate = postingDate;
        this.method = method;
        this.amount = newSources.stream().map(SourceValues::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> bases = newTargets.stream()
                .map(target -> method == CostAllocationMethod.VALUE ? target.basisValue() : target.baseQuantity())
                .toList();
        BigDecimal totalBasis = bases.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalBasis.signum() <= 0) {
            throw BusinessException.invalidField("method", "Tổng cơ sở phân bổ bằng 0; chọn phân bổ theo số lượng.");
        }
        for (int i = 0; i < newSources.size(); i++) {
            if (i < sources.size()) {
                sources.get(i).apply(newSources.get(i));
            } else {
                sources.add(new Source(i + 1, newSources.get(i)));
            }
        }
        trim(sources, newSources.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < newTargets.size(); i++) {
            BigDecimal share = i == newTargets.size() - 1 ? amount.subtract(allocated)
                    : amount.multiply(bases.get(i)).divide(totalBasis, 0, RoundingMode.HALF_UP);
            allocated = allocated.add(share);
            if (i < targets.size()) {
                targets.get(i).apply(newTargets.get(i), share);
            } else {
                targets.add(new Target(i + 1, newTargets.get(i), share));
            }
        }
        trim(targets, newTargets.size());
    }

    /** Sửa tại chỗ rồi bỏ dòng thừa: tránh chèn dòng mới trước khi xóa dòng cũ cùng số dòng (unique). */
    private static void trim(List<?> list, int size) {
        while (list.size() > size) {
            list.remove(list.size() - 1);
        }
    }

    public void markPosted(Long actorId, Instant now) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.APPROVED), "ghi sổ");
        setStatus(PurchaseDocumentStatus.POSTED);
        this.postedBy = actorId;
        this.postedAt = now;
    }

    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(PurchaseDocumentStatus.POSTED), "đảo");
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        setStatus(PurchaseDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        setReason(reason);
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public CostAllocationMethod getMethod() {
        return method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public LocalDate getReverseDate() {
        return reverseDate;
    }

    public List<Source> getSources() {
        return Collections.unmodifiableList(sources);
    }

    public List<Target> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    /** Dòng chi phí. */
    @Entity
    @Table(name = "purchase_cost_allocation_sources")
    public static class Source {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "line_no", nullable = false)
        private int lineNo;

        @Column(name = "invoice_line_id", nullable = false)
        private Long invoiceLineId;

        @Column(name = "product_id", nullable = false)
        private Long productId;

        @Column(name = "amount", nullable = false, precision = 19, scale = 4)
        private BigDecimal amount;

        protected Source() {
        }

        Source(int lineNo, SourceValues values) {
            this.lineNo = lineNo;
            apply(values);
        }

        void apply(SourceValues values) {
            this.invoiceLineId = values.invoiceLineId();
            this.productId = values.productId();
            this.amount = values.amount();
        }

        public Long getId() {
            return id;
        }

        public int getLineNo() {
            return lineNo;
        }

        public Long getInvoiceLineId() {
            return invoiceLineId;
        }

        public Long getProductId() {
            return productId;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

    /** Dòng đích phân bổ. */
    @Entity
    @Table(name = "purchase_cost_allocation_targets")
    public static class Target {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "line_no", nullable = false)
        private int lineNo;

        @Column(name = "receipt_line_id", nullable = false)
        private Long receiptLineId;

        @Column(name = "product_id", nullable = false)
        private Long productId;

        @Column(name = "base_quantity", nullable = false, precision = 19, scale = 3)
        private BigDecimal baseQuantity;

        @Column(name = "basis_value", nullable = false, precision = 19, scale = 4)
        private BigDecimal basisValue;

        @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
        private BigDecimal allocatedAmount;

        protected Target() {
        }

        Target(int lineNo, TargetValues values, BigDecimal allocatedAmount) {
            this.lineNo = lineNo;
            apply(values, allocatedAmount);
        }

        void apply(TargetValues values, BigDecimal allocatedAmount) {
            this.receiptLineId = values.receiptLineId();
            this.productId = values.productId();
            this.baseQuantity = values.baseQuantity();
            this.basisValue = values.basisValue();
            this.allocatedAmount = allocatedAmount;
        }

        public Long getId() {
            return id;
        }

        public int getLineNo() {
            return lineNo;
        }

        public Long getReceiptLineId() {
            return receiptLineId;
        }

        public Long getProductId() {
            return productId;
        }

        public BigDecimal getBaseQuantity() {
            return baseQuantity;
        }

        public BigDecimal getBasisValue() {
            return basisValue;
        }

        public BigDecimal getAllocatedAmount() {
            return allocatedAmount;
        }
    }
}
