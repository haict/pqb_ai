package com.company.erp.inventory.domain;

/**
 * Loại chứng từ kho dùng bảng {@code inventory_documents}; mã loại đồng thời là mã trong {@code sys_document_types}
 * (chế độ duyệt) và tiền tố số chứng từ mặc định (D-03).
 */
public enum InventoryDocumentType {
    /** Phiếu nhập kho: tồn đầu kỳ, nhập thu hồi từ khách. */
    PNK("inventory-receipt", "inventory-receipt:view"),
    /** Phiếu xuất kho nội bộ / demo (đề nghị nội bộ có duyệt). */
    PXK("inventory-issue", "inventory-issue:view"),
    /** Phiếu xuất hủy (BR-INV-33). */
    PXH("inventory-issue", "inventory-issue:view"),
    /** Phiếu chuyển kho (BR-INV-30). */
    PCK("inventory-transfer", "inventory-transfer:view"),
    /** Phiếu điều chỉnh tồn (BR-INV-32), kể cả phiếu điều chỉnh sinh từ kiểm kê. */
    PDC("inventory-adjustment", "inventory-adjustment:view"),
    /** Phiếu chuyển trạng thái chất lượng (BR-INV-03). */
    PCL("inventory-adjustment", "inventory-adjustment:view"),
    /** Chứng từ đảo chứng từ kho (BR-GEN-03, BR-GEN-21). */
    PDK("inventory-reversal", "inventory:view");

    private final String permissionPrefix;
    private final String viewPermission;

    InventoryDocumentType(String permissionPrefix, String viewPermission) {
        this.permissionPrefix = permissionPrefix;
        this.viewPermission = viewPermission;
    }

    public String viewPermission() {
        return viewPermission;
    }

    /** Quyền hành động: {@code create}, {@code approve}, {@code post}. */
    public String permission(String action) {
        return permissionPrefix + ":" + action;
    }
}
