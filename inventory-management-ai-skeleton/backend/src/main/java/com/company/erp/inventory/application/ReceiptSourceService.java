package com.company.erp.inventory.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.inventory.infrastructure.persistence.ReceiptSourceJdbc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai cho module khác: chứng từ nhập (dòng sổ nhật ký kho chiều nhập theo lô / serial) còn tồn để chọn xuất
 * đích danh (D-63, như F8 của MISA). Chỉ đọc; xuất kho vẫn theo lô / serial chỉ định khi ghi sổ.
 */
@Service
public class ReceiptSourceService {

    /** Tối đa số dòng trả về một lần (đủ cho một hàng tại một kho; nhiều hơn thì người dùng lọc lô). */
    static final int LIMIT = 500;

    /** Dòng nhập còn tồn; số lượng theo đơn vị cơ sở, {@code availableQuantity} là tồn ĐẠT của lô / serial tại kho. */
    public record ReceiptSource(Long transactionId, String documentType, Long documentId, String documentNo,
                                LocalDate postingDate, String lotNo, LocalDate expiryDate, String serialNo,
                                BigDecimal receivedQuantity, BigDecimal unitCost, BigDecimal availableQuantity) {
    }

    /** Dòng nhập để kiểm tra liên kết từ chứng từ xuất. */
    public record ReceiptRef(Long transactionId, Long companyId, Long warehouseId, Long productId, String documentNo,
                             String lotNo, String serialNo, boolean usable) {
    }

    private final ReceiptSourceJdbc jdbc;
    private final BusinessCalendar calendar;

    public ReceiptSourceService(ReceiptSourceJdbc jdbc, BusinessCalendar calendar) {
        this.jdbc = jdbc;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public List<ReceiptSource> available(Long companyId, Long warehouseId, Long productId) {
        return jdbc.available(companyId, warehouseId, productId, calendar.today(), LIMIT).stream()
                .map(row -> new ReceiptSource(row.transactionId(), row.referenceType(), row.referenceId(),
                        row.referenceNo(), row.postingDate(), row.lotNo(), row.expiryDate(), row.serialNo(),
                        row.receivedQuantity(), row.unitCost(), row.availableQuantity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReceiptRef> find(Long transactionId) {
        return jdbc.find(transactionId).map(key -> new ReceiptRef(key.transactionId(), key.companyId(),
                key.warehouseId(), key.productId(), key.referenceNo(), key.lotNo(), key.serialNo(), key.usable()));
    }
}
