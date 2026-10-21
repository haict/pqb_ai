package com.company.erp.sales.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.inventory.application.ReceiptSourceService;
import com.company.erp.inventory.application.ReceiptSourceService.ReceiptRef;
import com.company.erp.masterdata.application.MasterDataQueryService.ProductInfo;
import com.company.erp.sales.application.dto.ReceiptSourceResponse;
import com.company.erp.sales.application.dto.SalesLineRequest;
import com.company.erp.sales.domain.SalesOrderLine;
import com.company.erp.system.application.CurrentUser;
import com.company.erp.system.application.OrganizationQueryService.BranchRef;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chọn chứng từ nhập cho dòng đơn bán (D-63, F8 xuất đích danh như MISA): danh sách dòng nhập còn tồn của hàng tại kho
 * của đơn, và kiểm tra liên kết khi lưu đơn. Áp dụng hàng quản lý lô / serial; giá vốn đích danh tính ở Giai đoạn 8.
 */
@Service
public class SalesReceiptSourceService {

    private final SalesAccess access;
    private final ReceiptSourceService receipts;

    public SalesReceiptSourceService(SalesAccess access, ReceiptSourceService receipts) {
        this.access = access;
        this.receipts = receipts;
    }

    /** Dòng nhập còn tồn (FEFO); hàng không quản lý lô / serial trả danh sách rỗng. */
    @Transactional(readOnly = true)
    public List<ReceiptSourceResponse> available(CurrentUser actor, Long branchId, Long warehouseId, Long productId) {
        access.requirePermission(actor, SalesOrderService.CREATE);
        BranchRef branch = access.usableBranch(actor, branchId);
        access.issuingWarehouse(branch.id(), warehouseId, "warehouseId");
        ProductInfo product = access.product(branch.companyId(), productId, "productId");
        if (!product.lotTracked() && !product.serialTracked()) {
            return List.of();
        }
        return receipts.available(branch.companyId(), warehouseId, productId).stream()
                .map(source -> new ReceiptSourceResponse(source.transactionId(), source.documentType(),
                        source.documentId(), source.documentNo(), source.postingDate(), source.lotNo(),
                        source.expiryDate(), source.serialNo(), source.receivedQuantity(), source.unitCost(),
                        source.availableQuantity()))
                .toList();
    }

    /**
     * Chứng từ nhập của dòng (trống nếu không chọn): cùng công ty, kho, hàng, chưa đảo; lô của dòng phải là lô của
     * chứng từ nhập, serial của chứng từ nhập phải nằm trong serial chỉ định.
     */
    SalesOrderLine.ReceiptSource resolve(Long companyId, Long warehouseId, SalesLineRequest line, String lotNo,
                                         List<String> serials, String field) {
        if (line.receiptTransactionId() == null) {
            return null;
        }
        ReceiptRef ref = receipts.find(line.receiptTransactionId())
                .filter(found -> found.usable() && found.companyId().equals(companyId)
                        && found.warehouseId().equals(warehouseId) && found.productId().equals(line.productId()))
                .orElseThrow(() -> BusinessException.invalidField(field + ".receiptTransactionId",
                        "Chứng từ nhập không thuộc hàng / kho của dòng hoặc đã đảo; chọn lại (F8)."));
        if (ref.lotNo() != null && !Objects.equals(ref.lotNo(), lotNo)) {
            throw BusinessException.invalidField(field + ".lotNo", "Lô phải là lô " + ref.lotNo()
                    + " của chứng từ nhập " + ref.documentNo() + ".");
        }
        if (ref.serialNo() != null && !serials.contains(ref.serialNo())) {
            throw BusinessException.invalidField(field + ".serialNumbers", "Serial chỉ định phải gồm serial "
                    + ref.serialNo() + " của chứng từ nhập " + ref.documentNo() + ".");
        }
        return new SalesOrderLine.ReceiptSource(ref.transactionId(), ref.documentNo());
    }
}
