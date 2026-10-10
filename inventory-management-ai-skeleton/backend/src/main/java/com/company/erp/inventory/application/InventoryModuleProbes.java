package com.company.erp.inventory.application;

import com.company.erp.common.domain.PeriodModule;
import com.company.erp.inventory.domain.InventoryDocumentStatus;
import com.company.erp.inventory.infrastructure.persistence.InventoryDocumentRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryLotRepository;
import com.company.erp.inventory.infrastructure.persistence.InventorySerialRepository;
import com.company.erp.inventory.infrastructure.persistence.InventoryTransactionRepository;
import com.company.erp.masterdata.application.ProductUsageProbe;
import com.company.erp.masterdata.application.TrackedItemDirectory;
import com.company.erp.system.application.PeriodCloseCheck;
import com.company.erp.system.application.PeriodCloseContext;
import com.company.erp.system.application.PostingActivityProbe;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Câu trả lời của module kho cho module danh mục và hệ thống (đảo phụ thuộc, architecture.md mục 3): hàng đã phát
 * sinh giao dịch (BR-MD-04), lô / serial tồn tại (BR-INV-05), công ty đã có số liệu ghi sổ (BR-SYS-13), điều kiện
 * khóa kỳ kho: không còn chứng từ đã duyệt / đang vận chuyển chưa ghi sổ trong kỳ (BR-SYS-31).
 */
@Component
class InventoryModuleProbes implements ProductUsageProbe, TrackedItemDirectory, PostingActivityProbe,
        PeriodCloseCheck {

    private static final Set<InventoryDocumentStatus> UNFINISHED =
            EnumSet.of(InventoryDocumentStatus.APPROVED, InventoryDocumentStatus.IN_TRANSIT);

    private final InventoryTransactionRepository transactionRepository;
    private final InventoryLotRepository lotRepository;
    private final InventorySerialRepository serialRepository;
    private final InventoryDocumentRepository documentRepository;

    InventoryModuleProbes(InventoryTransactionRepository transactionRepository, InventoryLotRepository lotRepository,
                          InventorySerialRepository serialRepository, InventoryDocumentRepository documentRepository) {
        this.transactionRepository = transactionRepository;
        this.lotRepository = lotRepository;
        this.serialRepository = serialRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> productsWithTransactions(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(transactionRepository.findProductIdsWithTransactions(productIds));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean lotExists(Long companyId, Long lotId) {
        return lotRepository.existsByIdAndCompanyId(lotId, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean serialExists(Long companyId, Long serialId) {
        return serialRepository.existsByIdAndCompanyId(serialId, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPostedData(Long companyId) {
        return transactionRepository.existsByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> blockers(PeriodCloseContext context) {
        if (context.module() != PeriodModule.INVENTORY) {
            return List.of();
        }
        List<String> numbers = documentRepository.findUnfinishedNumbers(context.companyId(), context.branchId(),
                UNFINISHED, context.startDate(), context.endDate());
        if (numbers.isEmpty()) {
            return List.of();
        }
        return List.of("còn chứng từ kho đã duyệt hoặc đang vận chuyển chưa ghi sổ: " + String.join(", ", numbers));
    }
}
