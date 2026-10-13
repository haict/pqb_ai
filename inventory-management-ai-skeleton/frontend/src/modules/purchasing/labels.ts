import type { CostAllocationMethod, PurchaseDocumentStatus, PurchaseReturnType } from './types';

export const PURCHASE_STATUSES: PurchaseDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];

/**
 * Thao tác chứng từ mua (đơn mua, trả hàng, phân bổ) theo trạng thái và quyền {@code <prefix>:*} (chỉ hỗ trợ hiển thị;
 * server kiểm tra lại).
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

export const RETURN_TYPES: Record<PurchaseReturnType, string> = {
  THN: 'Trả hàng nhà cung cấp',
  GGM: 'Giảm giá hàng mua',
};

export const ALLOCATION_METHODS: Record<CostAllocationMethod, string> = {
  VALUE: 'Theo giá trị nhập',
  QUANTITY: 'Theo số lượng',
};
