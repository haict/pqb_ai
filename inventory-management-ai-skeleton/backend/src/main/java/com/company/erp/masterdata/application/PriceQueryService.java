package com.company.erp.masterdata.application;

import com.company.erp.common.domain.RecordStatus;
import com.company.erp.masterdata.domain.Partner;
import com.company.erp.masterdata.domain.PriceTargetType;
import com.company.erp.masterdata.domain.PriceType;
import com.company.erp.masterdata.infrastructure.persistence.PartnerRepository;
import com.company.erp.masterdata.infrastructure.persistence.PriceListItemRepository;
import com.company.erp.masterdata.infrastructure.persistence.PriceListItemRepository.PriceCandidate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai tra giá bán cho phân hệ bán hàng (BR-SAL-02, BR-MD-42): giá theo thứ tự bảng giá riêng của khách
 * (gán trên khách hoặc bảng giá bán theo khách) → bảng giá bán theo nhóm khách / đại lý → giá niêm yết; giá sàn theo
 * bảng giá sàn (D-04). Chỉ bảng giá đang hoạt động, còn hiệu lực tại ngày; bậc số lượng lớn nhất không vượt số lượng.
 * Giá hợp đồng / trúng thầu (ưu tiên cao nhất) do module đấu thầu cung cấp ở Giai đoạn 7.
 */
@Service
public class PriceQueryService {

    /** Nguồn giá đã chọn. */
    public enum PriceSource {
        CUSTOMER,
        CUSTOMER_GROUP,
        LIST
    }

    /** Giá đề xuất (có thể trống), nguồn, mã bảng giá; giá sàn (có thể trống). */
    public record SalesPrice(BigDecimal unitPrice, PriceSource source, String priceListCode, BigDecimal floorPrice) {
    }

    private final PriceListItemRepository itemRepository;
    private final PartnerRepository partnerRepository;

    public PriceQueryService(PriceListItemRepository itemRepository, PartnerRepository partnerRepository) {
        this.itemRepository = itemRepository;
        this.partnerRepository = partnerRepository;
    }

    @Transactional(readOnly = true)
    public SalesPrice salesPrice(Long companyId, Long customerId, Long productId, Long unitId, BigDecimal quantity,
                                 LocalDate date) {
        Long groupId = null;
        Long ownListId = null;
        if (customerId != null) {
            Partner partner = partnerRepository.findById(customerId).orElse(null);
            if (partner != null) {
                groupId = partner.getPartnerGroupId();
                ownListId = partner.getPriceListId();
            }
        }
        List<PriceCandidate> candidates = itemRepository.findCandidates(companyId, productId, unitId,
                quantity == null ? BigDecimal.ZERO : quantity, date, RecordStatus.ACTIVE);
        Long finalGroupId = groupId;
        Long finalOwnListId = ownListId;
        Optional<PriceCandidate> own = best(candidates.stream()
                .filter(candidate -> candidate.getPriceListId().equals(finalOwnListId)
                        || (candidate.getPriceType() == PriceType.SALES
                        && candidate.getTargetType() == PriceTargetType.PARTNER
                        && candidate.getPartnerId() != null && candidate.getPartnerId().equals(customerId)))
                .filter(candidate -> candidate.getPriceType() != PriceType.FLOOR
                        && candidate.getPriceType() != PriceType.PURCHASE_REFERENCE)
                .toList());
        Optional<PriceCandidate> group = best(candidates.stream()
                .filter(candidate -> candidate.getPriceType() == PriceType.SALES
                        && candidate.getTargetType() == PriceTargetType.PARTNER_GROUP
                        && finalGroupId != null && finalGroupId.equals(candidate.getPartnerGroupId()))
                .toList());
        Optional<PriceCandidate> list = best(candidates.stream()
                .filter(candidate -> candidate.getPriceType() == PriceType.LIST
                        && candidate.getTargetType() == PriceTargetType.ALL)
                .toList());
        Optional<PriceCandidate> floor = best(candidates.stream()
                .filter(candidate -> candidate.getPriceType() == PriceType.FLOOR
                        && candidate.getTargetType() == PriceTargetType.ALL)
                .toList());
        BigDecimal floorPrice = floor.map(PriceCandidate::getUnitPrice).orElse(null);
        if (own.isPresent()) {
            return new SalesPrice(own.get().getUnitPrice(), PriceSource.CUSTOMER, own.get().getPriceListCode(),
                    floorPrice);
        }
        if (group.isPresent()) {
            return new SalesPrice(group.get().getUnitPrice(), PriceSource.CUSTOMER_GROUP,
                    group.get().getPriceListCode(), floorPrice);
        }
        return list.map(candidate -> new SalesPrice(candidate.getUnitPrice(), PriceSource.LIST,
                candidate.getPriceListCode(), floorPrice)).orElse(new SalesPrice(null, null, null, floorPrice));
    }

    /** Bậc số lượng lớn nhất; cùng bậc thì bảng giá hiệu lực muộn nhất. */
    private static Optional<PriceCandidate> best(List<PriceCandidate> candidates) {
        return candidates.stream().max(Comparator.comparing(PriceCandidate::getMinQuantity)
                .thenComparing(PriceCandidate::getValidFrom).thenComparing(PriceCandidate::getPriceListId));
    }
}
