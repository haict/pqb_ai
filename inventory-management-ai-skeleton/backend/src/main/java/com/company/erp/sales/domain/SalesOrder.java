package com.company.erp.sales.domain;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Đơn bán hàng (DBH, BR-SAL-10..14, D-54): chứng từ ghi sổ duy nhất của nghiệp vụ bán, không qua duyệt (D-61). Giá
 * dưới sàn, chiết khấu vượt ngưỡng, vượt hạn mức tín dụng hoặc có nợ quá hạn thì chỉ người có quyền vượt mới ghi sổ
 * được (BR-SAL-03, BR-SAL-14, D-22). Giữ hàng ngay trên đơn nháp nếu chọn (BR-SAL-13, D-37). Ghi sổ: xuất kho (lô FEFO / chỉ định), doanh thu, công nợ phải
 * thu, bút toán; số hóa đơn điện tử ghi sau khi phát hành trên MISA (BR-SAL-31). Theo dõi giao hàng sau ghi sổ (đang
 * giao, khách nhận); giao thất bại thì đảo đơn.
 */
@Entity
@Table(name = "sales_orders")
public class SalesOrder extends SalesDocument {

    public static final String TYPE_CODE = "DBH";

    private static final Set<SalesDocumentStatus> DELETABLE = EnumSet.of(SalesDocumentStatus.DRAFT,
            SalesDocumentStatus.REJECTED, SalesDocumentStatus.SUBMITTED, SalesDocumentStatus.APPROVED);

    public record Header(LocalDate documentDate, LocalDate postingDate, String contactName, String deliveryAddress,
                         String receiverName, String receiverPhone, LocalDate expectedDate, Integer paymentTermDays,
                         boolean reserveStock, String description) {
    }

    /** Đợt thu tiền khai báo khi lập (số tiền tính theo tỷ lệ của tổng đơn). */
    public record PaymentTermValues(BigDecimal percent, PaymentDueBasis dueBasis, int dueDays, String description) {
    }

    /** Tham chiếu hóa đơn điện tử đã phát hành trên MISA (BR-SAL-31). */
    public record EinvoiceReference(String series, String number, LocalDate date, String taxAuthorityCode) {
    }

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_phone")
    private String receiverPhone;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "reserve_stock", nullable = false)
    private boolean reserveStock;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "source_return_id", updatable = false)
    private Long sourceReturnId;

    @Column(name = "currency_code", nullable = false, updatable = false)
    private String currencyCode = "VND";

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "approval_reasons")
    private String approvalReasons;

    @Column(name = "einvoice_series")
    private String einvoiceSeries;

    @Column(name = "einvoice_no")
    private String einvoiceNo;

    @Column(name = "einvoice_date")
    private LocalDate einvoiceDate;

    @Column(name = "tax_authority_code")
    private String taxAuthorityCode;

    @Column(name = "einvoice_key")
    private String einvoiceKey;

    @Column(name = "inventory_document_id")
    private Long inventoryDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "shipper_name")
    private String shipperName;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "signed_document_ref")
    private String signedDocumentRef;

    @Column(name = "delivery_note")
    private String deliveryNote;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    /** Đã từng ghi sổ (giữ nguyên khi bỏ ghi sổ, D-62): còn sổ phụ tham chiếu nên không xóa được (D-65). */
    @Column(name = "ever_posted", nullable = false)
    private boolean everPosted;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_date")
    private LocalDate reverseDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("lineNo")
    private List<SalesOrderLine> lines = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("installmentNo")
    private List<SalesOrderPaymentTerm> paymentTerms = new ArrayList<>();

    protected SalesOrder() {
    }

    public SalesOrder(Long companyId, Long branchId, String documentNo, Long customerId, Long sourceReturnId) {
        super(companyId, branchId, documentNo);
        this.customerId = customerId;
        this.sourceReturnId = sourceReturnId;
    }

    /** Khóa chống trùng số hóa đơn điện tử: ký hiệu|số viết hoa, bỏ khoảng trắng hai đầu. */
    public static String einvoiceKey(String series, String number) {
        return (series.trim() + "|" + number.trim()).toUpperCase(Locale.ROOT);
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    public void replaceContent(Header header, Long warehouseId, List<SalesOrderLine> newLines,
                               List<PaymentTermValues> terms) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Đơn bán phải có ít nhất một dòng.");
        }
        if (header.postingDate().isBefore(header.documentDate())) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.postingDate = header.postingDate();
        this.contactName = header.contactName();
        this.deliveryAddress = header.deliveryAddress();
        this.receiverName = header.receiverName();
        this.receiverPhone = header.receiverPhone();
        this.expectedDate = header.expectedDate();
        this.paymentTermDays = header.paymentTermDays();
        this.reserveStock = header.reserveStock();
        this.warehouseId = warehouseId;
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
        amount = sum(lines.stream().map(SalesOrderLine::getAmount).toList());
        discountAmount = sum(lines.stream().map(SalesOrderLine::getDiscountAmount).toList());
        taxAmount = sum(lines.stream().map(SalesOrderLine::getTaxAmount).toList());
        totalAmount = amount.add(taxAmount);
        replaceTerms(terms);
    }

    /** Lịch thu tiền: tổng tỷ lệ 100%; số tiền đợt cuối nhận phần dư làm tròn (BR-SAL-50). */
    private void replaceTerms(List<PaymentTermValues> terms) {
        if (!terms.isEmpty()) {
            BigDecimal percent = terms.stream().map(PaymentTermValues::percent).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            if (percent.compareTo(BigDecimal.valueOf(100)) != 0) {
                throw BusinessException.invalidField("paymentTerms", "Tổng tỷ lệ các đợt thu tiền phải bằng 100%.");
            }
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < terms.size(); i++) {
            PaymentTermValues term = terms.get(i);
            BigDecimal value = i == terms.size() - 1 ? totalAmount.subtract(allocated)
                    : totalAmount.multiply(term.percent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            allocated = allocated.add(value);
            if (i < paymentTerms.size()) {
                paymentTerms.get(i).apply(term.percent(), value, term.dueBasis(), term.dueDays(), term.description());
            } else {
                paymentTerms.add(new SalesOrderPaymentTerm(i + 1, term.percent(), value, term.dueBasis(),
                        term.dueDays(), term.description()));
            }
        }
        while (paymentTerms.size() > terms.size()) {
            paymentTerms.remove(paymentTerms.size() - 1);
        }
    }

    /**
     * Lý do cần thẩm quyền khi ghi sổ (giá dưới sàn, chiết khấu vượt ngưỡng, vượt hạn mức tín dụng / nợ quá hạn; D-61
     * thay bước duyệt). Cột giữ tên {@code approval_reasons} để tương thích API.
     */
    public void recordApprovalReasons(List<String> reasons) {
        this.approvalReasons = reasons.isEmpty() ? null : String.join(" ", reasons);
    }

    public void markReserved(Instant until) {
        this.reservedUntil = until;
    }

    /** Đơn chưa ghi sổ, chưa hủy (giữ hàng từ nháp, D-61; đơn đã duyệt trước D-61). */
    public void requirePostable() {
        if (!POSTABLE.contains(getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " đã ghi sổ, hủy hoặc đang ở trạng thái chờ duyệt cũ.");
        }
    }

    /** Xóa hẳn đơn (D-65, thay cho hủy): chỉ đơn chưa ghi sổ và chưa từng ghi sổ. */
    public void requireDeletable() {
        requireStatus(DELETABLE, "xóa");
        if (everPosted) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " đã từng ghi sổ (còn phiếu kho, công nợ, bút toán tham chiếu), không xóa được.");
        }
    }

    /** Ghi số hóa đơn điện tử từ MISA; được cập nhật cả sau khi ghi sổ (chưa đảo). */
    public void recordEinvoice(EinvoiceReference reference) {
        if (getStatus() == SalesDocumentStatus.REVERSED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " đã đảo.");
        }
        this.einvoiceSeries = reference.series().trim();
        this.einvoiceNo = reference.number().trim();
        this.einvoiceDate = reference.date();
        this.taxAuthorityCode = reference.taxAuthorityCode();
        this.einvoiceKey = einvoiceKey(einvoiceSeries, einvoiceNo);
    }

    /**
     * Ghi sổ đơn (từ nháp, hoặc đã duyệt trước D-61): phiếu xuất kho, hàng thực xuất theo dòng ({@code issued} theo
     * id dòng), hạn thanh toán = ngày hạch toán + số ngày nợ.
     */
    public void markPosted(Long inventoryDocumentId, Map<Long, List<SalesOrderItem>> issued, Long actorId,
                           Instant now) {
        requireStatus(POSTABLE, "ghi sổ");
        setStatus(SalesDocumentStatus.POSTED);
        this.inventoryDocumentId = inventoryDocumentId;
        this.dueDate = postingDate.plusDays(paymentTermDays == null ? 0 : paymentTermDays);
        this.postedBy = actorId;
        this.postedAt = now;
        this.everPosted = true;
        this.reservedUntil = null;
        lines.forEach(line -> line.replaceItems(issued.getOrDefault(line.getId(), List.of())));
    }

    /** Đang giao: chỉ đơn đã ghi sổ (đã xuất kho), chưa giao (BR-SAL-22). */
    public void markShipped(String shipperName, Instant now) {
        requirePostedForDelivery();
        if (deliveryStatus != DeliveryStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " đã chuyển đi giao hoặc khách đã nhận.");
        }
        this.deliveryStatus = DeliveryStatus.IN_TRANSIT;
        this.shipperName = shipperName;
        this.shippedAt = now;
    }

    /** Khách nhận hàng: ngày nhận, người nhận, số biên bản đã ký (BR-SAL-22). */
    public void markDelivered(LocalDate receivedDate, String receivedBy, String signedDocumentRef, String note) {
        requirePostedForDelivery();
        if (deliveryStatus == DeliveryStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " đã ghi nhận khách nhận hàng.");
        }
        if (receivedDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("receivedDate", "Ngày khách nhận không được trước ngày xuất kho.");
        }
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.receivedDate = receivedDate;
        this.receivedBy = receivedBy;
        this.signedDocumentRef = signedDocumentRef;
        this.deliveryNote = note;
    }

    private void requirePostedForDelivery() {
        if (getStatus() != SalesDocumentStatus.POSTED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " chưa ghi sổ (chưa xuất kho) hoặc đã đảo.");
        }
    }

    /**
     * Đảo (lập sai hoặc giao thất bại, BR-SAL-23): chặn khi còn trả hàng / giảm giá chưa đảo (BR-GEN-04); giải phóng số
     * hóa đơn điện tử.
     */
    public void markReversed(Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.POSTED), "đảo");
        requireNoDependents();
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày ghi sổ chứng từ gốc.");
        }
        setStatus(SalesDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        this.einvoiceKey = null;
        setReason(reason);
    }

    /**
     * Bỏ ghi sổ về nháp, giữ số đơn (D-62); sổ phụ đảo ở tầng application. Chặn khi còn trả hàng / giảm giá chưa đảo,
     * đã có số hóa đơn điện tử (hóa đơn đã phát hành xử lý bằng đảo đơn) hoặc đã chuyển đi giao.
     */
    public void markUnposted(String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.POSTED), "bỏ ghi sổ");
        requireNoDependents();
        if (einvoiceNo != null) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " đã có hóa đơn điện tử " + invoiceLabel() + ", không bỏ ghi sổ được; dùng đảo đơn.");
        }
        if (deliveryStatus != DeliveryStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Đơn bán " + getDocumentNo()
                    + " đã chuyển đi giao hoặc khách đã nhận, không bỏ ghi sổ được.");
        }
        setStatus(SalesDocumentStatus.DRAFT);
        this.inventoryDocumentId = null;
        this.postedBy = null;
        this.postedAt = null;
        lines.forEach(line -> line.replaceItems(List.of()));
        setReason(reason);
    }

    private void requireNoDependents() {
        if (lines.stream().anyMatch(SalesOrderLine::hasDependents)) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Đơn bán " + getDocumentNo()
                    + " đã có trả hàng / giảm giá ghi sổ; đảo các chứng từ đó trước.");
        }
    }

    public SalesOrderLine line(Long lineId, String field) {
        return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "Dòng không thuộc đơn bán "
                        + getDocumentNo() + "."));
    }

    /** Trả hàng theo dòng (đơn phải đang ghi sổ). */
    public void recordReturn(Long lineId, BigDecimal baseDelta, BigDecimal amountDelta, String field) {
        requirePostedFor(baseDelta.signum() > 0 || amountDelta.signum() > 0, field);
        line(lineId, field).addReturn(baseDelta, amountDelta, field);
    }

    /** Giảm giá hàng bán theo dòng. */
    public void recordDiscount(Long lineId, BigDecimal delta, String field) {
        requirePostedFor(delta.signum() > 0, field);
        line(lineId, field).addAdjusted(delta, field);
    }

    private void requirePostedFor(boolean increase, String field) {
        if (increase && getStatus() != SalesDocumentStatus.POSTED) {
            throw BusinessException.invalidField(field, "Đơn bán " + getDocumentNo() + " chưa ghi sổ hoặc đã đảo.");
        }
    }

    /** Dòng hàng hóa (giữ hàng, xuất kho). */
    public List<SalesOrderLine> goodsLines() {
        return lines.stream().filter(line -> !line.isServiceLine()).toList();
    }

    /** Hiển thị trên công nợ: số HĐĐT nếu đã có, không thì số đơn bán. */
    public String invoiceLabel() {
        return einvoiceNo != null ? einvoiceSeries + "-" + einvoiceNo : getDocumentNo();
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public boolean isReserveStock() {
        return reserveStock;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public Long getSourceReturnId() {
        return sourceReturnId;
    }

    public String getCurrencyCode() {
        return currencyCode;
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

    public String getApprovalReasons() {
        return approvalReasons;
    }

    public String getEinvoiceSeries() {
        return einvoiceSeries;
    }

    public String getEinvoiceNo() {
        return einvoiceNo;
    }

    public LocalDate getEinvoiceDate() {
        return einvoiceDate;
    }

    public String getTaxAuthorityCode() {
        return taxAuthorityCode;
    }

    public String getEinvoiceKey() {
        return einvoiceKey;
    }

    public Long getInventoryDocumentId() {
        return inventoryDocumentId;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getShipperName() {
        return shipperName;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public String getSignedDocumentRef() {
        return signedDocumentRef;
    }

    public String getDeliveryNote() {
        return deliveryNote;
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

    public List<SalesOrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<SalesOrderPaymentTerm> getPaymentTerms() {
        return Collections.unmodifiableList(paymentTerms);
    }
}
