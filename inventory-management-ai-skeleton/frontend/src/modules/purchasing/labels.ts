import type { CostAllocationMethod, PurchaseDocumentStatus, PurchaseReturnType } from './types';

export const PURCHASE_STATUSES: PurchaseDocumentStatus[] = [
  'DRAFT',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];

/**
 * Thao tác chứng từ mua (đơn mua, trả hàng, phân bổ) theo trạng thái và quyền {@code <prefix>:*} (chỉ hỗ trợ hiển thị;
 * server kiểm tra lại). Không qua duyệt (D-61): ghi sổ thẳng từ nháp; chứng từ chờ duyệt / bị từ chối trước D-61 chỉ
 * còn hủy, đã duyệt vẫn ghi sổ được.
 */
export function postingActions(
  status: PurchaseDocumentStatus,
  prefix: string,
  can: (permission: string) => boolean,
): string[] {
  const cancel = can(`${prefix}:create`) ? ['cancel'] : [];
  const post = can(`${prefix}:post`) ? ['post'] : [];
  const actions: Partial<Record<PurchaseDocumentStatus, string[]>> = {
    DRAFT: [...post, ...cancel],
    REJECTED: cancel,
    SUBMITTED: cancel,
    APPROVED: [...post, ...cancel],
    POSTED: can(`${prefix}:reverse`) ? ['reverse'] : [],
  };
  return actions[status] ?? [];
}

/** Thao tác đơn mua: như chứng từ ghi sổ, thêm bỏ ghi sổ về nháp để sửa (D-62). */
export function orderActions(status: PurchaseDocumentStatus, can: (permission: string) => boolean): string[] {
  const actions = postingActions(status, 'purchase-order', can);
  return status === 'POSTED' && can('purchase-order:unpost') ? ['unpost', ...actions] : actions;
}

export const RETURN_TYPES: Record<PurchaseReturnType, string> = {
  THN: 'Trả hàng nhà cung cấp',
  GGM: 'Giảm giá hàng mua',
};

export const ALLOCATION_METHODS: Record<CostAllocationMethod, string> = {
  VALUE: 'Theo giá trị nhập',
  QUANTITY: 'Theo số lượng',
};
