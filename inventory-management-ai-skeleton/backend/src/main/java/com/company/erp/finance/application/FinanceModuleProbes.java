package com.company.erp.finance.application;

import com.company.erp.common.domain.PeriodModule;
import com.company.erp.finance.domain.FinanceDocumentStatus;
import com.company.erp.finance.infrastructure.persistence.CashCountRepository;
import com.company.erp.finance.infrastructure.persistence.CashDocumentRepository;
import com.company.erp.finance.infrastructure.persistence.DebtAdjustmentDocumentRepository;
import com.company.erp.finance.infrastructure.persistence.DebtOffsetDocumentRepository;
import com.company.erp.system.application.PeriodCloseCheck;
import com.company.erp.system.application.PeriodCloseContext;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Điều kiện khóa kỳ của module công nợ, quỹ (BR-SYS-31): không còn chứng từ chờ duyệt / đã duyệt chưa ghi sổ có ngày
 * hạch toán trong kỳ (kỳ quỹ: thu chi, kiểm kê quỹ; kỳ công nợ: bù trừ, điều chỉnh, thanh toán tạm ứng).
 */
@Component
class FinanceModuleProbes implements PeriodCloseCheck {

    private static final Set<FinanceDocumentStatus> UNFINISHED =
            EnumSet.of(FinanceDocumentStatus.SUBMITTED, FinanceDocumentStatus.APPROVED);

    private final CashDocumentRepository cashDocuments;
    private final CashCountRepository cashCounts;
    private final DebtOffsetDocumentRepository offsets;
    private final DebtAdjustmentDocumentRepository adjustments;

    FinanceModuleProbes(CashDocumentRepository cashDocuments, CashCountRepository cashCounts,
                        DebtOffsetDocumentRepository offsets, DebtAdjustmentDocumentRepository adjustments) {
        this.cashDocuments = cashDocuments;
        this.cashCounts = cashCounts;
        this.offsets = offsets;
        this.adjustments = adjustments;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> blockers(PeriodCloseContext context) {
        List<String> numbers = new ArrayList<>();
        if (context.module() == PeriodModule.CASH) {
            numbers.addAll(cashDocuments.findUnfinishedNumbers(context.companyId(), context.branchId(), UNFINISHED,
                    context.startDate(), context.endDate()));
            numbers.addAll(cashCounts.findUnfinishedNumbers(context.companyId(), context.branchId(), UNFINISHED,
                    context.startDate(), context.endDate()));
        } else if (context.module() == PeriodModule.DEBT) {
            numbers.addAll(offsets.findUnfinishedNumbers(context.companyId(), context.branchId(), UNFINISHED,
                    context.startDate(), context.endDate()));
            numbers.addAll(adjustments.findUnfinishedNumbers(context.companyId(), context.branchId(), UNFINISHED,
                    context.startDate(), context.endDate()));
        }
        if (numbers.isEmpty()) {
            return List.of();
        }
        return List.of("còn chứng từ chờ duyệt hoặc đã duyệt chưa ghi sổ: " + String.join(", ", numbers));
    }
}
