package com.company.erp.masterdata.application;

import java.util.Collection;
import java.util.Set;

/**
 * Cho biết hàng nào đã phát sinh giao dịch kho, để chặn đổi loại hàng, đơn vị cơ sở, cờ lô/hạn/serial (BR-MD-04).
 * Module kho triển khai dưới dạng Spring bean; module danh mục không phụ thuộc ngược vào module kho.
 */
public interface ProductUsageProbe {

    Set<Long> productsWithTransactions(Collection<Long> productIds);
}
