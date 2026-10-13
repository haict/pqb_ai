import type {
  ComplaintDecision,
  ComplaintStatus,
  DeliveryStatus,
  PaymentDueBasis,
  SalesDocumentStatus,
  SalesOrder,
  SalesReportGrouping,
  SalesReturn,
  SalesReturnType,
} from './types';

type Can = (permission: string) => boolean;

export const POSTING_STATUSES: SalesDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];

export const DELIVERY_STATUSES: Record<DeliveryStatus, { label: string; color: string }> = {
  PENDING: { label: 'Chờ giao', color: 'gold' },
  IN_TRANSIT: { label: 'Đang giao', color: 'cyan' },
  DELIVERED: { label: 'Đã giao', color: 'green' },
};

export const RETURN_TYPES: Record<SalesReturnType, string> = {
  TLB: 'Hàng bán bị trả lại',
  GGB: 'Giảm giá hàng bán',
};

export const DUE_BASES: Record<PaymentDueBasis, string> = {
  ORDER_DATE: 'Từ ngày đơn',
  DELIVERY: 'Sau khi khách nhận hàng',
  INVOICE: 'Sau ngày hóa đơn',
  ACCEPTANCE: 'Sau nghiệm thu',
};

export const COMPLAINT_STATUSES: Record<ComplaintStatus, { label: string; color: string }> = {
  OPEN: { label: 'Mới tiếp nhận', color: 'gold' },
  INSPECTED: { label: 'Đã kiểm tra', color: 'blue' },
  RESOLVED: { label: 'Đã xử lý', color: 'green' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
};

export const DECISIONS: Record<ComplaintDecision, string> = {
  EXCHANGE: 'Đổi hàng',
  REPAIR: 'Sửa chữa',
  RETURN: 'Trả hàng',
  WARRANTY: 'Bảo hành',
  REJECT: 'Từ chối',
};

export const GROUPINGS: Record<SalesReportGrouping, string> = {
  SALES_USER: 'Nhân viên kinh doanh',
  CUSTOMER: 'Khách hàng',
  PRODUCT: 'Hàng hóa',
  BRANCH: 'Chi nhánh',
};

export const PRICE_SOURCES: Record<string, string> = {
  CUSTOMER: 'Bảng giá riêng của khách',
  CUSTOMER_GROUP: 'Bảng giá nhóm khách / đại lý',
  LIST: 'Giá niêm yết',
};

/**
 * Thao tác đơn bán theo trạng thái, trạng thái giao hàng và quyền (server kiểm tra lại). Khách nhận hàng và số hóa đơn
 * điện tử cần nhập thông tin nên có nút riêng trên màn hình.
 */
export function orderActions(order: SalesOrder, can: Can): string[] {
  const create = can('sales-order:create');
  const actions: Partial<Record<SalesDocumentStatus, string[]>> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(can('sales-order:approve') ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [
      ...(can('sales-order:post') ? ['post'] : []),
      ...(create ? ['reserve', 'release', 'cancel'] : []),
    ],
    POSTED: [
      ...(can('sales-order:deliver') && order.deliveryStatus === 'PENDING' ? ['ship'] : []),
      ...(can('sales-order:reverse') ? ['reverse'] : []),
    ],
  };
  return actions[order.status] ?? [];
}

export const ORDER_ACTION_ORDER = [
  'submit',
  'approve',
  'reject',
  'post',
  'reserve',
  'release',
  'ship',
  'reverse',
  'cancel',
];

/** Thao tác chứng từ ghi sổ (trả hàng / giảm giá) theo trạng thái và quyền {@code <prefix>:*}. */
export function postingActions(status: SalesDocumentStatus, prefix: string, can: Can): string[] {
  const create = can(`${prefix}:create`);
  const actions: Partial<Record<SalesDocumentStatus, string[]>> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(can(`${prefix}:approve`) ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [...(can(`${prefix}:post`) ? ['post'] : []), ...(create ? ['cancel'] : [])],
    POSTED: can(`${prefix}:reverse`) ? ['reverse'] : [],
  };
  return actions[status] ?? [];
}

/** Thao tác trả hàng / giảm giá; đổi hàng (lập đơn bán mới) cho phiếu trả hàng đã ghi sổ. */
export function returnActions(document: SalesReturn, can: Can): string[] {
  const actions = postingActions(document.status, 'sales-return', can);
  if (
    document.status === 'POSTED' &&
    document.documentType === 'TLB' &&
    can('sales-return:create') &&
    can('sales-order:create')
  ) {
    return [...actions, 'create-exchange-order'];
  }
  return actions;
}

export const RETURN_ACTION_ORDER = [
  'submit',
  'approve',
  'reject',
  'post',
  'create-exchange-order',
  'reverse',
  'cancel',
];
