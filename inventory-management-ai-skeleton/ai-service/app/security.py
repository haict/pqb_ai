import hmac

from fastapi import Header, Request

from app.errors import unauthorized


def require_service_key(request: Request, x_ai_service_key: str = Header(default="")) -> None:
    expected = request.app.state.settings.ai_service_api_key.get_secret_value()
    if not hmac.compare_digest(x_ai_service_key.encode(), expected.encode()):
        raise unauthorized()
