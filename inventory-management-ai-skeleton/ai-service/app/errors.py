from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.logging_setup import request_id_var, log


class AiServiceError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(code)
        self.status = status
        self.code = code
        self.message = message


def unauthorized() -> AiServiceError:
    return AiServiceError(401, "UNAUTHORIZED", "Thiếu hoặc sai khóa gọi AI Service.")


def file_too_large() -> AiServiceError:
    return AiServiceError(413, "OCR_FILE_TOO_LARGE", "File vượt dung lượng cho phép.")


def unsupported_type() -> AiServiceError:
    return AiServiceError(415, "OCR_UNSUPPORTED_TYPE", "Chỉ hỗ trợ file JPG, PNG, PDF.")


def file_invalid(message: str = "File hỏng hoặc không đọc được.") -> AiServiceError:
    return AiServiceError(400, "OCR_FILE_INVALID", message)


def no_text() -> AiServiceError:
    return AiServiceError(422, "OCR_NO_TEXT", "Không nhận dạng được chữ trên tài liệu.")


def busy() -> AiServiceError:
    return AiServiceError(429, "AI_BUSY", "AI Service đang xử lý yêu cầu khác, vui lòng thử lại.")


def ai_unavailable(message: str = "Trợ lý AI chưa sẵn sàng.") -> AiServiceError:
    return AiServiceError(503, "AI_SERVICE_UNAVAILABLE", message)


def ai_timeout() -> AiServiceError:
    return AiServiceError(504, "AI_TIMEOUT", "Trợ lý AI trả lời quá thời gian cho phép.")


def _body(code: str, message: str) -> dict:
    return {"success": False, "code": code, "message": message, "requestId": request_id_var.get()}


def register_handlers(app: FastAPI) -> None:
    @app.exception_handler(AiServiceError)
    async def handle_ai_error(_: Request, exc: AiServiceError):
        return JSONResponse(status_code=exc.status, content=_body(exc.code, exc.message))

    @app.exception_handler(RequestValidationError)
    async def handle_validation(_: Request, exc: RequestValidationError):
        fields = [".".join(str(p) for p in e.get("loc", ()) if p != "body") for e in exc.errors()]
        return JSONResponse(status_code=400, content=_body("VALIDATION_ERROR", f"Dữ liệu không hợp lệ: {', '.join(fields)}."))

    @app.exception_handler(StarletteHTTPException)
    async def handle_http(_: Request, exc: StarletteHTTPException):
        code = "NOT_FOUND" if exc.status_code == 404 else "HTTP_ERROR"
        return JSONResponse(status_code=exc.status_code, content=_body(code, "Yêu cầu không hợp lệ."))

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, exc: Exception):
        # Chỉ ghi loại lỗi; nội dung exception có thể chứa dữ liệu chứng từ.
        log.error("Lỗi không mong đợi: %s", type(exc).__name__)
        return JSONResponse(status_code=500, content=_body("INTERNAL_ERROR", "Lỗi hệ thống AI Service."))
