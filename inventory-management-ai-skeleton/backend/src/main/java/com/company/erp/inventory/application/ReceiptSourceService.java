package com.company.erp.inventory.application;

import com.company.erp.common.application.BusinessCalendar;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.ReceiptSourceJdbc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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

    /** Lô của một hàng (số lô là duy nhất theo hàng). */
    public record LotKey(Long productId, String lotNo) {
    }

    private final ReceiptSourceJdbc jdbc;
    private final InventoryLotRepository lotRepository;
    private final BusinessCalendar calendar;

    public ReceiptSourceService(ReceiptSourceJdbc jdbc, InventoryLotRepository lotRepository,
                                BusinessCalendar calendar) {
        this.jdbc = jdbc;
        this.lotRepository = lotRepository;
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

    /** Hạn dùng của các lô đã có trong kho; lô chưa tồn tại hoặc không có hạn dùng không có trong kết quả. */
    @Transactional(readOnly = true)
    public Map<LotKey, LocalDate> expiryDates(Collection<LotKey> lots) {
        if (lots.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = lots.stream().map(LotKey::productId).distinct().toList();
        List<String> lotNos = lots.stream().map(LotKey::lotNo).distinct().toList();
        return lotRepository.findByProductIdInAndLotNoIn(productIds, lotNos).stream()
                .filter(lot -> lot.getExpiryDate() != null)
                .map(lot -> Map.entry(new LotKey(lot.getProductId(), lot.getLotNo()), lot.getExpiryDate()))
                .filter(entry -> lots.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
