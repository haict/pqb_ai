package com.company.erp.system.application;

import java.util.List;

/**
 * Điều kiện trước khi khóa kỳ do module nghiệp vụ cung cấp (BR-SYS-31), ví dụ module kế toán kiểm tra
 * "đã tính giá vốn kỳ" trước khi khóa kỳ kho. Module triển khai interface này dưới dạng Spring bean;
 * module system không phụ thuộc ngược vào module đó.
 */
public interface PeriodCloseCheck {

    /** Lý do chưa được khóa, tiếng Việt; danh sách rỗng là đủ điều kiện. */
    List<String> blockers(PeriodCloseContext context);
}
