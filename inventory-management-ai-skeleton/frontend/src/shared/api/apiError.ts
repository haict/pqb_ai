import { isAxiosError } from 'axios';
import type { ApiErrorBody, ApiFieldError } from './types';

export const NETWORK_ERROR = 'NETWORK_ERROR';
export const UNKNOWN_ERROR = 'UNKNOWN_ERROR';

/** Lỗi đã chuẩn hóa để giao diện hiển thị; không chứa chi tiết kỹ thuật. */
export class ApiError extends Error {
  readonly status: number | null;
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];
  readonly requestId: string | null;

  constructor(
    message: string,
    code: string,
    status: number | null,
    fieldErrors: ApiFieldError[] = [],
    requestId: string | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.requestId = requestId;
  }
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    (value as ApiErrorBody).success === false &&
    typeof (value as ApiErrorBody).code === 'string'
  );
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (isAxiosError(error)) {
    const body: unknown = error.response?.data;
    if (isApiErrorBody(body)) {
      return new ApiError(
        body.message,
        body.code,
        error.response?.status ?? null,
        body.errors ?? [],
        body.requestId,
      );
    }
    if (!error.response) {
      return new ApiError(
        'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.',
        NETWORK_ERROR,
        null,
      );
    }
    return new ApiError('Máy chủ trả về lỗi không xác định.', UNKNOWN_ERROR, error.response.status);
  }
  return new ApiError('Đã xảy ra lỗi không xác định.', UNKNOWN_ERROR, null);
}
