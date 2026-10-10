package com.company.erp.inventory.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.inventory.application.dto.ReasonRequest;
import com.company.erp.inventory.domain.InventoryLot;
import com.company.erp.inventory.domain.InventorySerial;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.system.application.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khóa / mở khóa lô, serial (BR-INV-23): bị khóa thì không xuất bán / sử dụng. Lý do bắt buộc, ghi audit. Không
 * đổi tồn thực tế; tồn bán được và tồn khả dụng giảm theo.
 */
@Service
public class LotSerialBlockService {

    private static final String PERMISSION = "lot:block";

    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final InventoryAccess access;

    public LotSerialBlockService(InventoryLotRepository lotRepository, InventorySerialRepository serialRepository,
                                 InventoryAccess access) {
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.access = access;
    }

    @Transactional
    public void blockLot(CurrentUser actor, Long lotId, ReasonRequest request) {
        InventoryLot lot = lot(actor, lotId);
        lot.block(request.reason().trim());
        lotRepository.saveAndFlush(lot);
        access.audit(actor, "BLOCK", InventoryAccess.ENTITY_LOT, lot.getId(), lot.getLotNo(), request.reason(), null,
                null, lot.getCompanyId(), null);
    }

    @Transactional
    public void unblockLot(CurrentUser actor, Long lotId, ReasonRequest request) {
        InventoryLot lot = lot(actor, lotId);
        lot.unblock();
        lotRepository.saveAndFlush(lot);
        access.audit(actor, "UNBLOCK", InventoryAccess.ENTITY_LOT, lot.getId(), lot.getLotNo(), request.reason(),
                null, null, lot.getCompanyId(), null);
    }

    @Transactional
    public void blockSerial(CurrentUser actor, Long serialId, ReasonRequest request) {
        InventorySerial serial = serial(actor, serialId);
        serial.block(request.reason().trim());
        serialRepository.saveAndFlush(serial);
        access.audit(actor, "BLOCK", InventoryAccess.ENTITY_SERIAL, serial.getId(), serial.getSerialNo(),
                request.reason(), null, null, serial.getCompanyId(), null);
    }

    @Transactional
    public void unblockSerial(CurrentUser actor, Long serialId, ReasonRequest request) {
        InventorySerial serial = serial(actor, serialId);
        serial.unblock();
        serialRepository.saveAndFlush(serial);
        access.audit(actor, "UNBLOCK", InventoryAccess.ENTITY_SERIAL, serial.getId(), serial.getSerialNo(),
                request.reason(), null, null, serial.getCompanyId(), null);
    }

    private InventoryLot lot(CurrentUser actor, Long lotId) {
        access.requirePermission(actor, PERMISSION);
        return lotRepository.findById(lotId)
                .filter(lot -> actor.dataScope().canAccessCompany(lot.getCompanyId()))
                .orElseThrow(BusinessException::notFound);
    }

    private InventorySerial serial(CurrentUser actor, Long serialId) {
        access.requirePermission(actor, PERMISSION);
        return serialRepository.findById(serialId)
                .filter(serial -> actor.dataScope().canAccessCompany(serial.getCompanyId()))
                .orElseThrow(BusinessException::notFound);
    }
}
