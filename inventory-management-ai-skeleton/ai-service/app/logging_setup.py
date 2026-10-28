import logging
import re
import time
import uuid
from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

request_id_var: ContextVar[str] = ContextVar("request_id", default="-")
log = logging.getLogger("ai_service")

_REQUEST_ID = re.compile(r"^[A-Za-z0-9-]{1,64}$")


class _RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_var.get()
        return True


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler()
    handler.addFilter(_RequestIdFilter())
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s [%(request_id)s] %(name)s - %(message)s"))
    log.handlers[:] = [handler]
    log.setLevel(level.upper())
    log.propagate = False


class RequestLogMiddleware(BaseHTTPMiddleware):
    """Ghi requestId, đường dẫn, mã trạng thái, thời gian. Không ghi header, nội dung request hay kết quả OCR."""

    async def dispatch(self, request: Request, call_next):
        incoming = request.headers.get("X-Request-Id", "")
        token = request_id_var.set(incoming if _REQUEST_ID.match(incoming) else uuid.uuid4().hex)
        start = time.perf_counter()
        try:
            response = await call_next(request)
            response.headers["X-Request-Id"] = request_id_var.get()
            log.info("%s %s -> %s (%d ms)", request.method, request.url.path, response.status_code,
                     (time.perf_counter() - start) * 1000)
            return response
        finally:
            request_id_var.reset(token)
