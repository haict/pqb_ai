package com.company.erp.inventory.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lệnh ghi sổ kho của một chứng từ.
 *
 * @param referenceType      loại chứng từ nguồn (ví dụ {@code PXK}), ghi vào sổ nhật ký để truy vết
 * @param issuePolicyId      chính sách xuất vượt tồn khả dụng đang hiệu lực (BR-GEN-31), nếu có
 * @param exemptStockCountId phiếu kiểm kê được phép ghi sổ khi kho đang kiểm kê (điều chỉnh sinh từ chính phiếu đó)
 * @param extraLockKeys      (hàng, kho) cần khóa cùng lúc dù chưa có biến động (chuyển kho hai bước trong một
 *                           transaction), để mọi khóa lấy theo một thứ tự
 */
public record PostingCommand(
        Long companyId,
        String referenceType,
        Long referenceId,
        String referenceNo,
        LocalDate postingDate,
        Long partnerId,
        Long actorId,
        List<StockMovement> movements,
        Long issuePolicyId,
        Long exemptStockCountId,
        Set<ProductWarehouse> extraLockKeys) {

    /** Cặp (hàng, kho): đơn vị khóa đầu tiên khi ghi sổ. */
    public record ProductWarehouse(Long productId, Long warehouseId) implements Comparable<ProductWarehouse> {

        @Override
        public int compareTo(ProductWarehouse other) {
            int byProduct = productId.compareTo(other.productId);
            return byProduct != 0 ? byProduct : warehouseId.compareTo(other.warehouseId);
        }
    }

    public PostingCommand {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(referenceType, "referenceType");
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(referenceNo, "referenceNo");
        Objects.requireNonNull(postingDate, "postingDate");
        movements = List.copyOf(movements);
        extraLockKeys = extraLockKeys == null ? Set.of() : Set.copyOf(extraLockKeys);
    }
}
