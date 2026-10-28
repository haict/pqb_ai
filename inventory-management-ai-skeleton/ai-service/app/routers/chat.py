import time

from fastapi import APIRouter, Depends, Request

from app.chat.service import ChatTurn
from app.errors import AiServiceError
from app.logging_setup import log
from app.schemas import ChatRequest, ChatResponse
from app.security import require_service_key

router = APIRouter(prefix="/internal/v1", dependencies=[Depends(require_service_key)])


@router.post("/chat", response_model=ChatResponse)
def chat(request: Request, body: ChatRequest) -> ChatResponse:
    settings = request.app.state.settings
    start = time.perf_counter()
    error, grounded = None, False
    try:
        if len(body.question) > settings.chat_max_question_chars:
            raise AiServiceError(400, "VALIDATION_ERROR", "Câu hỏi quá dài.")
        result = request.app.state.chat_service.answer(
            [ChatTurn(turn.role, turn.content) for turn in body.history], body.question)
        grounded = result.grounded
        return ChatResponse(answer=result.answer, citations=result.citations, grounded=result.grounded,
                            durationMs=int((time.perf_counter() - start) * 1000))
    except AiServiceError as exc:
        error = exc.code
        raise
    finally:
        # Chỉ metadata (BR-AI-10): không ghi câu hỏi, đoạn tài liệu, câu trả lời.
        log.info("CHAT history=%d grounded=%s error=%s durationMs=%d", len(body.history), grounded, error or "-",
                 (time.perf_counter() - start) * 1000)
