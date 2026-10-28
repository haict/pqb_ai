from fastapi import APIRouter, Depends, Request

from app.schemas import ChatHealth, HealthResponse, OcrHealth
from app.security import require_service_key

router = APIRouter(prefix="/internal/v1", dependencies=[Depends(require_service_key)])


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    settings = request.app.state.settings
    ready = getattr(request.app.state, "ocr_runner", None) is not None
    service = request.app.state.chat_service
    model = request.app.state.chat_model
    index_ready = service is not None and service.index_ready
    # Chỉ hỏi Ollama khi đã có chỉ mục; Spring gọi health định kỳ (ai.service.health-interval).
    llm_ready = index_ready and model is not None and model.ready()
    return HealthResponse(
        status="UP" if ready else "STARTING",
        ocr=OcrHealth(
            ready=ready,
            detectionModel=settings.ocr_det_model,
            recognitionModel=settings.ocr_rec_model,
            textDiacriticsReliable=settings.ocr_text_diacritics_reliable,
        ),
        chat=ChatHealth(ready=index_ready and llm_ready, indexReady=index_ready, llmReady=llm_ready,
                        model=settings.chat_model),
    )
