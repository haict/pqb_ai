import type { PageResponse } from '../../shared/api/types';
import type { ApprovalRequest } from '../system/types';

/** Kiểu dữ liệu khớp DTO của module công nợ (docs/04 mục M07). */

export type DebtType = 'RECEIVABLE' | 'PAYABLE' | 'ADVANCE';
export type DebtItemStatus = 'OPEN' | 'PARTIAL' | 'SETTLED' | 'REVERSED';
export type FinanceDocumentStatus =
  'DRAFT' | 'SUBMITTED' | 'REJECTED' | 'APPROVED' | 'POSTED' | 'CANCELLED' | 'REVERSED';
export type DebtEntryType =
  | 'OPENING'
  | 'ADVANCE'
  | 'INVOICE'
  | 'RECEIPT'
  | 'PAYMENT'
  | 'RETURN'
  | 'DISCOUNT'
  | 'OFFSET'
  | 'ADJUSTMENT'
  | 'PREPAYMENT'
  | 'REVERSAL';

export interface DebtOpeningLine {
  id: number;
  lineNo: number;
  partnerId: number;
  partnerCode: string | null;
  partnerName: string | null;
  dueDate: string;
  amount: number;
  salesUserId: number | null;
  note: string | null;
}

/** Dòng công nợ đầu kỳ đọc từ Excel để điền form (chưa lưu); hạn thanh toán trống thì server tính khi lưu. */
export interface ImportedDebtOpeningLine {
  partnerId: number;
  partnerCode: string;
  partnerName: string;
  dueDate: string | null;
  amount: number;
  note: string | null;
}

export interface DebtOpeningLineImportResult {
  created: number;
  lines: ImportedDebtOpeningLine[];
}

export interface DebtOpening {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  debtType: DebtType;
  documentDate: string;
  postingDate: string;
  description: string | null;
  totalAmount: number;
  lineCount: number;
  status: FinanceDocumentStatus;
  submittedAt: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  reason: string | null;
  createdBy: number | null;
  version: number;
  lines: DebtOpeningLine[];
  approvals: ApprovalRequest[];
}

export interface DebtItem {
  id: number;
  branchId: number;
  debtType: DebtType;
  partnerId: number;
  partnerCode: string | null;
  partnerName: string | null;
  sourceType: string;
  sourceId: number;
  sourceNo: string;
  invoiceNo: string | null;
  documentDate: string;
  dueDate: string;
  originalAmount: number;
  settledAmount: number;
  remainingAmount: number;
  overdueDays: number;
  status: DebtItemStatus;
}

export interface AgingRow {
  partnerId: number | null;
  partnerCode: string | null;
  partnerName: string | null;
  notDue: number;
  bucket1: number;
  bucket2: number;
  bucket3: number;
  bucket4: number;
  total: number;
}

export interface Aging {
  asOf: string;
  bucketDays: number[];
  rows: PageResponse<AgingRow>;
  totals: AgingRow;
}

export interface DebtLedgerLine {
  id: number;
  postingDate: string;
  entryType: DebtEntryType;
  referenceType: string;
  referenceId: number;
  referenceNo: string;
  debtItemId: number | null;
  description: string | null;
  debitAmount: number;
  creditAmount: number;
  balance: number;
}

export interface DebtLedger {
  debtType: DebtType;
  partnerId: number;
  partnerCode: string;
  partnerName: string;
  dateFrom: string;
  dateTo: string;
  openingBalance: number;
  totalDebit: number;
  totalCredit: number;
  closingBalance: number;
  lines: DebtLedgerLine[];
}

export type CashAccountType = 'CASH' | 'BANK';
export type CashDocumentType = 'PT' | 'PC' | 'BC' | 'UNC' | 'CTN';
export type CashPurpose =
  | 'CUSTOMER_RECEIPT'
  | 'SUPPLIER_PAYMENT'
  | 'OTHER_RECEIPT'
  | 'OTHER_PAYMENT'
  | 'INTERNAL_TRANSFER'
  | 'EMPLOYEE_ADVANCE'
  | 'ADVANCE_RETURN';
export type AllocationMode = 'AUTO' | 'MANUAL';

export interface CashAccount {
  id: number;
  companyId: number;
  branchId: number;
  code: string;
  name: string;
  accountType: CashAccountType;
  glAccountId: number;
  glAccountCode: string | null;
  bankName: string | null;
  bankAccountNo: string | null;
  currencyCode: string;
  balance: number;
  status: 'ACTIVE' | 'INACTIVE';
  version: number;
}

export interface CashDocumentLine {
  id: number;
  lineNo: number;
  debtItemId: number | null;
  invoiceNo: string | null;
  debtRemainingAmount: number | null;
  accountId: number | null;
  accountCode: string | null;
  accountName: string | null;
  costItemId: number | null;
  costItemName: string | null;
  amount: number;
  description: string | null;
}

export interface PaymentAllocation {
  id: number;
  debtItemId: number;
  invoiceNo: string | null;
  debtSourceNo: string | null;
  dueDate: string | null;
  amount: number;
  status: 'ACTIVE' | 'REVERSED';
}

export interface CashDocument {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  documentType: CashDocumentType;
  purpose: CashPurpose;
  documentDate: string;
  postingDate: string;
  cashAccountId: number;
  cashAccountCode: string | null;
  cashAccountName: string | null;
  targetCashAccountId: number | null;
  targetCashAccountCode: string | null;
  targetCashAccountName: string | null;
  partnerId: number | null;
  partnerCode: string | null;
  partnerName: string | null;
  counterpartyName: string | null;
  allocationMode: AllocationMode | null;
  description: string | null;
  totalAmount: number;
  allocatedAmount: number;
  prepaymentAmount: number;
  status: FinanceDocumentStatus;
  submittedAt: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  reason: string | null;
  createdBy: number | null;
  version: number;
  lines: CashDocumentLine[];
  allocations: PaymentAllocation[];
  approvals: ApprovalRequest[];
}

export interface CashBookLine {
  id: number;
  postingDate: string;
  referenceType: string;
  referenceId: number;
  referenceNo: string;
  transactionCode: CashPurpose;
  partnerId: number | null;
  partnerName: string | null;
  counterpartyName: string | null;
  description: string | null;
  reversal: boolean;
  inAmount: number;
  outAmount: number;
  balance: number;
}

export interface CashBook {
  cashAccountId: number;
  cashAccountCode: string;
  cashAccountName: string;
  dateFrom: string;
  dateTo: string;
  openingBalance: number;
  totalIn: number;
  totalOut: number;
  closingBalance: number;
  lines: CashBookLine[];
}

export interface CashFlowAccountRow {
  cashAccountId: number;
  code: string;
  name: string;
  accountType: CashAccountType;
  branchId: number;
  openingBalance: number;
  inAmount: number;
  outAmount: number;
  closingBalance: number;
}

export interface CashFlow {
  dateFrom: string;
  dateTo: string;
  accounts: CashFlowAccountRow[];
  purposes: { transactionCode: CashPurpose; inAmount: number; outAmount: number }[];
  openingBalance: number;
  totalIn: number;
  totalOut: number;
  closingBalance: number;
}

/** Trường chung của chứng từ ghi sổ công nợ, quỹ. */
export interface FinanceDocumentBase {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  documentDate: string;
  postingDate: string;
  description: string | null;
  status: FinanceDocumentStatus;
  submittedAt: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  reason: string | null;
  createdBy: number | null;
  version: number;
  approvals: ApprovalRequest[];
}

export type OffsetType = 'PREPAYMENT' | 'AR_AP';

export interface DebtOffsetLine {
  id: number;
  lineNo: number;
  debtItemId: number;
  debtType: DebtType | null;
  invoiceNo: string | null;
  dueDate: string | null;
  remainingAmount: number | null;
  amount: number;
  description: string | null;
}

export interface DebtOffset extends FinanceDocumentBase {
  offsetType: OffsetType;
  debtType: DebtType | null;
  partnerId: number;
  partnerCode: string | null;
  partnerName: string | null;
  totalAmount: number;
  lines: DebtOffsetLine[];
}

export type AdjustmentDirection = 'INCREASE' | 'DECREASE';
export type AdjustmentDocumentType = 'DCCN' | 'TTTU';

export interface DebtAdjustmentLine {
  id: number;
  lineNo: number;
  direction: AdjustmentDirection;
  debtItemId: number | null;
  debtItemLabel: string | null;
  accountId: number;
  accountCode: string | null;
  accountName: string | null;
  costItemId: number | null;
  amount: number;
  invoiceNo: string | null;
  dueDate: string | null;
  description: string | null;
}

export interface DebtAdjustment extends FinanceDocumentBase {
  documentType: AdjustmentDocumentType;
  debtType: DebtType;
  partnerId: number;
  partnerCode: string | null;
  partnerName: string | null;
  reconciliationId: number | null;
  totalAmount: number;
  lines: DebtAdjustmentLine[];
}

export type ReconciliationStatus = 'PENDING' | 'MATCHED' | 'DIFFERENT' | 'CANCELLED';

export interface DebtReconciliation {
  id: number;
  companyId: number;
  branchId: number | null;
  documentNo: string;
  debtType: DebtType;
  partnerId: number;
  partnerCode: string | null;
  partnerName: string | null;
  periodFrom: string;
  asOfDate: string;
  systemBalance: number;
  confirmedBalance: number | null;
  difference: number | null;
  status: ReconciliationStatus;
  contactName: string | null;
  confirmedAt: string | null;
  note: string | null;
  createdBy: number | null;
  createdAt: string;
  version: number;
}

export interface DebtStatement {
  debtType: DebtType;
  partnerId: number;
  partnerCode: string;
  partnerName: string;
  branchId: number | null;
  dateFrom: string;
  dateTo: string;
  openingBalance: number;
  totalDebit: number;
  totalCredit: number;
  closingBalance: number;
  openItemsAmount: number;
  prepaymentAmount: number;
  lines: DebtLedgerLine[];
  openItems: DebtItem[];
}

export interface DebtPrepayment {
  branchId: number;
  debtType: DebtType;
  partnerId: number;
  amount: number;
}

export interface CashCount extends FinanceDocumentBase {
  cashAccountId: number;
  cashAccountCode: string | null;
  cashAccountName: string | null;
  countedAmount: number;
  bookBalance: number | null;
  differenceAmount: number | null;
  currentBookBalance: number | null;
}
