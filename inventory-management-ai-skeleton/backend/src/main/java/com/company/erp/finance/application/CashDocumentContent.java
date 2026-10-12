package com.company.erp.finance.application;

import com.company.erp.accounting.application.AccountLookupService;
import com.company.erp.accounting.application.AccountLookupService.AccountInfo;
import com.company.erp.common.domain.BusinessException;
import com.company.erp.finance.application.dto.CashDocumentLineRequest;
import com.company.erp.finance.application.dto.CashDocumentRequest;
import com.company.erp.finance.domain.AllocationMode;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashAccountType;
import com.company.erp.finance.domain.CashDocument;
import com.company.erp.finance.domain.CashDocumentLine;
import com.company.erp.finance.domain.CashPurpose;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.DebtItemStatus;
import com.company.erp.finance.domain.DebtType;
import com.company.erp.finance.infrastructure.persistence.CashAccountRepository;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra nội dung chứng từ thu chi khi lập / sửa: quỹ đúng chi nhánh và loại, đối tượng đúng vai trò, dòng phân bổ
 * vào hóa đơn còn nợ hoặc tài khoản đối ứng hợp lệ (BR-CSH-02, BR-AR-03).
 */
@Component
class CashDocumentContent {

    private static final Set<DebtItemStatus> OPEN = EnumSet.of(DebtItemStatus.OPEN, DebtItemStatus.PARTIAL);

    private final CashAccountRepository accountRepository;
    private final DebtItemRepository itemRepository;
    private final AccountLookupService accounts;
    private final FinanceAccess access;

    CashDocumentContent(CashAccountRepository accountRepository, DebtItemRepository itemRepository,
                        AccountLookupService accounts, FinanceAccess access) {
        this.accountRepository = accountRepository;
        this.itemRepository = itemRepository;
        this.accounts = accounts;
        this.access = access;
    }

    /** Kiểm tra quỹ, đối tượng, dòng theo nội dung chứng từ rồi thay nội dung. */
    void fill(CashDocument document, CashDocumentRequest request) {
        CashAccount cash = cashAccount(document, request.cashAccountId());
        if (document.getPurpose() == CashPurpose.INTERNAL_TRANSFER && request.targetCashAccountId() != null) {
            targetAccount(document, cash, request.targetCashAccountId());
        }
        Long partnerId = partner(document, request.partnerId());
        List<CashDocumentLine> lines = document.getPurpose().settlesDebt()
                ? allocationLines(document, partnerId, request)
                : counterLines(document, partnerId, request.safeLines());
        LocalDate postingDate = request.postingDate() != null ? request.postingDate() : request.documentDate();
        document.replaceContent(new CashDocument.Content(request.documentDate(), postingDate, cash.getId(),
                request.targetCashAccountId(), partnerId, trim(request.counterpartyName()), request.allocationMode(),
                request.amount(), trim(request.description())), lines);
    }

    /** Quỹ chính: cùng chi nhánh với chứng từ, đang hoạt động, đúng loại (tiền mặt / ngân hàng) của loại chứng từ. */
    private CashAccount cashAccount(CashDocument document, Long id) {
        CashAccount cash = accountRepository.findById(id)
                .filter(found -> found.getCompanyId().equals(document.getCompanyId())
                        && found.getBranchId().equals(document.getBranchId()) && found.isActive())
                .orElseThrow(() -> BusinessException.invalidField("cashAccountId",
                        "Quỹ không tồn tại, đã ngừng hoặc thuộc chi nhánh khác."));
        if (document.getDocumentType().accountType() != null
                && cash.getAccountType() != document.getDocumentType().accountType()) {
            throw BusinessException.invalidField("cashAccountId", document.getDocumentType() + " phải dùng "
                    + (document.getDocumentType().accountType() == CashAccountType.CASH
                    ? "quỹ tiền mặt." : "tài khoản ngân hàng."));
        }
        return cash;
    }

    /** Quỹ nhận của chuyển tiền: cùng công ty (có thể chi nhánh khác), đang hoạt động. */
    private void targetAccount(CashDocument document, CashAccount source, Long id) {
        accountRepository.findById(id)
                .filter(found -> found.getCompanyId().equals(document.getCompanyId()) && found.isActive()
                        && !found.getId().equals(source.getId()))
                .orElseThrow(() -> BusinessException.invalidField("targetCashAccountId",
                        "Quỹ nhận không tồn tại, đã ngừng hoặc trùng quỹ chuyển."));
    }

    private Long partner(CashDocument document, Long partnerId) {
        DebtType debtType = document.getPurpose().partnerDebtType();
        if (debtType != null) {
            if (partnerId == null) {
                throw BusinessException.invalidField("partnerId", "Chọn đối tượng thanh toán.");
            }
            return access.debtPartner(document.getCompanyId(), partnerId, debtType, "partnerId").id();
        }
        return partnerId == null ? null : access.activePartner(document.getCompanyId(), partnerId, "partnerId").id();
    }

    /** Phân bổ chỉ định: hóa đơn còn nợ của đối tượng trong chi nhánh, không vượt số còn lại (kiểm lại khi ghi sổ). */
    private List<CashDocumentLine> allocationLines(CashDocument document, Long partnerId, CashDocumentRequest request) {
        List<CashDocumentLineRequest> requested = request.safeLines();
        if (request.allocationMode() != AllocationMode.MANUAL) {
            if (!requested.isEmpty()) {
                throw BusinessException.invalidField("lines", "Phân bổ tự động không nhập dòng phân bổ.");
            }
            return List.of();
        }
        Map<Long, DebtItem> items = itemRepository.findAllById(requested.stream()
                        .map(CashDocumentLineRequest::debtItemId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DebtItem::getId, Function.identity()));
        List<CashDocumentLine> lines = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            CashDocumentLineRequest line = requested.get(i);
            String field = "lines[" + i + "]";
            DebtItem item = line.debtItemId() == null ? null : items.get(line.debtItemId());
            if (item == null || !item.getCompanyId().equals(document.getCompanyId())
                    || !item.getBranchId().equals(document.getBranchId())
                    || item.getDebtType() != document.getPurpose().debtType()
                    || !item.getPartnerId().equals(partnerId) || !OPEN.contains(item.getStatus())) {
                throw BusinessException.invalidField(field + ".debtItemId", "Hóa đơn không còn nợ hoặc không thuộc "
                        + "đối tượng, chi nhánh của chứng từ.");
            }
            if (line.amount().compareTo(item.getRemainingAmount()) > 0) {
                throw BusinessException.invalidField(field + ".amount", "Hóa đơn " + item.label() + " chỉ còn "
                        + item.getRemainingAmount().stripTrailingZeros().toPlainString() + ".");
            }
            lines.add(CashDocumentLine.allocation(i + 1, item.getId(), line.amount(), trim(line.description())));
        }
        return lines;
    }

    /** Thu chi khác: tài khoản đối ứng chi tiết cùng công ty; tài khoản theo dõi đối tượng bắt buộc chọn đối tượng. */
    private List<CashDocumentLine> counterLines(CashDocument document, Long partnerId,
                                                List<CashDocumentLineRequest> requested) {
        Map<Long, AccountInfo> found = accounts.accounts(requested.stream().map(CashDocumentLineRequest::accountId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AccountInfo::id, Function.identity()));
        List<CashDocumentLine> lines = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            CashDocumentLineRequest line = requested.get(i);
            String field = "lines[" + i + "]";
            if (document.getPurpose() == CashPurpose.INTERNAL_TRANSFER) {
                throw BusinessException.invalidField("lines", "Chuyển tiền nội bộ không có dòng.");
            }
            AccountInfo account = line.accountId() == null ? null : found.get(line.accountId());
            if (account == null || !account.companyId().equals(document.getCompanyId()) || !account.usable()) {
                throw BusinessException.invalidField(field + ".accountId", "Tài khoản đối ứng không tồn tại, đã "
                        + "ngừng hoặc là tài khoản tổng hợp.");
            }
            if (account.partnerRequired() && partnerId == null) {
                throw BusinessException.invalidField(field + ".accountId", "Tài khoản " + account.code()
                        + " theo dõi theo đối tượng; chọn đối tượng cho chứng từ.");
            }
            if (line.costItemId() != null) {
                access.costItem(document.getCompanyId(), line.costItemId(), field + ".costItemId");
            }
            lines.add(CashDocumentLine.counterAccount(i + 1, account.id(), line.costItemId(), line.amount(),
                    trim(line.description())));
        }
        return lines;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
