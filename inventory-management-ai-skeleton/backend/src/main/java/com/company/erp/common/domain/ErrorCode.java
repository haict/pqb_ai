package com.company.erp.common.domain;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi ổn định trả cho client (docs/04-api-specification.md mục 1.4).
 * Frontend xử lý theo {@code name()}; không đổi tên mã đã công bố.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu sai định dạng."),
    OCR_FILE_INVALID(HttpStatus.BAD_REQUEST, "File hỏng hoặc không đọc được."),
    ASSISTANT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Câu hỏi quá dài."),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đang bị tạm khóa."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên làm việc đã hết hạn, vui lòng đăng nhập lại."),

    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Bạn cần đổi mật khẩu trước khi tiếp tục."),
    LICENSE_INACTIVE(HttpStatus.FORBIDDEN, "Bản quyền phần mềm chưa có, không hợp lệ hoặc đã hết hạn: hệ thống chỉ cho "
            + "xem, in, xuất dữ liệu. Liên hệ nhà cung cấp để gia hạn."),

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
    DATA_IN_USE(HttpStatus.CONFLICT, "Dữ liệu đã được sử dụng, không thay đổi được."),
    CONFIGURATION_IN_EFFECT(HttpStatus.CONFLICT, "Cấu hình đã có hiệu lực, không xóa được."),
    INVALID_PERIOD_STATE(HttpStatus.CONFLICT, "Trạng thái kỳ không cho phép thao tác này."),

    OCR_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File vượt dung lượng cho phép."),
    OCR_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ hỗ trợ file ảnh JPG, PNG hoặc PDF."),

    OCR_NO_TEXT(HttpStatus.UNPROCESSABLE_ENTITY, "Không nhận dạng được chữ trên tài liệu."),
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "Tồn khả dụng không đủ."),
    LOT_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Lô hàng đã hết hạn."),
    LOT_NOT_SELLABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Lô hàng không ở trạng thái được bán."),
    SERIAL_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Serial không khả dụng."),
    PERIOD_LOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Kỳ đã khóa."),
    PERIOD_NOT_DEFINED(HttpStatus.UNPROCESSABLE_ENTITY, "Chưa tạo kỳ cho ngày hạch toán."),
    PERIOD_ORDER_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Phải khóa/mở kỳ theo đúng thứ tự thời gian."),
    PERIOD_CLOSE_PRECONDITION(HttpStatus.UNPROCESSABLE_ENTITY, "Chưa đủ điều kiện khóa kỳ."),
    PARAMETER_LOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Tham số đã khóa vì đã phát sinh số liệu ghi sổ."),
    CONTRACT_QUANTITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Vượt số lượng còn lại của hợp đồng."),
    CREDIT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Vượt hạn mức tín dụng."),
    AUTHORITY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Giá trị chứng từ vượt ngưỡng thẩm quyền của bạn."),
    PRICE_BELOW_FLOOR(HttpStatus.UNPROCESSABLE_ENTITY, "Giá thấp hơn giá sàn."),
    OVER_ALLOCATION(HttpStatus.UNPROCESSABLE_ENTITY, "Số phân bổ vượt số còn lại."),
    SOURCE_QUANTITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Vượt số lượng còn lại của chứng từ nguồn."),
    CASH_BALANCE_NEGATIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Số dư quỹ không đủ."),
    STOCK_COUNT_IN_PROGRESS(HttpStatus.UNPROCESSABLE_ENTITY, "Kho đang kiểm kê."),
    POSTING_RULE_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "Chưa cấu hình quy tắc hạch toán cho nghiệp vụ này."),
    POSTING_RULE_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "Quy tắc hạch toán thiếu tài khoản."),
    ACCOUNT_NOT_POSTABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Tài khoản không được hạch toán trực tiếp."),
    UNBALANCED_JOURNAL(HttpStatus.UNPROCESSABLE_ENTITY, "Bút toán không cân Nợ - Có."),

    LOCK_TIMEOUT(HttpStatus.LOCKED, "Dữ liệu đang được xử lý, vui lòng thử lại."),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều lần thử, vui lòng thử lại sau."),
    AI_BUSY(HttpStatus.TOO_MANY_REQUESTS, "Hệ thống AI đang bận, vui lòng thử lại sau."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống, vui lòng thử lại hoặc liên hệ quản trị."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Chức năng AI tạm thời không khả dụng; vẫn nhập tay bình thường."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Hệ thống AI xử lý quá thời gian, vui lòng thử lại.");

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
