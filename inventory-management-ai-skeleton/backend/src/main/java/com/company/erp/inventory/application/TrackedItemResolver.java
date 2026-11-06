package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.ItemCondition;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lấy / tạo lô, serial khi ghi sổ. Lô: (hàng, số lô) duy nhất, một hạn dùng (BR-INV-01). Serial: duy nhất theo hàng
 * (BR-INV-02). Tạo bằng câu lệnh bỏ qua khi đã có, nên hai phiếu cùng nhập một lô mới không tạo trùng.
 */
@Component
class TrackedItemResolver {

    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final Clock clock;

    TrackedItemResolver(InventoryLotRepository lotRepository, InventorySerialRepository serialRepository,
                        Clock clock) {
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.clock = clock;
    }

    /**
     * Lô cho dòng nhập: lô đã có theo id, hoặc tạo / lấy theo số lô (kiểm tra cùng hạn dùng). Dòng khai báo tình trạng
     * máy thì lô đã có phải cùng tình trạng (BR-INV-43).
     */
    Long lotForReceipt(Long companyId, ProductInfo product, Long lotId, String lotNo, LocalDate manufactureDate,
                       LocalDate expiryDate, ItemCondition condition, Long actorId, String field) {
        if (!product.lotTracked()) {
            return null;
        }
        if (lotId != null) {
            InventoryLot lot = existingLot(product, lotId, field);
            lot.requireSameCondition(condition, field);
            return lot.getId();
        }
        Instant now = Instant.now(clock);
        lotRepository.insertIfAbsent(companyId, product.id(), lotNo.trim(), manufactureDate, expiryDate,
                initialCondition(condition), actorId, now);
        InventoryLot lot = lotRepository.findByProductIdAndLotNo(product.id(), lotNo.trim())
                .orElseThrow(() -> new IllegalStateException("Không tạo được lô " + lotNo));
        lot.requireSameExpiry(expiryDate, field);
        lot.requireSameCondition(condition, field);
        return lot.getId();
    }

    /** Lô / serial mới không khai báo tình trạng là máy / hàng mới. */
    private static String initialCondition(ItemCondition condition) {
        return (condition == null ? ItemCondition.NEW : condition).name();
    }

    InventoryLot existingLot(ProductInfo product, Long lotId, String field) {
        return lotRepository.findById(lotId)
                .filter(lot -> lot.getProductId().equals(product.id()))
                .orElseThrow(() -> BusinessException.invalidField(field, "Lô không thuộc hàng " + product.code() + "."));
    }

    /**
     * Serial cho dòng nhập: tạo nếu chưa có; serial đã có phải cùng lô (nếu hàng quản lý lô). Tình trạng máy của serial
     * đã có đổi khi ghi sổ dòng nhập (InventoryPostingService), sau khi kiểm tra serial không còn tồn.
     */
    List<Long> serialsForReceipt(Long companyId, ProductInfo product, List<String> serialNumbers, Long lotId,
                                 ItemCondition condition, Long actorId) {
        Instant now = Instant.now(clock);
        for (String serialNo : serialNumbers) {
            serialRepository.insertIfAbsent(companyId, product.id(), serialNo.trim(), lotId,
                    initialCondition(condition), actorId, now);
        }
        return serials(product, serialNumbers);
    }

    /** Serial cho dòng xuất: phải đã có trong hệ thống. */
    List<Long> serialsForIssue(ProductInfo product, List<String> serialNumbers) {
        return serials(product, serialNumbers);
    }

    private List<Long> serials(ProductInfo product, List<String> serialNumbers) {
        List<String> trimmed = serialNumbers.stream().map(String::trim).toList();
        Map<String, InventorySerial> found = serialRepository.findByProductIdAndSerialNoIn(product.id(), trimmed)
                .stream().collect(Collectors.toMap(InventorySerial::getSerialNo, Function.identity()));
        List<Long> ids = new ArrayList<>();
        for (String serialNo : trimmed) {
            InventorySerial serial = found.get(serialNo);
            if (serial == null) {
                throw new BusinessException(ErrorCode.SERIAL_UNAVAILABLE,
                        "Serial " + serialNo + " của hàng " + product.code() + " không có trong kho.");
            }
            ids.add(serial.getId());
        }
        return ids;
    }
}
