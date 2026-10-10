package com.company.erp.masterdata.application;

/**
 * Kiểm tra lô, serial tồn tại trong công ty, để gắn hồ sơ chất lượng theo lô / serial (BR-INV-05). Module kho triển
 * khai dưới dạng Spring bean; module danh mục không phụ thuộc ngược vào module kho.
 */
public interface TrackedItemDirectory {

    boolean lotExists(Long companyId, Long lotId);

    boolean serialExists(Long companyId, Long serialId);
}
