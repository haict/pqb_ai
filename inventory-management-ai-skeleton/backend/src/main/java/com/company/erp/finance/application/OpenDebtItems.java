package com.company.erp.finance.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtItemStatus;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.domain.FinanceDocument;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra khoản công nợ được chọn trên dòng chứng từ khi lập / sửa (bù trừ, điều chỉnh): còn nợ, đúng công ty, chi
 * nhánh, đối tượng, loại công nợ, số tiền không vượt số còn lại. Ghi sổ kiểm tra lại sau khi khóa (BR-GEN-52).
 */
@Component
class OpenDebtItems {

    private static final Set<DebtItemStatus> OPEN = EnumSet.of(DebtItemStatus.OPEN, DebtItemStatus.PARTIAL);

    private final DebtItemRepository repository;

    OpenDebtItems(DebtItemRepository repository) {
        this.repository = repository;
    }

    Map<Long, DebtItem> load(Collection<Long> ids) {
        return repository.findAllById(ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(DebtItem::getId, Function.identity()));
    }

    DebtItem require(DebtItem item, FinanceDocument document, Long partnerId, Set<DebtType> types,
                     BigDecimal amount, String field) {
        if (item == null || !item.getCompanyId().equals(document.getCompanyId())
                || !item.getBranchId().equals(document.getBranchId()) || !item.getPartnerId().equals(partnerId)
                || !types.contains(item.getDebtType()) || !OPEN.contains(item.getStatus())) {
            throw BusinessException.invalidField(field + ".debtItemId", "Khoản công nợ không còn nợ hoặc không thuộc "
                    + "đối tượng, chi nhánh, loại công nợ của chứng từ.");
        }
        if (amount.compareTo(item.getRemainingAmount()) > 0) {
            throw BusinessException.invalidField(field + ".amount", "Hóa đơn " + item.label() + " chỉ còn "
                    + item.getRemainingAmount().stripTrailingZeros().toPlainString() + ".");
        }
        return item;
    }
}
