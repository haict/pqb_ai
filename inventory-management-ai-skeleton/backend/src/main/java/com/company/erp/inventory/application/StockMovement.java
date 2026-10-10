package com.company.erp.inventory.application;

import com.company.erp.common.domain.QualityStatus;
import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.domain.TransactionType;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Một biến động cần ghi sổ, theo đơn vị cơ sở. Với chiều xuất, {@code locationId}, {@code lotId}, {@code serialId}
 * có thể để trống: dịch vụ ghi sổ phân bổ theo FEFO (hạn dùng sớm nhất trước, BR-INV-21) trên các dòng tồn thỏa
 * {@code outRule}. Với chiều nhập, khóa tồn phải đầy đủ.
 *
 * @param lineId       dòng chứng từ nguồn
 * @param unitCost     đơn giá nhập (chiều nhập); giá xuất do tính giá vốn (Giai đoạn 4)
 * @param reversalOfId dòng sổ nhật ký gốc khi đây là dòng đảo
 * @param lotChoiceJustified đã ghi lý do khi chỉ định lô khác lô FEFO đề xuất (BR-INV-21)
 */
public record StockMovement(
        Long lineId,
        MovementDirection direction,
        TransactionType type,
        Long productId,
        Long warehouseId,
        Long locationId,
        Long lotId,
        Long serialId,
        QualityStatus qualityStatus,
        BigDecimal quantity,
        BigDecimal unitCost,
        OutRule outRule,
        Long reversalOfId,
        boolean lotChoiceJustified) {

    /** Điều kiện của tồn được xuất. */
    public enum OutRule {
        /** Xuất bán / nội bộ: chỉ ĐẠT, chưa hết hạn, không khóa; kiểm tra tồn khả dụng (BR-INV-20, BR-INV-23). */
        SELLABLE,
        /** Xuất hủy: CÁCH LY, KHÔNG ĐẠT, HẾT HẠN, THU HỒI, hoặc lô đã quá hạn (BR-INV-23, BR-INV-33). */
        DISPOSABLE,
        /** Điều chỉnh, chuyển trạng thái, đảo: đúng trạng thái đã chỉ định, chỉ kiểm tra không âm. */
        EXACT_STATUS
    }

    public StockMovement {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Số lượng biến động phải dương");
        }
        if (direction == MovementDirection.IN && (locationId == null || qualityStatus == null)) {
            throw new IllegalArgumentException("Biến động nhập phải có vị trí và trạng thái chất lượng");
        }
        if (direction == MovementDirection.OUT && outRule == null) {
            throw new IllegalArgumentException("Biến động xuất phải có quy tắc chọn tồn");
        }
    }
}
