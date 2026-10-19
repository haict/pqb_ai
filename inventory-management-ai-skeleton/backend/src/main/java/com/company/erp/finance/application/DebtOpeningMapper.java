package com.company.erp.finance.application;

import com.company.erp.finance.application.dto.DebtOpeningLineResponse;
import com.company.erp.finance.application.dto.DebtOpeningResponse;
import com.company.erp.finance.domain.DebtOpeningDocument;
import com.company.erp.finance.domain.DebtOpeningLine;
import com.company.erp.masterdata.application.MasterDataQueryService;
import com.company.erp.masterdata.application.MasterDataQueryService.PartnerInfo;
import com.company.erp.system.application.ApprovalService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Chuyển chứng từ công nợ đầu kỳ sang DTO; tra mã, tên đối tượng một lần cho mọi dòng. */
@Component
class DebtOpeningMapper {

    private final MasterDataQueryService masterData;
    private final ApprovalService approvalService;

    DebtOpeningMapper(MasterDataQueryService masterData, ApprovalService approvalService) {
        this.masterData = masterData;
        this.approvalService = approvalService;
    }

    DebtOpeningResponse toResponse(DebtOpeningDocument document, boolean detailed) {
        List<DebtOpeningLineResponse> lines = detailed ? lines(document) : List.of();
        return new DebtOpeningResponse(document.getId(), document.getCompanyId(), document.getBranchId(),
                document.getDocumentNo(), document.getDebtType(), document.getDocumentDate(),
                document.getPostingDate(), document.getDescription(), document.getTotalAmount(),
                document.getLines().size(), document.getStatus(), document.getSubmittedAt(),
                document.getApprovedAt(), document.getPostedAt(), document.getReversedAt(),
                document.getReverseDate(), document.getReason(), document.getCreatedBy(), document.getVersion(),
                lines, detailed ? approvalService.history(DebtOpeningService.DOCUMENT_TYPE, document.getId())
                        : List.of());
    }

    private List<DebtOpeningLineResponse> lines(DebtOpeningDocument document) {
        Map<Long, PartnerInfo> partners = masterData.partners(document.getLines().stream()
                        .map(DebtOpeningLine::getPartnerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PartnerInfo::id, Function.identity()));
        return document.getLines().stream().map(line -> {
            PartnerInfo partner = partners.get(line.getPartnerId());
            return new DebtOpeningLineResponse(line.getId(), line.getLineNo(), line.getPartnerId(),
                    partner == null ? null : partner.code(), partner == null ? null : partner.name(),
                    line.getDueDate(), line.getAmount(),
                    line.getSalesUserId(), line.getNote());
        }).toList();
    }
}
