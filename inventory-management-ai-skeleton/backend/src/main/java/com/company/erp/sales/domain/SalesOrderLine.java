package com.company.erp.sales.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dòng đơn bán (D-54): giá (BR-SAL-02, 03, 35), lô chỉ định (trống: FEFO, BR-SAL-21; khác lô FEFO phải ghi lý do,
 * BR-INV-21), serial chỉ định với hàng quản lý serial. Khi ghi sổ lưu hàng thực xuất (lô, hạn dùng, serial). Sau ghi
 * sổ theo dõi số khách trả lại (đơn vị cơ sở) và giá trị đã giảm (trả hàng + giảm giá, trước thuế, BR-SAL-32, 36). Dòng
 * dịch vụ không qua kho.
 */
@Entity
@Table(name = "sales_order_lines")
public class SalesOrderLine extends SalesPricedLine {

    @Column(name = "service_line", nullable = false)
    private boolean serviceLine;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "lot_no")
    private String lotNo;

    @Column(name = "lot_override_reason")
    private String lotOverrideReason;

    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    @Column(name = "adjusted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal adjustedAmount = BigDecimal.ZERO;

    @ElementCollection
    @CollectionTable(name = "sales_order_serials", joinColumns = @JoinColumn(name = "line_id"))
    @Column(name = "serial_no", length = 100)
    @OrderBy
    private List<String> serialNumbers = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "line_id", nullable = false)
    @OrderBy("id")
    private List<SalesOrderItem> items = new ArrayList<>();

    protected SalesOrderLine() {
    }

    /** Lô / serial chỉ định khi lập đơn; trống thì kho chọn theo FEFO khi ghi sổ. */
    public record Values(PricedValues price, boolean serviceLine, LocalDate expectedDate, String lotNo,
                         String lotOverrideReason, List<String> serialNumbers) {
    }

    public SalesOrderLine(int lineNo, Values values) {
        super(lineNo);
        apply(values);
    }

    private void apply(Values values) {
        applyPrice(values.price());
        this.serviceLine = values.serviceLine();
        this.expectedDate = values.expectedDate();
        this.lotNo = values.lotNo();
        this.lotOverrideReason = values.lotOverrideReason();
        this.serialNumbers.clear();
        this.serialNumbers.addAll(values.serialNumbers());
    }

    void copyFrom(SalesOrderLine other) {
        apply(new Values(other.priceValues(), other.serviceLine, other.expectedDate, other.lotNo,
                other.lotOverrideReason, new ArrayList<>(other.serialNumbers)));
    }

    /** Ghi hàng thực xuất khi ghi sổ (giữ lại sau đảo để truy vết). */
    void replaceItems(List<SalesOrderItem> issued) {
        items.clear();
        items.addAll(issued);
    }

    /** Số còn trả lại được (đơn vị cơ sở). */
    public BigDecimal returnableQuantity() {
        return serviceLine ? BigDecimal.ZERO : getBaseQuantity().subtract(returnedQuantity).max(BigDecimal.ZERO);
    }

    /** Giá trị trước thuế còn giảm được. */
    public BigDecimal remainingAmount() {
        return getAmount().subtract(adjustedAmount).max(BigDecimal.ZERO);
    }

    /** Khách trả lại (dương) / đảo (âm): số lượng và giá trị giảm (BR-SAL-32). */
    void addReturn(BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        if (baseDelta.signum() > 0 && baseDelta.compareTo(returnableQuantity()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + getLineNo()
                    + " của đơn bán chỉ còn trả được " + plain(returnableQuantity()) + " (đơn vị cơ sở).");
        }
        returnedQuantity = returnedQuantity.add(baseDelta).max(BigDecimal.ZERO);
        addAdjusted(amountDelta, field);
    }

    /** Giảm giá hàng bán (dương) / đảo (âm); không vượt giá trị còn lại của dòng (BR-SAL-36). */
    void addAdjusted(BigDecimal delta, String field) {
        if (delta.signum() > 0 && delta.compareTo(remainingAmount()) > 0) {
            throw new BusinessException(ErrorCode.SOURCE_QUANTITY_EXCEEDED, field + ": dòng " + getLineNo()
                    + " của đơn bán chỉ còn giá trị " + plain(remainingAmount()) + ".");
        }
        adjustedAmount = adjustedAmount.add(delta).max(BigDecimal.ZERO);
    }

    /** Đã có trả hàng / giảm giá ghi sổ theo dòng (đảo đơn phải đảo các chứng từ này trước). */
    boolean hasDependents() {
        return returnedQuantity.signum() > 0 || adjustedAmount.signum() > 0;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public boolean isServiceLine() {
        return serviceLine;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public String getLotNo() {
        return lotNo;
    }

    public String getLotOverrideReason() {
        return lotOverrideReason;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public BigDecimal getAdjustedAmount() {
        return adjustedAmount;
    }

    public List<String> getSerialNumbers() {
        return Collections.unmodifiableList(serialNumbers);
    }

    public List<SalesOrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
