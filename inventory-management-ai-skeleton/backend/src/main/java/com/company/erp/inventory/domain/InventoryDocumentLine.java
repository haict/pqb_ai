package com.company.erp.inventory.domain;

import com.company.erp.common.domain.QualityStatus;
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
import java.util.List;

/**
 * Dòng chứng từ kho. Lưu số lượng theo đơn vị giao dịch, hệ số tại thời điểm lập và số lượng quy đổi về đơn vị cơ
 * sở (BR-MD-21). Lô: {@code lotId} cho lô đã có, hoặc {@code lotNo} + hạn dùng cho lô nhập mới.
 */
@Entity
@Table(name = "inventory_document_lines")
public class InventoryDocumentLine {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 3)
    private MovementDirection direction;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "destination_location_id")
    private Long destinationLocationId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", length = 30)
    private QualityStatus qualityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_quality_status", length = 30)
    private QualityStatus targetQualityStatus;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "lot_override_reason", length = 500)
    private String lotOverrideReason;

    @Column(name = "note", length = 500)
    private String note;

    @ElementCollection
    @CollectionTable(name = "inventory_document_line_serials", joinColumns = @JoinColumn(name = "line_id"))
    @Column(name = "serial_no", length = 100)
    @OrderBy
    private List<String> serialNumbers = new ArrayList<>();

    protected InventoryDocumentLine() {
    }

    public InventoryDocumentLine(int lineNo, LineValues values) {
        this.lineNo = lineNo;
        this.productId = values.productId();
        this.unitId = values.unitId();
        this.conversionFactor = values.conversionFactor();
        this.quantity = values.quantity();
        this.baseQuantity = values.baseQuantity();
        this.direction = values.direction();
        this.locationId = values.locationId();
        this.destinationLocationId = values.destinationLocationId();
        this.lotId = values.lotId();
        this.lotNo = values.lotNo();
        this.manufactureDate = values.manufactureDate();
        this.expiryDate = values.expiryDate();
        this.qualityStatus = values.qualityStatus();
        this.targetQualityStatus = values.targetQualityStatus();
        this.unitCost = values.unitCost();
        this.amount = values.unitCost() == null ? null : values.unitCost().multiply(values.quantity());
        this.lotOverrideReason = values.lotOverrideReason();
        this.note = values.note();
        this.serialNumbers.addAll(values.serialNumbers());
    }

    /** Giá trị một dòng sau khi đã kiểm tra và quy đổi ở tầng application. */
    public record LineValues(Long productId, Long unitId, BigDecimal conversionFactor, BigDecimal quantity,
                             BigDecimal baseQuantity, MovementDirection direction, Long locationId,
                             Long destinationLocationId, Long lotId, String lotNo, LocalDate manufactureDate,
                             LocalDate expiryDate, QualityStatus qualityStatus, QualityStatus targetQualityStatus,
                             BigDecimal unitCost, String lotOverrideReason, String note, List<String> serialNumbers) {

        public LineValues {
            serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);
        }
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

    public MovementDirection getDirection() {
        return direction;
    }

    public Long getLocationId() {
        return locationId;
    }

    public Long getDestinationLocationId() {
        return destinationLocationId;
    }

    public Long getLotId() {
        return lotId;
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

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public QualityStatus getTargetQualityStatus() {
        return targetQualityStatus;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getLotOverrideReason() {
        return lotOverrideReason;
    }

    public String getNote() {
        return note;
    }

    public List<String> getSerialNumbers() {
        return List.copyOf(serialNumbers);
    }
}
