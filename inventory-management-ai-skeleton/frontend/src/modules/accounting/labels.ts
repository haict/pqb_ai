import type { BalanceSide, JournalEntryKind, JournalEntryStatus } from './types';

export const BALANCE_SIDES: Record<BalanceSide, string> = {
  DEBIT: 'Dư Nợ',
  CREDIT: 'Dư Có',
  BOTH: 'Lưỡng tính',
};

export const JOURNAL_KINDS: Record<JournalEntryKind, string> = {
  AUTO: 'Tự sinh từ chứng từ',
  MANUAL: 'Nhập tay',
  COST: 'Giá vốn',
  CLOSING: 'Kết chuyển',
};

export const JOURNAL_STATUSES: Record<JournalEntryStatus, { label: string; color: string }> = {
  POSTED: { label: 'Đã ghi sổ', color: 'green' },
  REVERSED: { label: 'Đã đảo', color: 'default' },
};

/** Tiền hiển thị làm tròn đến đồng (D-05); số liệu do server tính, client chỉ định dạng. */
export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value);
}
