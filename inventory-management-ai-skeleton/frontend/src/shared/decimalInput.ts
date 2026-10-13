/**
 * Định dạng ô nhập số thập phân theo Việt Nam: dấu chấm phân cách hàng nghìn, dấu phẩy thập phân, không thêm số 0
 * thừa (antd `precision` hiển thị 1 thành "1.000", dễ đọc nhầm là một nghìn).
 */
export function formatDecimalInput(value: string | number | undefined, fractionDigits: number): string {
  if (value === undefined || value === '') {
    return '';
  }
  const number = Number(value);
  if (Number.isNaN(number)) {
    return String(value);
  }
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: fractionDigits }).format(number);
}

/** Đọc chuỗi người dùng gõ theo định dạng Việt Nam; cắt phần thập phân vượt số chữ số cho phép. */
export function parseDecimalInput(text: string | undefined, fractionDigits: number): string {
  const normalized = (text ?? '')
    .replace(/\./g, '')
    .replace(',', '.')
    .replace(/[^\d.-]/g, '');
  const [integer = '', fraction] = normalized.split('.');
  return fraction === undefined ? integer : `${integer}.${fraction.slice(0, fractionDigits)}`;
}
