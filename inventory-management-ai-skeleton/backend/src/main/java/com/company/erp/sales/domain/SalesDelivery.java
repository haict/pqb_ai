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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Phiếu giao hàng theo đơn bán (GH, BR-SAL-20..23): ghi sổ là xuất kho (phiếu xuất PXK có nguồn ở module kho, FEFO,
 * dùng phần hàng đã giữ cho đơn); sau đó theo dõi giao hàng: đang giao → khách nhận (ngày nhận, người nhận, số biên
 * bản đã ký) hoặc giao thất bại (hàng quay về kho bằng chứng từ đảo phiếu xuất). Xuất kho và giao hàng là hai sự kiện
 * riêng (BR mục 10.1).
 */
@Entity
@Table(name = "sales_deliveries")
public class SalesDelivery extends SalesDocument {

    public static final String TYPE_CODE = "GH";

    public record Header(LocalDate documentDate, LocalDate postingDate, String deliveryAddress, String receiverName,
                         String receiverPhone, String shipperName, String description) {
    }

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_phone")
    private String receiverPhone;

    @Column(name = "shipper_name")
    private String shipperName;

    @Column(name = "source_return_id", updatable = false)
    private Long sourceReturnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "inventory_document_id")
    private Long inventoryDocumentId;

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

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_date")
    private LocalDate reverseDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "delivery_id", nullable = false)
    @OrderBy("lineNo")
    private List<SalesDeliveryLine> lines = new ArrayList<>();

    protected SalesDelivery() {
    }

    public SalesDelivery(Long companyId, Long branchId, String documentNo, Long orderId, Long customerId,
                         Long warehouseId, Long sourceReturnId) {
        super(companyId, branchId, documentNo);
        this.orderId = orderId;
        this.customerId = customerId;
        this.warehouseId = warehouseId;
        this.sourceReturnId = sourceReturnId;
    }

    @Override
    public String typeCode() {
        return TYPE_CODE;
    }

    @Override
    public BigDecimal approvalAmount() {
        return null;
    }

    @Override
    protected boolean fulfilled() {
        return false;
    }

    @Override
    protected boolean hasProgress() {
        return false;
    }

    public void replaceContent(Header header, List<SalesDeliveryLine> newLines) {
        if (newLines.isEmpty()) {
            throw BusinessException.invalidField("lines", "Phiếu giao hàng phải có ít nhất một dòng.");
        }
        if (header.postingDate().isBefore(header.documentDate())) {
            throw BusinessException.invalidField("postingDate", "Ngày hạch toán không được trước ngày chứng từ.");
        }
        replaceHeader(header.documentDate(), header.description());
        this.postingDate = header.postingDate();
        this.deliveryAddress = header.deliveryAddress();
        this.receiverName = header.receiverName();
        this.receiverPhone = header.receiverPhone();
        this.shipperName = header.shipperName();
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
    }

    /** Xuất kho: ghi hàng thực xuất theo dòng, chờ giao. */
    public void markIssued(Long inventoryDocumentId, Map<Long, List<SalesDeliveryItem>> issued, Long actorId,
                           Instant now) {
        requireStatus(EnumSet.of(SalesDocumentStatus.APPROVED), "xuất kho");
        lines.forEach(line -> line.replaceItems(issued.getOrDefault(line.getId(), List.of())));
        this.inventoryDocumentId = inventoryDocumentId;
        this.deliveryStatus = DeliveryStatus.PENDING;
        setStatus(SalesDocumentStatus.POSTED);
        this.postedBy = actorId;
        this.postedAt = now;
    }

    public void markShipped(String shipperName, Instant now) {
        requireDelivery(DeliveryStatus.PENDING, "chuyển sang đang giao");
        this.deliveryStatus = DeliveryStatus.IN_TRANSIT;
        if (shipperName != null) {
            this.shipperName = shipperName;
        }
        this.shippedAt = now;
    }

    /** Khách nhận hàng: ngày nhận, người nhận, số biên bản giao nhận đã ký (BR-SAL-22). */
    public void markDelivered(LocalDate receivedDate, String receivedBy, String signedDocumentRef, String note) {
        if (deliveryStatus != DeliveryStatus.PENDING && deliveryStatus != DeliveryStatus.IN_TRANSIT) {
            throw invalidDelivery("xác nhận khách nhận");
        }
        requireStatus(EnumSet.of(SalesDocumentStatus.POSTED), "xác nhận khách nhận");
        if (receivedDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("receivedDate", "Ngày khách nhận không được trước ngày xuất kho.");
        }
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.receivedDate = receivedDate;
        this.receivedBy = receivedBy;
        this.signedDocumentRef = signedDocumentRef;
        this.deliveryNote = note;
    }

    /**
     * Đảo xuất kho: giao thất bại (hàng quay về kho, BR-SAL-23) hoặc lập sai; không được khi đã xuất hóa đơn / trả hàng
     * hoặc khách đã nhận.
     */
    public void markReversed(boolean failed, Long actorId, Instant now, LocalDate reverseDate, String reason) {
        requireStatus(EnumSet.of(SalesDocumentStatus.POSTED), failed ? "ghi nhận giao thất bại" : "đảo");
        if (deliveryStatus == DeliveryStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + getDocumentNo()
                    + " khách đã nhận hàng; dùng trả hàng bán.");
        }
        if (lines.stream().anyMatch(line -> line.getInvoicedQuantity().signum() > 0
                || line.getReturnedQuantity().signum() > 0)) {
            throw new BusinessException(ErrorCode.HAS_DEPENDENT_DOCUMENTS, "Phiếu " + getDocumentNo()
                    + " đã có hóa đơn bán / trả hàng; đảo các chứng từ đó trước.");
        }
        if (reverseDate.isBefore(postingDate)) {
            throw BusinessException.invalidField("postingDate", "Ngày đảo không được trước ngày xuất kho.");
        }
        if (failed) {
            this.deliveryStatus = DeliveryStatus.FAILED;
        }
        setStatus(SalesDocumentStatus.REVERSED);
        this.reversedBy = actorId;
        this.reversedAt = now;
        this.reverseDate = reverseDate;
        setReason(reason);
    }

    public SalesDeliveryLine line(Long lineId, String field) {
        return lines.stream().filter(candidate -> candidate.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> BusinessException.invalidField(field, "Dòng không thuộc phiếu " + getDocumentNo()));
    }

    /** Hóa đơn bán theo dòng (phiếu phải đã xuất kho, chưa đảo). */
    public void recordInvoice(Long lineId, BigDecimal delta, String field) {
        if (delta.signum() > 0 && getStatus() != SalesDocumentStatus.POSTED) {
            throw BusinessException.invalidField(field, "Phiếu giao " + getDocumentNo() + " chưa xuất kho hoặc đã đảo.");
        }
        line(lineId, field).addInvoiced(delta, field);
    }

    /** Trả hàng bán theo dòng (BR-SAL-32). */
    public void recordReturn(Long lineId, BigDecimal delta, boolean invoiced, String field) {
        if (delta.signum() > 0 && getStatus() != SalesDocumentStatus.POSTED) {
            throw BusinessException.invalidField(field, "Phiếu giao " + getDocumentNo() + " chưa xuất kho hoặc đã đảo.");
        }
        line(lineId, field).addReturned(delta, invoiced, field);
    }

    private void requireDelivery(DeliveryStatus expected, String action) {
        requireStatus(EnumSet.of(SalesDocumentStatus.POSTED), action);
        if (deliveryStatus != expected) {
            throw invalidDelivery(action);
        }
    }

    private BusinessException invalidDelivery(String action) {
        return new BusinessException(ErrorCode.INVALID_DOCUMENT_STATE, "Phiếu " + getDocumentNo()
                + " đang ở trạng thái giao " + deliveryStatus + ", không " + action + " được.");
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getWarehouseId() {
        return warehouseId;
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

    public String getShipperName() {
        return shipperName;
    }

    public Long getSourceReturnId() {
        return sourceReturnId;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Long getInventoryDocumentId() {
        return inventoryDocumentId;
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

    public List<SalesDeliveryLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
