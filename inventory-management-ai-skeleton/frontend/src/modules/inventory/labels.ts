import type { InventoryDocumentStatus, InventoryDocumentType, SerialStatus, TransactionType } from './types';
import { formatAmount, formatQuantity } from '../../shared/format';

export const DOCUMENT_TYPES: Record<InventoryDocumentType, string> = {
  PNK: 'Phiếu nhập kho',
  PXK: 'Phiếu xuất kho nội bộ',
  PXH: 'Phiếu xuất hủy',
  PCK: 'Phiếu chuyển kho',
  PDC: 'Phiếu điều chỉnh tồn',
  PCL: 'Chuyển trạng thái chất lượng',
  PDK: 'Chứng từ đảo',
};

/** Loại chứng từ người dùng lập trực tiếp (chứng từ đảo chỉ sinh từ thao tác đảo). */
export const CREATABLE_TYPES: InventoryDocumentType[] = ['PNK', 'PXK', 'PXH', 'PCK', 'PDC', 'PCL'];

/** Loại nghiệp vụ của phiếu nhập kho người dùng chọn được; để trống là nhập kho thông thường. */
export const OPENING_SUB_TYPE = 'OPENING';
export const RECEIPT_SUB_TYPES: Record<string, string> = {
  [OPENING_SUB_TYPE]: 'Tồn kho đầu kỳ',
  RECALL_IN: 'Nhập hàng thu hồi từ khách',
};

export const DOCUMENT_TYPE_PERMISSION: Record<InventoryDocumentType, string> = {
  PNK: 'inventory-receipt',
  PXK: 'inventory-issue',
  PXH: 'inventory-issue',
  PCK: 'inventory-transfer',
  PDC: 'inventory-adjustment',
  PCL: 'inventory-adjustment',
  PDK: 'inventory-reversal',
};

export const STATUS_TAGS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: 'Nháp', color: 'default' },
  SUBMITTED: { label: 'Chờ duyệt', color: 'gold' },
  APPROVED: { label: 'Đã duyệt', color: 'blue' },
  REJECTED: { label: 'Bị từ chối', color: 'red' },
  IN_TRANSIT: { label: 'Đang vận chuyển', color: 'cyan' },
  POSTED: { label: 'Đã ghi sổ', color: 'green' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
  REVERSED: { label: 'Đã đảo', color: 'purple' },
  COUNTING: { label: 'Đang đếm', color: 'orange' },
  ACTIVE: { label: 'Đang thu hồi', color: 'magenta' },
  CLOSED: { label: 'Đã đóng', color: 'default' },
  IN_PROGRESS: { label: 'Đang thực hiện', color: 'processing' },
  COMPLETED: { label: 'Hoàn thành', color: 'green' },
  SENT: { label: 'Đã gửi khách', color: 'cyan' },
  ACCEPTED: { label: 'Khách chấp nhận', color: 'green' },
  DECLINED: { label: 'Khách từ chối', color: 'volcano' },
  EXPIRED: { label: 'Hết hạn', color: 'default' },
};

export const DOCUMENT_STATUSES: InventoryDocumentStatus[] = [
  'DRAFT',
  'IN_TRANSIT',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];

export function statusLabel(status: string): string {
  return STATUS_TAGS[status]?.label ?? status;
}

export const TRANSACTION_TYPES: Record<TransactionType, string> = {
  OPENING: 'Nhập kho',
  RECALL_IN: 'Nhập thu hồi',
  INTERNAL_ISSUE: 'Xuất nội bộ',
  SCRAP: 'Xuất hủy',
  TRANSFER_OUT: 'Chuyển đi',
  TRANSFER_IN: 'Chuyển đến',
  ADJUST_IN: 'Điều chỉnh tăng',
  ADJUST_OUT: 'Điều chỉnh giảm',
  COUNT_GAIN: 'Kiểm kê thừa',
  COUNT_LOSS: 'Kiểm kê thiếu',
  STATUS_OUT: 'Đổi trạng thái (ra)',
  STATUS_IN: 'Đổi trạng thái (vào)',
  REVERSAL: 'Đảo',
};

export const SERIAL_STATUSES: Record<SerialStatus, string> = {
  IN_STOCK: 'Trong kho',
  ISSUED: 'Đã xuất',
  SCRAPPED: 'Đã hủy',
};

export const ACTION_LABELS: Record<string, string> = {
  activate: 'Kích hoạt',
  cancel: 'Hủy',
  delete: 'Xóa',
  post: 'Ghi sổ',
  receive: 'Nhận hàng',
  reverse: 'Đảo chứng từ',
  unpost: 'Bỏ ghi sổ',
  start: 'Bắt đầu đếm',
  close: 'Đóng',
  'stock-in': 'Nhập kho',
  reserve: 'Giữ hàng',
  release: 'Nhả hàng',
  ship: 'Đang giao',
  'create-exchange-order': 'Lập đơn bán đổi hàng',
};

/** Chứng từ nguồn của phiếu kho sinh từ phân hệ khác (D-54). */
export const SOURCE_TYPES: Record<string, string> = {
  DMH: 'Đơn mua',
  DBH: 'Đơn bán',
  THN: 'Trả hàng nhà cung cấp',
  TLB: 'Hàng bán bị trả lại',
};

/** Hành động bắt buộc lý do (api.md mục 3). */
export const REASON_ACTIONS = new Set(['cancel', 'delete', 'reverse', 'unpost', 'close']);

/**
 * Định dạng theo Việt Nam: không truyền số lẻ là số lượng (2 số lẻ), 0 là giá trị tiền (D-57); số khác (đơn giá,
 * phần trăm) hiển thị tối đa số lẻ đó. Không làm tròn số liệu do server tính.
 */
export function formatNumber(value: number | null | undefined, maximumFractionDigits?: number): string {
  if (value === null || value === undefined) {
    return '';
  }
  if (maximumFractionDigits === undefined) {
    return formatQuantity(value);
  }
  if (maximumFractionDigits === 0) {
    return formatAmount(value);
  }
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits }).format(value);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const [year, month, day] = value.split('-');
  return `${day}/${month}/${year}`;
}
