package com.company.erp.finance.application;

import com.company.erp.accounting.application.AccountLookupService;
import com.company.erp.accounting.application.AccountLookupService.AccountInfo;
import com.company.erp.finance.application.dto.CashDocumentLineResponse;
import com.company.erp.finance.application.dto.CashDocumentResponse;
import com.company.erp.finance.application.dto.PaymentAllocationResponse;
import com.company.erp.finance.domain.CashAccount;
import com.company.erp.finance.domain.CashDocument;
import com.company.erp.finance.domain.CashDocumentLine;
import com.company.erp.finance.domain.DebtItem;
import com.company.erp.finance.domain.PaymentAllocation;
import com.company.erp.finance.infrastructure.persistence.CashAccountRepository;
import com.company.erp.finance.infrastructure.persistence.DebtItemRepository;
import com.company.erp.finance.infrastructure.persistence.PaymentAllocationRepository;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.masterdata.application.MasterDataQueryService.ReferenceInfo;
import com.company.erp.system.application.ApprovalService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Chuyển chứng từ thu chi sang DTO; tra quỹ, đối tượng, hóa đơn, tài khoản một lần cho cả trang / chứng từ. */
@Component
class CashDocumentMapper {

    private final CashAccountRepository cashAccounts;
    private final DebtItemRepository debtItems;
    private final PaymentAllocationRepository allocations;
    private final AccountLookupService accounts;
    private final MasterDataQueryService masterData;
    private final ApprovalService approvalService;

    CashDocumentMapper(CashAccountRepository cashAccounts, DebtItemRepository debtItems,
                       PaymentAllocationRepository allocations, AccountLookupService accounts,
                       MasterDataQueryService masterData, ApprovalService approvalService) {
        this.cashAccounts = cashAccounts;
        this.debtItems = debtItems;
        this.allocations = allocations;
        this.accounts = accounts;
        this.masterData = masterData;
        this.approvalService = approvalService;
    }

    private record Lookups(Map<Long, CashAccount> cash, Map<Long, PartnerInfo> partners) {
    }

    Page<CashDocumentResponse> toSummaries(Page<CashDocument> page) {
        Lookups lookups = lookups(page.getContent());
        return page.map(document -> response(document, lookups, List.of(), List.of(), false));
    }

    CashDocumentResponse toResponse(CashDocument document) {
        Lookups lookups = lookups(List.of(document));
        List<PaymentAllocation> posted = allocations.findBySource(document.getCompanyId(),
                document.getDocumentType().name(), document.getId());
        Map<Long, DebtItem> items = debtItems.findAllById(Stream.concat(
                        document.getLines().stream().map(CashDocumentLine::getDebtItemId),
                        posted.stream().map(PaymentAllocation::getDebtItemId))
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(DebtItem::getId, Function.identity()));
        return response(document, lookups, lines(document, items), posted.stream().map(allocation -> {
            DebtItem item = items.get(allocation.getDebtItemId());
            return new PaymentAllocationResponse(allocation.getId(), allocation.getDebtItemId(),
                    item == null ? null : item.getInvoiceNo(), item == null ? null : item.getSourceNo(),
                    item == null ? null : item.getDueDate(), allocation.getAmount(), allocation.getStatus());
        }).toList(), true);
    }

    private CashDocumentResponse response(CashDocument document, Lookups lookups,
                                          List<CashDocumentLineResponse> lines,
                                          List<PaymentAllocationResponse> posted, boolean detailed) {
        CashAccount cash = lookups.cash().get(document.getCashAccountId());
        CashAccount target = document.getTargetCashAccountId() == null ? null
                : lookups.cash().get(document.getTargetCashAccountId());
        PartnerInfo partner = document.getPartnerId() == null ? null : lookups.partners().get(document.getPartnerId());
        return new CashDocumentResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDocumentType(), document.getPurpose(),
                document.getDocumentDate(), document.getPostingDate(), document.getCashAccountId(),
                cash == null ? null : cash.getCode(), cash == null ? null : cash.getName(),
                document.getTargetCashAccountId(), target == null ? null : target.getCode(),
                target == null ? null : target.getName(), document.getPartnerId(),
                partner == null ? null : partner.code(), partner == null ? null : partner.name(),
                document.getCounterpartyName(), document.getAllocationMode(), document.getDescription(),
                document.getTotalAmount(), document.getAllocatedAmount(), document.getPrepaymentAmount(),
                document.getStatus(), document.getSubmittedAt(), document.getApprovedAt(), document.getPostedAt(),
                document.getReversedAt(), document.getReverseDate(), document.getReason(), document.getCreatedBy(),
                document.getVersion(), lines, posted,
                detailed ? approvalService.history(document.getDocumentType().name(), document.getId()) : List.of());
    }

    private List<CashDocumentLineResponse> lines(CashDocument document, Map<Long, DebtItem> items) {
        Map<Long, AccountInfo> glAccounts = accounts.accounts(document.getLines().stream()
                        .map(CashDocumentLine::getAccountId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AccountInfo::id, Function.identity()));
        return document.getLines().stream().map(line -> {
            DebtItem item = line.getDebtItemId() == null ? null : items.get(line.getDebtItemId());
            AccountInfo account = line.getAccountId() == null ? null : glAccounts.get(line.getAccountId());
            String costItem = line.getCostItemId() == null ? null
                    : masterData.referenceItem(line.getCostItemId()).map(ReferenceInfo::name).orElse(null);
            return new CashDocumentLineResponse(line.getId(), line.getLineNo(), line.getDebtItemId(),
                    item == null ? null : item.label(), item == null ? null : item.getRemainingAmount(),
                    line.getAccountId(), account == null ? null : account.code(),
                    account == null ? null : account.name(), line.getCostItemId(), costItem, line.getAmount(),
                    line.getDescription());
        }).toList();
    }

    private Lookups lookups(List<CashDocument> documents) {
        Set<Long> cashIds = new HashSet<>();
        Set<Long> partnerIds = new HashSet<>();
        for (CashDocument document : documents) {
            cashIds.add(document.getCashAccountId());
            if (document.getTargetCashAccountId() != null) {
                cashIds.add(document.getTargetCashAccountId());
            }
            if (document.getPartnerId() != null) {
                partnerIds.add(document.getPartnerId());
            }
        }
        Map<Long, CashAccount> cash = cashAccounts.findAllById(cashIds).stream()
                .collect(Collectors.toMap(CashAccount::getId, Function.identity()));
        Map<Long, PartnerInfo> partners = partnerIds.isEmpty() ? Map.of() : masterData.partners(partnerIds).stream()
                .collect(Collectors.toMap(PartnerInfo::id, Function.identity()));
        return new Lookups(cash, partners);
    }
}
