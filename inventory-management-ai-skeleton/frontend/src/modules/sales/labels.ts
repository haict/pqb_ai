import type {
  ComplaintDecision,
  ComplaintStatus,
  DeliveryStatus,
  PaymentDueBasis,
  SalesDocumentStatus,
  SalesOrder,
  SalesLedgerGrouping,
  SalesReportGrouping,
  SalesReturn,
  SalesReturnType,
} from './types';

type Can = (permission: string) => boolean;

export const POSTING_STATUSES: SalesDocumentStatus[] = ['DRAFT', 'POSTED', 'CANCELLED', 'REVERSED'];

/** Trạng thái đơn bán: không còn "Đã hủy", đơn chưa từng ghi sổ được xóa hẳn (D-65). */
export const ORDER_STATUSES: SalesDocumentStatus[] = ['DRAFT', 'POSTED', 'REVERSED'];

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

/** "Thống kê theo" của sổ chi tiết bán hàng: nhóm ngoài khối từng hàng hóa. */
export const LEDGER_GROUPINGS: Record<SalesLedgerGrouping, string> = {
  NONE: '<<Không chọn>>',
  CUSTOMER: 'Khách hàng',
  PRODUCT_CATEGORY: 'Nhóm VTHH',
};

export const PRICE_SOURCES: Record<string, string> = {
  CUSTOMER: 'Bảng giá riêng của khách',
  CUSTOMER_GROUP: 'Bảng giá nhóm khách / đại lý',
  LIST: 'Giá niêm yết',
};

/**
 * Thao tác đơn bán theo trạng thái, trạng thái giao hàng và quyền (server kiểm tra lại). Khách nhận hàng và số hóa đơn
 * điện tử cần nhập thông tin nên có nút riêng trên màn hình. Không qua duyệt (D-61): ghi sổ, giữ / nhả hàng ngay trên
 * đơn nháp; đơn chờ duyệt / bị từ chối trước D-61 chỉ còn xóa. Xóa hẳn thay cho hủy (D-65); đơn nháp đã từng ghi sổ
 * vẫn hiện nút xóa, server chặn (409) và báo lý do.
 */
export function orderActions(order: SalesOrder, can: Can): string[] {
  const create = can('sales-order:create');
  const open = [
    ...(can('sales-order:post') ? ['post'] : []),
    ...(create ? ['reserve', 'release', 'delete'] : []),
  ];
  const actions: Partial<Record<SalesDocumentStatus, string[]>> = {
    DRAFT: open,
    REJECTED: create ? ['delete'] : [],
    SUBMITTED: create ? ['delete'] : [],
    APPROVED: open,
    POSTED: [
      ...(can('sales-order:deliver') && order.deliveryStatus === 'PENDING' ? ['ship'] : []),
      // D-62: bỏ ghi sổ chỉ khi chưa chuyển đi giao, chưa có hóa đơn điện tử.
      ...(can('sales-order:unpost') && order.deliveryStatus === 'PENDING' && !order.einvoiceNo
        ? ['unpost']
        : []),
      ...(can('sales-order:reverse') ? ['reverse'] : []),
    ],
  };
  return actions[order.status] ?? [];
}

/** Thao tác trên chi tiết đơn bán; bỏ ghi sổ, đảo đặt ở cột thao tác của danh sách ({@link ORDER_ROW_ACTION_ORDER}). */
export const ORDER_ACTION_ORDER = ['post', 'reserve', 'release', 'ship', 'delete'];
export const ORDER_ROW_ACTION_ORDER = ['unpost', 'reverse'];

/**
 * Thao tác chứng từ ghi sổ (trả hàng / giảm giá) theo trạng thái và quyền {@code <prefix>:*}. Không qua duyệt (D-61):
 * ghi sổ thẳng từ nháp; chứng từ chờ duyệt / bị từ chối trước D-61 chỉ còn hủy.
 */
export function postingActions(status: SalesDocumentStatus, prefix: string, can: Can): string[] {
  const cancel = can(`${prefix}:create`) ? ['cancel'] : [];
  const post = can(`${prefix}:post`) ? ['post'] : [];
  const actions: Partial<Record<SalesDocumentStatus, string[]>> = {
    DRAFT: [...post, ...cancel],
    REJECTED: cancel,
    SUBMITTED: cancel,
    APPROVED: [...post, ...cancel],
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

export const RETURN_ACTION_ORDER = ['post', 'create-exchange-order', 'reverse', 'cancel'];
