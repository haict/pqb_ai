package com.company.erp.purchasing.domain;

import com.company.erp.common.domain.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đơn mua hàng (DMH, BR-PUR-04): nhà cung cấp, kho nhận, dòng hàng có giá, chiết khấu, thuế. Duyệt xong thì cộng hàng
 * đang về; nhập kho / đóng phần còn lại thì trừ (BR mục 5.4).
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends PurchaseDocument {

    public static final String TYPE_CODE = "DMH";

    /** Header gửi khi lập / sửa. */
    public record Header(LocalDate documentDate, Long supplierId, Long warehouseId, LocalDate expectedDate,
                         Integer paymentTermDays, String currencyCode, BigDecimal exchangeRate, String description) {
    }

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("lineNo")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(Long companyId, Long branchId, String documentNo) {
        super(companyId, branchId, documentNo);
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return totalAmount;
    }

    @Override
    protected boolean fulfilled() {
        return lines.stream().allMatch(line -> line.outstandingQuantity().signum() == 0);
    }

    @Override
    protected boolean hasProgress() {
        return lines.stream().anyMatch(PurchaseOrderLine::hasProgress);
    }

    /** Thay nội dung (chỉ DRAFT / REJECTED); tổng tiền tính lại từ dòng. */
    public void replaceContent(Header header, List<PurchaseOrderLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Đơn mua phải có ít nhất một dòng.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.supplierId = header.supplierId();
        this.warehouseId = header.warehouseId();
        this.expectedDate = header.expectedDate();
        this.paymentTermDays = header.paymentTermDays();
        this.currencyCode = header.currencyCode();
        this.exchangeRate = header.exchangeRate();
        for (int i = 0; i < newLines.size(); i++) {
            if (i < lines.size()) {
                lines.get(i).copyFrom(newLines.get(i));
            } else {
                lines.add(newLines.get(i));
            }
        }
        while (lines.size() > newLines.size()) {
            lines.remove(lines.size() - 1);
        }
        amount = lines.stream().map(PurchaseOrderLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        discountAmount = lines.stream().map(PurchaseOrderLine::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        taxAmount = lines.stream().map(PurchaseOrderLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = amount.subtract(discountAmount).add(taxAmount);
    }

    /** Chiết khấu dòng cao nhất (%) để xét luồng duyệt theo ngưỡng chiết khấu. */
    public BigDecimal maxDiscountPercent() {
        return lines.stream().map(PurchaseOrderLine::getDiscountPercent).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /** Đóng phần chưa nhập kho của mọi dòng (BR-PUR-05). Gọi sau khi đã trừ hàng đang về theo số còn lại. */
    public void close(Long actorId, Instant now, String reason) {
        requireStatus(OPEN, "đóng");
        lines.forEach(PurchaseOrderLine::closeRemaining);
        markClosed(actorId, now, reason);
    }

    public PurchaseOrderLine line(Long lineId) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField("orderLineId", "Dòng không thuộc đơn mua "
                        + getDocumentNo() + "."));
    }

    /** Nhận hàng chỉ theo đơn đã duyệt còn mở. */
    public void requireReceivable() {
        requireStatus(OPEN, "nhận hàng theo");
    }

    /** Phiếu nhận hàng ghi sổ / đảo: số đã nhận, lỗi theo dòng (BR-PUR-05). */
    public void recordReceipt(Long lineId, BigDecimal received, BigDecimal rejected) {
        line(lineId).addReceived(received, rejected);
        refreshProgress();
    }

    /** Nhập kho / đảo nhập kho: số đã nhập; trả thay đổi hàng đang về của dòng. */
    public BigDecimal recordStock(Long lineId, BigDecimal delta) {
        BigDecimal incoming = line(lineId).addStocked(delta, getStatus() == PurchaseDocumentStatus.CLOSED);
        refreshProgress();
        return incoming;
    }

    /** Hóa đơn mua ghi sổ / đảo: số đã nhận hóa đơn của dòng (BR-PUR-05). */
    public void recordInvoice(Long lineId, BigDecimal delta) {
        line(lineId).addInvoiced(delta);
        refreshProgress();
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public Integer getPaymentTermDays() {
        return paymentTermDays;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<PurchaseOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
