import type {
  CostAllocationMethod,
  InspectionResult,
  PurchaseDocumentStatus,
  PurchaseReceipt,
  PurchaseReturnType,
} from './types';

export const PURCHASE_STATUSES: PurchaseDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'IN_PROGRESS',
  'COMPLETED',
  'CLOSED',
  'CANCELLED',
];

/**
 * Thao tác theo trạng thái và quyền {@code <prefix>:create|approve|close} (chỉ hỗ trợ hiển thị; server kiểm tra lại).
 * Hủy khi chưa phát sinh chứng từ đích (server chặn nếu đã có); đóng phần còn lại khi đã duyệt.
 */
export function purchaseActions(
  status: PurchaseDocumentStatus,
  prefix: string,
  can: (permission: string) => boolean,
): string[] {
  const create = can(`${prefix}:create`);
  const approve = can(`${prefix}:approve`);
  const close = can(`${prefix}:close`);
  const actions: Record<PurchaseDocumentStatus, string[]> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(approve ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [...(close ? ['close'] : []), ...(create ? ['cancel'] : [])],
    IN_PROGRESS: close ? ['close'] : [],
    COMPLETED: [],
    CLOSED: [],
    CANCELLED: [],
    POSTED: [],
    REVERSED: [],
  };
  return actions[status];
}

export const RECEIPT_STATUSES: PurchaseDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];

export const INSPECTION_RESULTS: Record<InspectionResult, string> = {
  ACCEPTED: 'Đạt - nhập kho',
  QUARANTINE: 'Lỗi - nhập khu cách ly',
  RETURN: 'Lỗi - trả nhà cung cấp',
};

/** Thao tác phiếu nhận hàng theo trạng thái và quyền {@code purchase-receipt:*} (server kiểm tra lại). */
export function receiptActions(receipt: PurchaseReceipt, can: (permission: string) => boolean): string[] {
  const create = can('purchase-receipt:create');
  const post = can('purchase-receipt:post');
  const actions: Partial<Record<PurchaseDocumentStatus, string[]>> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [
      ...(can('purchase-receipt:approve') ? ['approve', 'reject'] : []),
      ...(create ? ['cancel'] : []),
    ],
    APPROVED: [...(post ? ['post'] : []), ...(create ? ['cancel'] : [])],
    POSTED: [
      ...(post && !receipt.stockedAt ? ['stock-in'] : []),
      ...(can('purchase-receipt:reverse') ? ['reverse'] : []),
    ],
  };
  return actions[receipt.status] ?? [];
}

export const RECEIPT_ACTION_ORDER = ['submit', 'approve', 'reject', 'post', 'stock-in', 'reverse', 'cancel'];

/**
 * Thao tác chứng từ ghi sổ (hóa đơn, trả hàng, phân bổ...) theo trạng thái và quyền {@code <prefix>:*} (server kiểm tra
 * lại).
 */
export function postingActions(
  status: PurchaseDocumentStatus,
  prefix: string,
  can: (permission: string) => boolean,
): string[] {
  const create = can(`${prefix}:create`);
  const actions: Partial<Record<PurchaseDocumentStatus, string[]>> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(can(`${prefix}:approve`) ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [...(can(`${prefix}:post`) ? ['post'] : []), ...(create ? ['cancel'] : [])],
    POSTED: can(`${prefix}:reverse`) ? ['reverse'] : [],
  };
  return actions[status] ?? [];
}

/** Thao tác hóa đơn mua theo trạng thái và quyền {@code purchase-invoice:*} (server kiểm tra lại). */
export function invoiceActions(
  status: PurchaseDocumentStatus,
  can: (permission: string) => boolean,
): string[] {
  return postingActions(status, 'purchase-invoice', can);
}

export const RETURN_TYPES: Record<PurchaseReturnType, string> = {
  THN: 'Trả hàng nhà cung cấp',
  GGM: 'Giảm giá hàng mua',
};

export const ALLOCATION_METHODS: Record<CostAllocationMethod, string> = {
  VALUE: 'Theo giá trị nhập',
  QUANTITY: 'Theo số lượng',
};
