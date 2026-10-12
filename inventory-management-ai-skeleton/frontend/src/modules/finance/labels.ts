import type { PartnerRole } from '../masterdata/types';
import type {
  AdjustmentDocumentType,
  AllocationMode,
  CashAccountType,
  CashDocumentType,
  CashPurpose,
  DebtEntryType,
  DebtItemStatus,
  DebtType,
  FinanceDocumentStatus,
  OffsetType,
  ReconciliationStatus,
} from './types';

export const DEBT_TYPES: Record<DebtType, string> = {
  RECEIVABLE: 'Phải thu',
  PAYABLE: 'Phải trả',
  ADVANCE: 'Tạm ứng',
};

/** Vai trò đối tác để chọn đối tượng công nợ (P-07). */
export const DEBT_PARTNER_ROLE: Record<DebtType, PartnerRole> = {
  RECEIVABLE: 'CUSTOMER',
  PAYABLE: 'SUPPLIER',
  ADVANCE: 'EMPLOYEE',
};

export const DEBT_ITEM_STATUSES: Record<DebtItemStatus, { label: string; color: string }> = {
  OPEN: { label: 'Chưa thanh toán', color: 'gold' },
  PARTIAL: { label: 'Thanh toán một phần', color: 'blue' },
  SETTLED: { label: 'Đã tất toán', color: 'green' },
  REVERSED: { label: 'Đã đảo', color: 'default' },
};

export const DEBT_ENTRY_TYPES: Record<DebtEntryType, string> = {
  OPENING: 'Công nợ đầu kỳ',
  ADVANCE: 'Tạm ứng',
  INVOICE: 'Hóa đơn',
  RECEIPT: 'Thu tiền',
  PAYMENT: 'Chi tiền',
  RETURN: 'Trả hàng',
  DISCOUNT: 'Giảm giá, chiết khấu',
  OFFSET: 'Đối trừ',
  ADJUSTMENT: 'Điều chỉnh',
  PREPAYMENT: 'Trả trước',
  REVERSAL: 'Đảo',
};

export const CASH_ACCOUNT_TYPES: Record<CashAccountType, string> = {
  CASH: 'Tiền mặt',
  BANK: 'Ngân hàng',
};

export const CASH_DOCUMENT_TYPES: Record<CashDocumentType, string> = {
  PT: 'Phiếu thu',
  PC: 'Phiếu chi',
  BC: 'Giấy báo có',
  UNC: 'Ủy nhiệm chi',
  CTN: 'Chuyển tiền nội bộ',
};

export const CASH_PURPOSES: Record<CashPurpose, string> = {
  CUSTOMER_RECEIPT: 'Thu tiền khách hàng',
  SUPPLIER_PAYMENT: 'Trả tiền nhà cung cấp',
  OTHER_RECEIPT: 'Thu khác',
  OTHER_PAYMENT: 'Chi khác',
  INTERNAL_TRANSFER: 'Chuyển tiền nội bộ',
  EMPLOYEE_ADVANCE: 'Chi tạm ứng nhân viên',
  ADVANCE_RETURN: 'Nhân viên hoàn ứng',
};

/** Nội dung hợp lệ theo loại chứng từ và loại quỹ dùng được (khớp CashDocumentType phía server). */
export const CASH_TYPE_RULES: Record<
  CashDocumentType,
  { purposes: CashPurpose[]; accountType: CashAccountType | null }
> = {
  PT: { purposes: ['CUSTOMER_RECEIPT', 'OTHER_RECEIPT', 'ADVANCE_RETURN'], accountType: 'CASH' },
  BC: { purposes: ['CUSTOMER_RECEIPT', 'OTHER_RECEIPT', 'ADVANCE_RETURN'], accountType: 'BANK' },
  PC: { purposes: ['SUPPLIER_PAYMENT', 'OTHER_PAYMENT', 'EMPLOYEE_ADVANCE'], accountType: 'CASH' },
  UNC: { purposes: ['SUPPLIER_PAYMENT', 'OTHER_PAYMENT', 'EMPLOYEE_ADVANCE'], accountType: 'BANK' },
  CTN: { purposes: ['INTERNAL_TRANSFER'], accountType: null },
};

/** Loại công nợ giảm theo nội dung; {@code null} = thu chi không qua công nợ. */
export const PURPOSE_DEBT_TYPE: Record<CashPurpose, DebtType | null> = {
  CUSTOMER_RECEIPT: 'RECEIVABLE',
  SUPPLIER_PAYMENT: 'PAYABLE',
  OTHER_RECEIPT: null,
  OTHER_PAYMENT: null,
  INTERNAL_TRANSFER: null,
  EMPLOYEE_ADVANCE: null,
  ADVANCE_RETURN: 'ADVANCE',
};

/** Chi tạm ứng: đối tượng là nhân viên, tạo công nợ tạm ứng (không phân bổ). */
export const PURPOSE_CREATED_DEBT: Partial<Record<CashPurpose, DebtType>> = {
  EMPLOYEE_ADVANCE: 'ADVANCE',
};

export const ALLOCATION_MODES: Record<AllocationMode, string> = {
  AUTO: 'Tự động: hóa đơn đến hạn sớm nhất trước',
  MANUAL: 'Chỉ định hóa đơn',
};

export const OFFSET_TYPES: Record<OffsetType, string> = {
  PREPAYMENT: 'Trả trước với hóa đơn',
  AR_AP: 'Phải thu với phải trả',
};

export const ADJUSTMENT_DOCUMENT_TYPES: Record<AdjustmentDocumentType, string> = {
  DCCN: 'Điều chỉnh công nợ',
  TTTU: 'Thanh toán tạm ứng',
};

export const RECONCILIATION_STATUSES: Record<ReconciliationStatus, { label: string; color: string }> = {
  PENDING: { label: 'Chờ xác nhận', color: 'gold' },
  MATCHED: { label: 'Khớp', color: 'green' },
  DIFFERENT: { label: 'Lệch', color: 'red' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
};

/** Thao tác chứng từ ghi sổ theo trạng thái và quyền {@code <prefix>:create|approve|post|reverse}. */
export function financeActions(
  status: FinanceDocumentStatus,
  prefix: string,
  can: (permission: string) => boolean,
): string[] {
  const create = can(`${prefix}:create`);
  const approve = can(`${prefix}:approve`);
  const actions: Record<FinanceDocumentStatus, string[]> = {
    DRAFT: create ? ['submit', 'cancel'] : [],
    REJECTED: create ? ['submit', 'cancel'] : [],
    SUBMITTED: [...(approve ? ['approve', 'reject'] : []), ...(create ? ['cancel'] : [])],
    APPROVED: [...(can(`${prefix}:post`) ? ['post'] : []), ...(create ? ['cancel'] : [])],
    POSTED: can(`${prefix}:reverse`) ? ['reverse'] : [],
    CANCELLED: [],
    REVERSED: [],
  };
  return actions[status];
}

export const FINANCE_STATUSES: FinanceDocumentStatus[] = [
  'DRAFT',
  'SUBMITTED',
  'REJECTED',
  'APPROVED',
  'POSTED',
  'CANCELLED',
  'REVERSED',
];
