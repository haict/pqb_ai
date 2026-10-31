import type { RecordStatus } from '../components/StatusTag';
import { httpClient, IDEMPOTENCY_HEADER } from './httpClient';
import type { ApiResponse, PageResponse } from './types';

/** Hàm gọi REST dùng chung cho các phân hệ (định dạng phản hồi chuẩn docs/04 mục 1). */

export function withoutEmpty(params: object): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== '' && value !== null),
  );
}

/** Danh sách ID gửi dạng "1,2,3": Spring đọc thành List; axios mặc định gửi "ids[]=" không khớp. */
export function idList(ids: number[] | undefined): string | undefined {
  return ids && ids.length > 0 ? ids.join(',') : undefined;
}

export async function getPage<T>(url: string, params: object): Promise<PageResponse<T>> {
  const response = await httpClient.get<ApiResponse<PageResponse<T>>>(url, { params: withoutEmpty(params) });
  return response.data.data;
}

export async function getOne<T>(url: string, params: object = {}): Promise<T> {
  const response = await httpClient.get<ApiResponse<T>>(url, { params: withoutEmpty(params) });
  return response.data.data;
}

/** Lệnh tạo / hành động: luôn gửi Idempotency-Key (BR-GEN-50). */
export async function postWithKey<T>(
  url: string,
  body: object | undefined,
  idempotencyKey: string,
): Promise<T> {
  const response = await httpClient.post<ApiResponse<T>>(url, body ?? {}, {
    headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });
  return response.data.data;
}

/** POST không cần chống trùng (thao tác tự idempotent ở server). */
export async function postJson<T>(url: string, body: object): Promise<T> {
  const response = await httpClient.post<ApiResponse<T>>(url, body);
  return response.data.data;
}

export async function putJson<T>(url: string, body: object): Promise<T> {
  const response = await httpClient.put<ApiResponse<T>>(url, body);
  return response.data.data;
}

export async function patchStatus<T>(
  url: string,
  status: RecordStatus,
  version: number,
  reason: string,
): Promise<T> {
  const response = await httpClient.patch<ApiResponse<T>>(`${url}/status`, { status, version, reason });
  return response.data.data;
}

/** DELETE có nội dung (lý do) và Idempotency-Key: xóa chứng từ chưa từng ghi sổ (D-65). */
export async function deleteWithKey<T>(
  url: string,
  body: object | undefined,
  idempotencyKey: string,
): Promise<T> {
  const response = await httpClient.delete<ApiResponse<T>>(url, {
    data: body ?? {},
    headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });
  return response.data.data;
}

export async function deleteJson(url: string): Promise<void> {
  await httpClient.delete(url);
}

/** Tải file (mẫu Excel...) qua client có xác thực rồi lưu về máy. */
export async function downloadFile(url: string, fileName: string): Promise<void> {
  const response = await httpClient.get<Blob>(url, { responseType: 'blob' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(response.data);
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(link.href);
}

/** Gửi file multipart (nhập Excel) kèm Idempotency-Key. */
export async function uploadFile<T>(
  url: string,
  file: File,
  params: object,
  idempotencyKey: string,
): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  const response = await httpClient.post<ApiResponse<T>>(url, form, {
    params: withoutEmpty(params),
    // Ghi đè Content-Type JSON mặc định: nếu giữ, axios chuyển FormData thành JSON và backend trả 415.
    headers: { [IDEMPOTENCY_HEADER]: idempotencyKey, 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}
