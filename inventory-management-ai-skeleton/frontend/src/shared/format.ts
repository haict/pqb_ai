import dayjs from 'dayjs';

/** Server lưu UTC; hiển thị theo múi giờ của máy người dùng (UTC+7 tại Việt Nam). */
export function formatDateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('DD/MM/YYYY HH:mm') : '';
}

/** Số lẻ hiển thị toàn hệ thống (D-57): số lượng luôn 2 số lẻ, giá trị tiền làm tròn đến đồng. */
export const QUANTITY_DIGITS = 2;
export const AMOUNT_DIGITS = 0;

/**
 * Số kiểu Việt Nam với đúng {@code digits} số lẻ (114.300,00); số âm trong ngoặc ((5.955,00)). Chỉ định dạng hiển thị,
 * không làm tròn số liệu server tính.
 */
export function formatFixed(value: number, digits: number): string {
  const text = new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Math.abs(value));
  return value < 0 && text !== new Intl.NumberFormat('vi-VN', { minimumFractionDigits: digits }).format(0)
    ? `(${text})`
    : text;
}

export function formatQuantity(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : formatFixed(value, QUANTITY_DIGITS);
}

export function formatAmount(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : formatFixed(value, AMOUNT_DIGITS);
}
