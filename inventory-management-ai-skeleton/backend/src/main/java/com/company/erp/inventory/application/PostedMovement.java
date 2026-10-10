package com.company.erp.inventory.application;

import com.company.erp.inventory.domain.MovementDirection;
import com.company.erp.inventory.domain.StockKey;
import java.math.BigDecimal;

/** Biến động đã ghi sổ (sau phân bổ lô / vị trí), tương ứng một dòng sổ nhật ký kho. */
public record PostedMovement(Long transactionId, Long lineId, MovementDirection direction, StockKey key,
                             BigDecimal quantity) {
}
