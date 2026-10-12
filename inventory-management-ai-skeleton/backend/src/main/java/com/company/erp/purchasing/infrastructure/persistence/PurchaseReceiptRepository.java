package com.company.erp.purchasing.infrastructure.persistence;

import com.company.erp.purchasing.domain.InspectionResult;
import com.company.erp.purchasing.domain.PurchaseDocumentStatus;
import com.company.erp.purchasing.domain.PurchaseReceipt;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, Long>,
        JpaSpecificationExecutor<PurchaseReceipt> {

    /** Khóa ghi chứng từ trước khi đổi trạng thái (BR-GEN-51). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PurchaseReceipt d where d.id = :id")
    Optional<PurchaseReceipt> findByIdForUpdate(@Param("id") Long id);

    /** Id phiếu chứa các dòng, tăng dần (khóa từng phiếu theo thứ tự này). */
    @Query("select distinct r.id from PurchaseReceipt r join r.lines l where l.id in :lineIds order by r.id")
    List<Long> findIdsByLineIds(@Param("lineIds") Collection<Long> lineIds);

    @Query("select distinct r from PurchaseReceipt r join r.lines l where l.id in :lineIds")
    List<PurchaseReceipt> findByLineIds(@Param("lineIds") Collection<Long> lineIds);

    /** Dòng đã nhập kho còn chưa nhận hóa đơn (đơn vị cơ sở). */
    interface InvoiceableLineRow {
        Long getReceiptId();

        String getReceiptNo();

        LocalDate getReceiptDate();

        Long getPurchaseOrderId();

        Long getReceiptLineId();

        Integer getLineNo();

        Long getOrderLineId();

        Long getProductId();

        Long getUnitId();

        BigDecimal getConversionFactor();

        BigDecimal getRemaining();

        BigDecimal getUnitCost();
    }

    @Query("""
            select r.id as receiptId, r.documentNo as receiptNo, r.documentDate as receiptDate,
                   r.purchaseOrderId as purchaseOrderId, l.id as receiptLineId, l.lineNo as lineNo,
                   l.orderLineId as orderLineId, l.productId as productId, l.unitId as unitId,
                   l.conversionFactor as conversionFactor,
                   (l.baseQuantity - l.invoicedQuantity - l.returnedQuantity + l.returnedInvoicedQuantity) as remaining,
                   l.unitCost as unitCost
            from PurchaseReceipt r join r.lines l
            where r.companyId = :companyId and r.branchId = :branchId and r.supplierId = :supplierId
              and r.status = :posted and r.stockedAt is not null and l.inspectionResult <> :returned
              and l.baseQuantity - l.invoicedQuantity - l.returnedQuantity + l.returnedInvoicedQuantity > 0
            order by r.documentDate, r.id, l.lineNo
            """)
    List<InvoiceableLineRow> findInvoiceableLines(@Param("companyId") Long companyId,
                                                  @Param("branchId") Long branchId,
                                                  @Param("supplierId") Long supplierId,
                                                  @Param("posted") PurchaseDocumentStatus posted,
                                                  @Param("returned") InspectionResult returned);

    /** Phiếu đã ghi sổ và nhập kho của nhà cung cấp trong chi nhánh (chọn dòng trả hàng / phân bổ chi phí). */
    @Query("""
            select r from PurchaseReceipt r
            where r.companyId = :companyId and r.branchId = :branchId
              and (:supplierId is null or r.supplierId = :supplierId)
              and r.status = :posted and r.stockedAt is not null
            order by r.documentDate desc, r.id desc
            """)
    List<PurchaseReceipt> findStocked(@Param("companyId") Long companyId, @Param("branchId") Long branchId,
                                      @Param("supplierId") Long supplierId,
                                      @Param("posted") PurchaseDocumentStatus posted);
}
