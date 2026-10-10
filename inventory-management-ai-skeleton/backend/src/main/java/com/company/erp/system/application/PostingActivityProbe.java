package com.company.erp.system.application;

/**
 * Cho biết công ty đã phát sinh số liệu ghi sổ hay chưa, để khóa tham số ảnh hưởng số liệu đã ghi sổ
 * (BR-SYS-13). Module ghi sổ (kho, công nợ, quỹ, kế toán) triển khai dưới dạng Spring bean.
 */
public interface PostingActivityProbe {

    boolean hasPostedData(Long companyId);
}
