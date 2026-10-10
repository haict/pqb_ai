import type { FormInstance } from 'antd';
import { ApiError } from './api/apiError';

/**
 * Hiển thị lỗi server cạnh trường tương ứng (errors[].field) nếu trường có trên form.
 * Trả về thông báo cần hiện chung cho form, hoặc {@code null} khi mọi lỗi đã gắn vào trường.
 */
export function applyServerErrors(form: FormInstance, error: unknown, fieldNames: string[]): string | null {
  if (!(error instanceof ApiError)) {
    return 'Đã xảy ra lỗi không xác định.';
  }
  const known = new Set(fieldNames);
  const fieldErrors = error.fieldErrors.filter((fieldError) => known.has(fieldError.field));
  if (fieldErrors.length > 0) {
    form.setFields(
      fieldErrors.map((fieldError) => ({ name: fieldError.field, errors: [fieldError.message] })),
    );
  }
  const allAttached = fieldErrors.length > 0 && fieldErrors.length === error.fieldErrors.length;
  return allAttached ? null : error.message;
}

/** Đổi {@code null} từ API thành {@code undefined} để đưa vào form (input không nhận null). */
export function formValues<T extends object>(item: T): Partial<{ [K in keyof T]: Exclude<T[K], null> }> {
  return Object.fromEntries(Object.entries(item).filter(([, value]) => value !== null)) as Partial<{
    [K in keyof T]: Exclude<T[K], null>;
  }>;
}

/**
 * Kiểm tra form; trường sai thì antd đã hiển thị lỗi tại trường, trả {@code null} để dừng gửi (không để lời hứa bị
 * từ chối mà không xử lý).
 */
export async function validated<T>(form: FormInstance<T>): Promise<T | null> {
  try {
    return await form.validateFields();
  } catch {
    return null;
  }
}
