package com.company.erp.inventory.application.dto;

import com.company.erp.inventory.domain.InventoryDocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu in phiếu nhập kho / phiếu xuất kho của chứng từ kho đã ghi sổ (D-78). {@code warehouseCode},
 * {@code warehouseName}: các kho của phiếu nối bằng dấu phẩy; kho từng dòng ở {@link Line#warehouseCode()}.
 * Đơn giá, thành tiền: phiếu nhập theo đơn giá nhập của dòng; phiếu xuất theo giá trị đã ghi trên sổ kho, chưa có giá
 * vốn thì bằng 0 (D-51). {@code sourceNo}: chứng từ nguồn (đơn mua / đơn bán) nếu phiếu sinh tự động.
 * Phiếu chuyển kho: {@code warehouseCode/Name} là kho xuất, {@code destinationWarehouseCode/Name} là kho nhận (loại
 * khác để trống); dòng, lô, giá trị theo dòng sổ xuất khỏi kho xuất.
 */
public record InventorySlipResponse(
        String companyName,
        String companyAddress,
        Long documentId,
        InventoryDocumentType documentType,
        String documentNo,
        LocalDate slipDate,
        String partnerName,
        String partnerAddress,
        String partnerTaxCode,
        String description,
        String sourceNo,
        String warehouseCode,
        String warehouseName,
        List<Line> lines,
        BigDecimal totalAmount,
        String destinationWarehouseCode,
        String destinationWarehouseName) {

    public record Line(
            int lineNo,
            String productCode,
            String productName,
            String warehouseCode,
            Long unitId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            List<Item> items) {
    }

    /** Lô / serial thực nhập / xuất của dòng theo sổ kho; {@code baseQuantity} theo đơn vị cơ sở. */
    public record Item(String lotNo, LocalDate expiryDate, String serialNo, BigDecimal baseQuantity) {
    }
}
