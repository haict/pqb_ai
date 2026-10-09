package com.company.erp.common.domain;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi ổn định trả cho client (docs/04-api-specification.md mục 1.4).
 * Frontend xử lý theo {@code name()}; không đổi tên mã đã công bố.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu sai định dạng."),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đang bị tạm khóa."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên làm việc đã hết hạn, vui lòng đăng nhập lại."),

    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    SELF_APPROVAL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Người lập không được tự duyệt chứng từ."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Bạn cần đổi mật khẩu trước khi tiếp tục."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ."),

    DUPLICATE_CODE(HttpStatus.CONFLICT, "Mã đã tồn tại."),
    DUPLICATE_DOCUMENT(HttpStatus.CONFLICT, "Chứng từ đã tồn tại."),
    INVALID_DOCUMENT_STATE(HttpStatus.CONFLICT, "Trạng thái chứng từ không cho phép thao tác này."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Dữ liệu đã được người khác thay đổi, vui lòng tải lại."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Mã chống trùng đã dùng cho một yêu cầu khác."),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "Yêu cầu giống hệt đang được xử lý, vui lòng tải lại sau ít giây."),
    HAS_DEPENDENT_DOCUMENTS(HttpStatus.CONFLICT, "Có chứng từ phụ thuộc, cần xử lý trước."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Dữ liệu vi phạm ràng buộc toàn vẹn."),

    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "Tồn khả dụng không đủ."),
    LOT_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Lô hàng đã hết hạn."),
    LOT_NOT_SELLABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Lô hàng không ở trạng thái được bán."),
    SERIAL_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Serial không khả dụng."),
    PERIOD_LOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Kỳ đã khóa."),
    CONTRACT_QUANTITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Vượt số lượng còn lại của hợp đồng."),
    CREDIT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Vượt hạn mức tín dụng."),
    PRICE_BELOW_FLOOR(HttpStatus.UNPROCESSABLE_ENTITY, "Giá thấp hơn giá sàn."),
    OVER_ALLOCATION(HttpStatus.UNPROCESSABLE_ENTITY, "Số phân bổ vượt số còn lại."),
    CASH_BALANCE_NEGATIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Số dư quỹ không đủ."),
    STOCK_COUNT_IN_PROGRESS(HttpStatus.UNPROCESSABLE_ENTITY, "Kho đang kiểm kê."),

    LOCK_TIMEOUT(HttpStatus.LOCKED, "Dữ liệu đang được xử lý, vui lòng thử lại."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống, vui lòng thử lại hoặc liên hệ quản trị.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
