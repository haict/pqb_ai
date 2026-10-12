package com.company.erp.sales.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dòng phiếu giao hàng theo dòng đơn bán. Lô chỉ định (trống: FEFO, BR-SAL-21; chọn lô khác lô FEFO phải ghi lý do,
 * BR-INV-21), serial chỉ định với hàng quản lý serial. Theo dõi số đã xuất hóa đơn, đã trả lại (đơn vị cơ sở): còn
 * chưa xuất hóa đơn = đã xuất − đã xuất hóa đơn − (đã trả − đã trả có hóa đơn).
 */
@Entity
@Table(name = "sales_delivery_lines")
public class SalesDeliveryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "order_line_id", nullable = false)
    private Long orderLineId;

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

    @Column(name = "lot_no")
    private String lotNo;

    @Column(name = "lot_override_reason")
    private String lotOverrideReason;

    @Column(name = "invoiced_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal invoicedQuantity = BigDecimal.ZERO;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "returned_invoiced_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedInvoicedQuantity = BigDecimal.ZERO;

    @Column(name = "note")
    private String note;

    @ElementCollection
    @CollectionTable(name = "sales_delivery_serials", joinColumns = @JoinColumn(name = "line_id"))
    @Column(name = "serial_no", length = 100)
    @OrderBy
    private List<String> serialNumbers = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "line_id", nullable = false)
    @OrderBy("id")
    private List<SalesDeliveryItem> items = new ArrayList<>();

    protected SalesDeliveryLine() {
    }

    public record Values(Long orderLineId, Long productId, Long unitId, BigDecimal conversionFactor,
                         BigDecimal quantity, BigDecimal baseQuantity, String lotNo, String lotOverrideReason,
                         String note, List<String> serialNumbers) {
    }

    public SalesDeliveryLine(int lineNo, Values values) {
        this.lineNo = lineNo;
        apply(values);
    }

    private void apply(Values values) {
        this.orderLineId = values.orderLineId();
        this.productId = values.productId();
        this.unitId = values.unitId();
        this.conversionFactor = values.conversionFactor();
        this.quantity = values.quantity();
        this.baseQuantity = values.baseQuantity();
        this.lotNo = values.lotNo();
        this.lotOverrideReason = values.lotOverrideReason();
        this.note = values.note();
        this.serialNumbers.clear();
        this.serialNumbers.addAll(values.serialNumbers());
    }

    void copyFrom(SalesDeliveryLine other) {
        apply(new Values(other.orderLineId, other.productId, other.unitId, other.conversionFactor, other.quantity,
                other.baseQuantity, other.lotNo, other.lotOverrideReason, other.note,
                new ArrayList<>(other.serialNumbers)));
    }

    /** Ghi hàng thực xuất khi xuất kho; xóa khi đảo. */
    void replaceItems(List<SalesDeliveryItem> issued) {
        items.clear();
        items.addAll(issued);
    }

    /** Còn chưa xuất hóa đơn (đơn vị cơ sở). */
    public BigDecimal uninvoicedQuantity() {
        return baseQuantity.subtract(invoicedQuantity).subtract(returnedQuantity).add(returnedInvoicedQuantity)
                .max(BigDecimal.ZERO);
    }

    /** Còn trả lại được (đơn vị cơ sở). */
    public BigDecimal returnableQuantity() {
        return baseQuantity.subtract(returnedQuantity).max(BigDecimal.ZERO);
    }

    /** Hóa đơn bán ghi sổ (dương, không vượt số chưa xuất hóa đơn) / đảo (âm). */
    void addInvoiced(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(uninvoicedQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của phiếu giao còn " + plain(uninvoicedQuantity()) + " (đơn vị cơ sở) chưa xuất hóa đơn.");
        }
        invoicedQuantity = invoicedQuantity.add(delta).max(BigDecimal.ZERO);
    }

    /** Khách trả lại (dương) / đảo (âm); {@code invoiced}: phần thuộc hàng đã có hóa đơn (BR-SAL-32). */
    void addReturned(BigDecimal delta, boolean invoiced, String field) {
        if (delta.signum() > 0) {
            BigDecimal limit = invoiced ? returnableQuantity() : uninvoicedQuantity().min(returnableQuantity());
            if (delta.compareTo(limit) > 0) {
                throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                        + " của phiếu giao chỉ còn trả được " + plain(limit) + " (đơn vị cơ sở)"
                        + (invoiced ? "." : " chưa xuất hóa đơn."));
            }
        }
        returnedQuantity = returnedQuantity.add(delta).max(BigDecimal.ZERO);
        if (invoiced) {
            returnedInvoicedQuantity = returnedInvoicedQuantity.add(delta).max(BigDecimal.ZERO);
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getOrderLineId() {
        return orderLineId;
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

    public String getLotNo() {
        return lotNo;
    }

    public String getLotOverrideReason() {
        return lotOverrideReason;
    }

    public BigDecimal getInvoicedQuantity() {
        return invoicedQuantity;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getReturnedInvoicedQuantity() {
        return returnedInvoicedQuantity;
    }

    public String getNote() {
        return note;
    }

    public List<String> getSerialNumbers() {
        return Collections.unmodifiableList(serialNumbers);
    }

    public List<SalesDeliveryItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
