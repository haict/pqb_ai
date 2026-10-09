import axios from 'axios';
import { toApiError } from './apiError';

export interface SystemHealth {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';
}

export async function fetchSystemHealth(): Promise<SystemHealth> {
  try {
    const response = await axios.get<SystemHealth>('/erp/actuator/health', { timeout: 10_000 });
    return response.data;
  } catch (error) {
    // Actuator trả 503 kèm {status: DOWN} khi một thành phần (ví dụ DB) lỗi.
    if (axios.isAxiosError(error) && error.response?.status === 503) {
      return { status: 'DOWN' };
    }
    throw toApiError(error);
  }
}
