import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { ApiError, toApiError } from './apiError';
import type { ApiResponse } from './types';
import { sessionStore } from '../auth/sessionStore';
import type { TokenResponse } from '../auth/types';

export const API_BASE = '/erp/api/v1';
export const IDEMPOTENCY_HEADER = 'Idempotency-Key';
const BRANCH_HEADER = 'X-Branch-Id';

/** Client cho API xác thực: không gắn access token, không tự làm mới (tránh vòng lặp). */
export const authClient = axios.create({
  baseURL: `${API_BASE}/auth`,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});
authClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(toApiError(error)),
);

/** Client dùng chung cho API nghiệp vụ. Mọi lỗi được chuẩn hóa thành ApiError. */
export const httpClient = axios.create({
  baseURL: API_BASE,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
});

let refreshInFlight: Promise<TokenResponse> | null = null;

/**
 * Làm mới phiên bằng refresh token trong cookie httpOnly. Nhiều request cùng hết hạn chỉ gọi /auth/refresh
 * một lần: backend coi refresh token bị gửi lại là bị đánh cắp và thu hồi cả phiên (docs/07 mục 7).
 */
export function refreshSession(): Promise<TokenResponse> {
  if (!refreshInFlight) {
    refreshInFlight = authClient
      .post<ApiResponse<TokenResponse>>('/refresh')
      .then((response) => {
        sessionStore.setAccessToken(response.data.data.accessToken);
        return response.data.data;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

httpClient.interceptors.request.use((config) => {
  const token = sessionStore.getAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  const branchId = sessionStore.getWorkingBranchId();
  if (branchId !== null) {
    config.headers.set(BRANCH_HEADER, String(branchId));
  }
  return config;
});

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const apiError = toApiError(error);
    const config = error.config as RetriableConfig | undefined;

    if (apiError.status === 401 && config && !config._retried) {
      config._retried = true;
      try {
        await refreshSession();
      } catch {
        sessionStore.emit('expired');
        return Promise.reject(apiError);
      }
      // Gửi lại đúng request cũ (kể cả Idempotency-Key) với access token mới.
      return httpClient.request(config);
    }
    if (apiError.status === 403 && apiError.code === 'PASSWORD_CHANGE_REQUIRED') {
      sessionStore.emit('passwordChangeRequired');
    }
    return Promise.reject(apiError);
  },
);

/**
 * Sinh key chống xử lý trùng cho một thao tác người dùng (BR-GEN-50).
 * Tạo một lần khi người dùng mở thao tác; dùng lại cùng key khi gửi lại thao tác đó.
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
