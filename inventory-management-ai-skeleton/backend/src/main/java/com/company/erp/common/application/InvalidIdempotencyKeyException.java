package com.company.erp.common.application;

import com.company.erp.common.domain.BusinessException;
import com.company.erp.common.domain.ErrorCode;

public class InvalidIdempotencyKeyException extends BusinessException {

    public InvalidIdempotencyKeyException() {
        super(ErrorCode.VALIDATION_ERROR,
                "Header " + IdempotentCommand.HEADER + " bắt buộc, gồm 8-100 ký tự chữ, số hoặc dấu gạch ngang.");
    }
}
