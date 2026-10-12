package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dòng phiếu nhận hàng: một dòng đơn mua, một kết quả kiểm nhận, một lô (BR-PUR-07). Đơn vị tính và đơn giá tạm tính
 * lấy theo dòng đơn mua; serial cho hàng quản lý serial (dòng nhập kho).
 */
@Entity
@Table(name = "purchase_receipt_lines")
public class PurchaseReceiptLine {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_result", nullable = false)
    private InspectionResult inspectionResult;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "lot_no")
    private String lotNo;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "invoiced_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal invoicedQuantity = BigDecimal.ZERO;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "returned_invoiced_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedInvoicedQuantity = BigDecimal.ZERO;

    @Column(name = "note")
    private String note;

    @ElementCollection
    @CollectionTable(name = "purchase_receipt_serials", joinColumns = @JoinColumn(name = "line_id"))
    @Column(name = "serial_no", length = 100)
    @OrderBy
    private List<String> serialNumbers = new ArrayList<>();

    protected PurchaseReceiptLine() {
    }

    /** Giá trị dòng đã kiểm tra ở tầng ứng dụng. */
    public record Values(Long orderLineId, Long productId, Long unitId, BigDecimal conversionFactor,
                         BigDecimal quantity, BigDecimal baseQuantity, InspectionResult inspectionResult,
                         String rejectReason, Long locationId, String lotNo, LocalDate manufactureDate,
                         LocalDate expiryDate, BigDecimal unitCost, String note, List<String> serialNumbers) {
    }

    public PurchaseReceiptLine(int lineNo, Values values) {
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
        this.inspectionResult = values.inspectionResult();
        this.rejectReason = values.rejectReason();
        this.locationId = values.locationId();
        this.lotNo = values.lotNo();
        this.manufactureDate = values.manufactureDate();
        this.expiryDate = values.expiryDate();
        this.unitCost = values.unitCost();
        this.note = values.note();
        this.serialNumbers.clear();
        this.serialNumbers.addAll(values.serialNumbers());
    }

    void copyFrom(PurchaseReceiptLine other) {
        apply(new Values(other.orderLineId, other.productId, other.unitId, other.conversionFactor, other.quantity,
                other.baseQuantity, other.inspectionResult, other.rejectReason, other.locationId, other.lotNo,
                other.manufactureDate, other.expiryDate, other.unitCost, other.note,
                new ArrayList<>(other.serialNumbers)));
    }

    /** Số lỗi (đơn vị cơ sở): dòng cách ly hoặc trả lại. */
    public BigDecimal rejectedBaseQuantity() {
        return inspectionResult == InspectionResult.ACCEPTED ? BigDecimal.ZERO : baseQuantity;
    }

    /** Số nhập kho (đơn vị cơ sở): dòng đạt hoặc cách ly. */
    public BigDecimal stockedBaseQuantity() {
        return inspectionResult.stocked() ? baseQuantity : BigDecimal.ZERO;
    }

    /** Số đã nhập kho còn chưa nhận hóa đơn, trừ phần đã trả lại khi chưa có hóa đơn (đơn vị cơ sở). */
    public BigDecimal uninvoicedQuantity() {
        return stockedBaseQuantity().subtract(invoicedQuantity).subtract(returnedQuantity)
                .add(returnedInvoicedQuantity).max(BigDecimal.ZERO);
    }

    /** Số đã nhập kho còn có thể trả nhà cung cấp (đơn vị cơ sở). */
    public BigDecimal returnableQuantity() {
        return stockedBaseQuantity().subtract(returnedQuantity).max(BigDecimal.ZERO);
    }

    /**
     * Trả hàng nhà cung cấp ghi sổ (dương) / đảo (âm), BR-PUR-14. {@code invoiced}: phần trả thuộc hàng đã có hóa đơn;
     * phần chưa có hóa đơn không vượt số chưa nhận hóa đơn.
     */
    void addReturned(BigDecimal delta, boolean invoiced, String field) {
        if (delta.signum() > 0) {
            BigDecimal limit = invoiced ? returnableQuantity() : uninvoicedQuantity().min(returnableQuantity());
            if (delta.compareTo(limit) > 0) {
                throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                        + " của phiếu nhận hàng chỉ còn trả được " + limit.stripTrailingZeros().toPlainString()
                        + " (đơn vị cơ sở)" + (invoiced ? "." : " chưa có hóa đơn."));
            }
        }
        returnedQuantity = returnedQuantity.add(delta).max(BigDecimal.ZERO);
        if (invoiced) {
            returnedInvoicedQuantity = returnedInvoicedQuantity.add(delta).max(BigDecimal.ZERO);
        }
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    /** Hóa đơn mua ghi sổ (dương, không vượt số chưa nhận hóa đơn) / đảo (âm). */
    void addInvoiced(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(uninvoicedQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + lineNo
                    + " của phiếu nhận hàng còn " + uninvoicedQuantity().stripTrailingZeros().toPlainString()
                    + " (đơn vị cơ sở) chưa nhận hóa đơn.");
        }
        invoicedQuantity = invoicedQuantity.add(delta).max(BigDecimal.ZERO);
    }

    public BigDecimal getInvoicedQuantity() {
        return invoicedQuantity;
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

    public InspectionResult getInspectionResult() {
        return inspectionResult;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Long getLocationId() {
        return locationId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public LocalDate getManufactureDate() {
        return manufactureDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getNote() {
        return note;
    }

    public List<String> getSerialNumbers() {
        return Collections.unmodifiableList(serialNumbers);
    }
}
