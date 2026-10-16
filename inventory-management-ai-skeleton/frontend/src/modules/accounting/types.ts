import type { RecordStatus } from '../../shared/components/StatusTag';

/** Kiểu dữ liệu khớp DTO của module kế toán (docs/04 mục kế toán). */

export type BalanceSide = 'DEBIT' | 'CREDIT' | 'BOTH';
export type JournalEntryKind = 'AUTO' | 'MANUAL' | 'COST' | 'CLOSING';
export type JournalEntryStatus = 'POSTED' | 'REVERSED';

export interface Account {
  id: number;
  companyId: number;
  code: string;
  name: string;
  parentId: number | null;
  parentCode: string | null;
  level: number;
  balanceSide: BalanceSide;
  postable: boolean;
  partnerRequired: boolean;
  status: RecordStatus;
  version: number;
  updatedAt: string;
}

export interface PostingRule {
  id: number;
  companyId: number;
  documentType: string;
  transactionCode: string;
  productType: string | null;
  partnerRole: string | null;
  debitAccountId: number | null;
  debitAccountCode: string | null;
  creditAccountId: number | null;
  creditAccountCode: string | null;
  validFrom: string;
  validTo: string | null;
  description: string | null;
  status: RecordStatus;
  version: number;
  updatedAt: string;
}

export interface JournalEntrySummary {
  id: number;
  companyId: number;
  branchId: number;
  entryNo: string;
  postingDate: string;
  entryKind: JournalEntryKind;
  sourceType: string;
  sourceId: number;
  sourceNo: string;
  description: string | null;
  totalAmount: number;
  status: JournalEntryStatus;
  reversalOfId: number | null;
  createdAt: string;
}

export interface JournalEntryLine {
  id: number;
  lineNo: number;
  accountId: number;
  accountCode: string | null;
  accountName: string | null;
  debitAmount: number;
  creditAmount: number;
  transactionCode: string;
  postingRuleId: number | null;
  partnerId: number | null;
  partnerCode: string | null;
  partnerName: string | null;
  productId: number | null;
  warehouseId: number | null;
  departmentId: number | null;
  employeeId: number | null;
  sourceLineId: number | null;
  description: string | null;
}

export interface JournalEntry {
  entry: JournalEntrySummary;
  lines: JournalEntryLine[];
  reversedBy: JournalEntrySummary | null;
}

export interface AccountImportResult {
  created: number;
}
