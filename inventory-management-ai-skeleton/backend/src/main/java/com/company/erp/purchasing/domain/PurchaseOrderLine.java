package com.company.erp.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Dòng đơn mua (BR-PUR-04): {@code amount} = số lượng × đơn giá, chiết khấu theo %, thuế tính trên tiền sau chiết
 * khấu; tiền làm tròn đến đồng (D-05). Tiến độ (BR-PUR-05) theo đơn vị cơ sở: đã nhận, đã nhập kho, lỗi, đã nhận hóa
 * đơn, đã đóng.
 */
@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 6)
    private BigDecimal conversionFactor;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "base_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal baseQuantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal discountPercent;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "tax_rate", precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "requisition_line_id")
    private Long requisitionLineId;

    @Column(name = "service_line", nullable = false)
    private boolean serviceLine;

    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "stocked_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal stockedQuantity = BigDecimal.ZERO;

    @Column(name = "rejected_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    @Column(name = "invoiced_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal invoicedQuantity = BigDecimal.ZERO;

    @Column(name = "closed_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal closedQuantity = BigDecimal.ZERO;

    @Column(name = "note")
    private String note;

    protected PurchaseOrderLine() {
    }

    /** Giá trị dòng; tiền tính lại từ số lượng, đơn giá, chiết khấu, thuế suất (không nhận tiền từ client). */
    public record Values(Long productId, Long unitId, BigDecimal conversionFactor, BigDecimal quantity,
                         BigDecimal baseQuantity, BigDecimal unitPrice, BigDecimal discountPercent, Long taxRateId,
                         BigDecimal taxRate, LocalDate expectedDate, Long requisitionLineId, boolean serviceLine,
                         String note) {
    }

    public PurchaseOrderLine(int lineNo, Values values) {
        this.lineNo = lineNo;
        apply(values);
    }

    private void apply(Values values) {
        this.productId = values.productId();
        this.unitId = values.unitId();
        this.conversionFactor = values.conversionFactor();
        this.quantity = values.quantity();
        this.baseQuantity = values.baseQuantity();
        this.unitPrice = values.unitPrice();
        this.discountPercent = values.discountPercent() == null ? BigDecimal.ZERO : values.discountPercent();
        this.taxRateId = values.taxRateId();
        this.taxRate = values.taxRate();
        this.expectedDate = values.expectedDate();
        this.requisitionLineId = values.requisitionLineId();
        this.serviceLine = values.serviceLine();
        this.note = values.note();
        this.amount = money(quantity.multiply(unitPrice));
        this.discountAmount = money(amount.multiply(discountPercent).divide(HUNDRED));
        this.taxAmount = taxRate == null ? BigDecimal.ZERO
                : money(amount.subtract(discountAmount).multiply(taxRate).divide(HUNDRED));
    }

    void copyFrom(PurchaseOrderLine other) {
        apply(new Values(other.productId, other.unitId, other.conversionFactor, other.quantity, other.baseQuantity,
                other.unitPrice, other.discountPercent, other.taxRateId, other.taxRate, other.expectedDate,
                other.requisitionLineId, other.serviceLine, other.note));
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Số còn thiếu, chưa đóng (đơn vị cơ sở): hàng hóa theo số đã nhập kho (phần đóng góp vào hàng đang về); dịch vụ
     * (không qua kho, BR-PUR-16) theo số đã nhận hóa đơn.
     */
    public BigDecimal outstandingQuantity() {
        BigDecimal done = serviceLine ? invoicedQuantity : stockedQuantity;
        return baseQuantity.subtract(done).subtract(closedQuantity).max(BigDecimal.ZERO);
    }

    /** Hóa đơn mua ghi sổ (dương) / đảo (âm): số đã nhận hóa đơn. */
    void addInvoiced(BigDecimal delta) {
        invoicedQuantity = invoicedQuantity.add(delta).max(BigDecimal.ZERO);
    }

    /** Đóng phần chưa nhập kho; trả số vừa đóng. */
    BigDecimal closeRemaining() {
        BigDecimal outstanding = outstandingQuantity();
        closedQuantity = closedQuantity.add(outstanding);
        return outstanding;
    }

    /** Ghi nhận (dương) / hoàn (âm) số đã nhận và số lỗi theo phiếu nhận hàng, đơn vị cơ sở. */
    void addReceived(BigDecimal received, BigDecimal rejected) {
        receivedQuantity = receivedQuantity.add(received).max(BigDecimal.ZERO);
        rejectedQuantity = rejectedQuantity.add(rejected).max(BigDecimal.ZERO);
    }

    /**
     * Ghi nhận (dương) / hoàn (âm) số đã nhập kho; trả thay đổi của số còn thiếu (= thay đổi hàng đang về). Đơn đã
     * đóng: phần nhập bị đảo cộng vào đã đóng để không làm hàng đang về tăng lại.
     */
    BigDecimal addStocked(BigDecimal delta, boolean closed) {
        BigDecimal before = outstandingQuantity();
        stockedQuantity = stockedQuantity.add(delta).max(BigDecimal.ZERO);
        if (closed) {
            closedQuantity = closedQuantity.add(outstandingQuantity().subtract(before));
        }
        return outstandingQuantity().subtract(before);
    }

    /** Đơn giá tạm tính theo đơn mua cho một đơn vị của dòng (sau chiết khấu, trước thuế, BR-PUR-10). */
    public BigDecimal provisionalUnitCost() {
        return netAmount().divide(quantity, 4, RoundingMode.HALF_UP);
    }

    boolean hasProgress() {
        return receivedQuantity.signum() > 0 || stockedQuantity.signum() > 0 || invoicedQuantity.signum() > 0;
    }

    public BigDecimal netAmount() {
        return amount.subtract(discountAmount);
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getBaseQuantity() {
        return baseQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getTaxRateId() {
        return taxRateId;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public Long getRequisitionLineId() {
        return requisitionLineId;
    }

    public boolean isServiceLine() {
        return serviceLine;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public BigDecimal getStockedQuantity() {
        return stockedQuantity;
    }

    public BigDecimal getRejectedQuantity() {
        return rejectedQuantity;
    }

    public BigDecimal getInvoicedQuantity() {
        return invoicedQuantity;
    }

    public BigDecimal getClosedQuantity() {
        return closedQuantity;
    }

    public String getNote() {
        return note;
    }
}
