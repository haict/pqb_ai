package com.company.erp.inventory.application;

import com.company.erp.inventory.infrastructure.persistence.StockLedgerJdbc;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hàng đang về (BR mục 5.4): Σ số đặt trên đơn mua đã duyệt − Σ đã nhập kho − phần đã đóng, lưu ở
 * {@code inventory_stock_summaries.incoming_quantity}. API công khai cho phân hệ mua hàng gọi trong transaction của
 * chứng từ nguồn (duyệt, hủy, đóng đơn mua; ghi sổ, đảo phiếu nhập). Không đổi tồn thực tế.
 */
@Service
public class IncomingStockService {

    /** Thay đổi hàng đang về theo đơn vị cơ sở; âm khi giảm. */
    public record IncomingChange(Long productId, Long warehouseId, BigDecimal quantity) {
    }

    private record Key(Long productId, Long warehouseId) {
    }

    private static final Comparator<Key> LOCK_ORDER =
            Comparator.comparing(Key::productId).thenComparing(Key::warehouseId);

    private final StockLedgerJdbc ledger;
    private final Clock clock;

    public IncomingStockService(StockLedgerJdbc ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    /**
     * Cộng dồn theo (hàng, kho), khóa dòng tổng theo thứ tự (hàng, kho) tăng dần như ghi sổ kho. Giảm quá số đang về
     * là lỗi dữ liệu (rollback cả chứng từ nguồn).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Long companyId, List<IncomingChange> changes) {
        Map<Key, BigDecimal> totals = new TreeMap<>(LOCK_ORDER);
        for (IncomingChange change : changes) {
            totals.merge(new Key(change.productId(), change.warehouseId()), change.quantity(), BigDecimal::add);
        }
        Instant now = Instant.now(clock);
        totals.forEach((key, delta) -> {
            if (delta.signum() == 0) {
                return;
            }
            ledger.lockSummary(companyId, key.productId(), key.warehouseId(), now);
            if (!ledger.addIncoming(key.productId(), key.warehouseId(), delta, now)) {
                throw new IllegalStateException("Hàng đang về (hàng " + key.productId() + ", kho "
                        + key.warehouseId() + ") sẽ âm");
            }
        });
    }
}
