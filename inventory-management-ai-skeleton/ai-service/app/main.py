from collections.abc import Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.chat.guide import INDEX_FILE, load_index
from app.chat.ollama import ChatModel, OllamaChatModel
from app.chat.retriever import Bm25Retriever
from app.chat.service import ChatService
from app.config import Settings, get_settings
from app.errors import register_handlers
from app.logging_setup import RequestLogMiddleware, configure_logging, log
from app.ocr.engine import OcrEngine, OcrRunner, PaddleOcrEngine
from app.routers import chat, health, ocr


def create_app(settings: Settings | None = None,
               engine_factory: Callable[[Settings], OcrEngine] = PaddleOcrEngine,
               chat_model_factory: Callable[[Settings], ChatModel] = OllamaChatModel) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        # Nạp model một lần khi khởi động (docs/14 mục 9.2); lỗi nạp model thì dừng để dịch vụ hệ thống khởi động lại.
        app.state.ocr_runner = OcrRunner(engine_factory(settings))
        log.info("OCR sẵn sàng: det=%s rec=%s", settings.ocr_det_model, settings.ocr_rec_model)
        model = chat_model_factory(settings)
        app.state.chat_model = model
        app.state.chat_service = _chat_service(settings, model)
        yield
        app.state.ocr_runner = None
        close = getattr(model, "close", None)
        if close:
            close()

    docs = settings.ai_service_enable_docs
    app = FastAPI(title="ERP AI Service", version="0.1.0", lifespan=lifespan,
                  docs_url="/docs" if docs else None, redoc_url=None, openapi_url="/openapi.json" if docs else None)
    app.state.settings = settings
    app.state.ocr_runner = None
    app.state.chat_model = None
    app.state.chat_service = None
    app.add_middleware(RequestLogMiddleware)
    register_handlers(app)
    app.include_router(health.router)
    app.include_router(ocr.router)
    app.include_router(chat.router)
    return app


def _chat_service(settings: Settings, model: ChatModel) -> ChatService:
    """Chỉ mục lập ngoại tuyến bằng tools.build_rag_index; chưa có thì chat báo không khả dụng, OCR vẫn chạy."""
    directory = settings.rag_index_dir
    if directory is None or not (directory / INDEX_FILE).is_file():
        log.warning("Chưa có chỉ mục tài liệu hướng dẫn (RAG_INDEX_DIR): trợ lý chat không khả dụng.")
        return ChatService(settings, model, None, None)
    source, chunks = load_index(directory)
    log.info("Chỉ mục tài liệu hướng dẫn: %d đoạn từ %s", len(chunks), source)
    return ChatService(settings, model, Bm25Retriever(chunks), source)
