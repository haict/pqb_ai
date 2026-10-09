import dayjs from 'dayjs';

/** Server lưu UTC; hiển thị theo múi giờ của máy người dùng (UTC+7 tại Việt Nam). */
export function formatDateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('DD/MM/YYYY HH:mm') : '';
}
