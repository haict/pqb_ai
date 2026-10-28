import uvicorn

from app.config import get_settings
from app.main import create_app


def main() -> None:
    settings = get_settings()
    # Một worker: model OCR nạp một lần trong một tiến trình (docs/14 mục 9.2).
    uvicorn.run(create_app(settings), host=settings.ai_service_host, port=settings.ai_service_port,
                workers=1, access_log=False, log_config=None)


if __name__ == "__main__":
    main()
