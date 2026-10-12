package com.company.erp.common.application.excel;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;
import com.company.erp.common.domain.FieldViolation;
import java.util.ArrayList;
import java.util.List;

/**
 * Lỗi theo dòng khi nhập Excel. Trường lỗi dạng {@code rows[<số dòng Excel>].<cột>} để giao diện hiển thị đúng dòng.
 * Giữ tối đa {@link #MAX_ERRORS} lỗi để phản hồi không quá lớn.
 */
public final class RowErrors {

    private static final int MAX_ERRORS = 200;

    private final List<FieldViolation> violations = new ArrayList<>();
    private int total;
    private int currentRow;

    public void row(int excelRowNumber) {
        this.currentRow = excelRowNumber;
    }

    public void add(String column, String message) {
        total++;
        if (violations.size() < MAX_ERRORS) {
            violations.add(new FieldViolation("rows[" + currentRow + "]." + column, "Dòng " + currentRow + ": " + message));
        }
    }

    /** Có lỗi thì dừng toàn bộ lần nhập, không ghi dòng nào. */
    public void throwIfAny() {
        if (total > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "File có " + total + " lỗi, chưa nhập dòng nào. Sửa file rồi nhập lại.", violations);
        }
    }
}
