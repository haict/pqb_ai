import type {
  ComplaintDecision,
  ComplaintStatus,
  DeliveryStatus,
  PaymentDueBasis,
  SalesDelivery,
  SalesDocumentStatus,
  SalesReportGrouping,
  SalesReturn,
  SalesReturnType,
} from './types';

type Can = (permission: string) => boolean;

export const QUOTE_STATUSES: SalesDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'SENT',
  'ACCEPTED',
  'DECLINED',
  'EXPIRED',
  'CLOSED',
  'CANCELLED',
];

export const ORDER_STATUSES: SalesDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'IN_PROGRESS',
  'COMPLETED',
  'CLOSED',
  'CANCELLED',
];

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
  FAILED: { label: 'Giao thất bại', color: 'red' },
};

export const RETURN_TYPES: Record<SalesReturnType, string> = {
  TLB: 'Hàng bán bị trả lại',
  GGB: 'Giảm giá hàng bán',
};

export const DUE_BASES: Record<PaymentDueBasis, string> = {
  ORDER_DATE: 'Từ ngày đơn',
  DELIVERY: 'Sau khi giao hàng',
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

/** Thao tác báo giá theo trạng thái và quyền (server kiểm tra lại). */
export function quoteActions(status: SalesDocumentStatus, can: Can): string[] {
  const create = can('sales-quote:create');
  const approve = can('sales-quote:approve');
  const actions: Partial<Record<SalesDocumentStatus, string[]>> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(approve ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: create ? ['send', 'accept', 'decline', 'revise', 'cancel'] : [],
    SENT: create ? ['accept', 'decline', 'revise'] : [],
    DECLINED: create ? ['revise'] : [],
    EXPIRED: create ? ['revise'] : [],
  };
  return actions[status] ?? [];
}

export const QUOTE_ACTION_ORDER = [
  'submit',
  'approve',
  'reject',
  'send',
  'accept',
  'decline',
  'revise',
  'cancel',
];

/** Thao tác đơn bán theo trạng thái và quyền. */
export function orderActions(status: SalesDocumentStatus, can: Can): string[] {
  const create = can('sales-order:create');
  const open = [
    ...(create ? ['reserve', 'release', 'create-purchase-request'] : []),
    ...(can('sales-order:close') ? ['close'] : []),
  ];
  const actions: Partial<Record<SalesDocumentStatus, string[]>> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(can('sales-order:approve') ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [...open, ...(create ? ['cancel'] : [])],
    IN_PROGRESS: open,
  };
  return actions[status] ?? [];
}

export const ORDER_ACTION_ORDER = [
  'submit',
  'approve',
  'reject',
  'reserve',
  'release',
  'create-purchase-request',
  'close',
  'cancel',
];

/** Thao tác phiếu giao theo trạng thái chứng từ, trạng thái giao và quyền. */
export function deliveryActions(delivery: SalesDelivery, can: Can): string[] {
  const create = can('sales-delivery:create');
  const deliver = can('sales-delivery:deliver');
  const reverse = can('sales-delivery:reverse') ? ['reverse'] : [];
  if (delivery.status === 'POSTED') {
    if (delivery.deliveryStatus === 'PENDING') {
      return [...(deliver ? ['ship', 'fail'] : []), ...reverse];
    }
    if (delivery.deliveryStatus === 'IN_TRANSIT') {
      return [...(deliver ? ['fail'] : []), ...reverse];
    }
    return [];
  }
  const actions: Partial<Record<SalesDocumentStatus, string[]>> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [
      ...(can('sales-delivery:approve') ? ['approve', 'reject'] : []),
      ...(create ? ['cancel'] : []),
    ],
    APPROVED: [...(can('sales-delivery:post') ? ['post'] : []), ...(create ? ['cancel'] : [])],
  };
  return actions[delivery.status] ?? [];
}

export const DELIVERY_ACTION_ORDER = [
  'submit',
  'approve',
  'reject',
  'post',
  'ship',
  'fail',
  'reverse',
  'cancel',
];

/** Thao tác chứng từ ghi sổ (hóa đơn, trả hàng) theo trạng thái và quyền {@code <prefix>:*}. */
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

/** Thao tác trả hàng / giảm giá; đổi hàng cho phiếu trả hàng đã ghi sổ. */
export function returnActions(document: SalesReturn, can: Can): string[] {
  const actions = postingActions(document.status, 'sales-return', can);
  if (document.status === 'POSTED' && document.documentType === 'TLB' && can('sales-delivery:create')) {
    return [...actions, 'create-exchange-delivery'];
  }
  return actions;
}

export const RETURN_ACTION_ORDER = [
  'submit',
  'approve',
  'reject',
  'post',
  'create-exchange-delivery',
  'reverse',
  'cancel',
];
