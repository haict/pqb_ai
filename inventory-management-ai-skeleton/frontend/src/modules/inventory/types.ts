import type { QualityStatus } from '../masterdata/types';
import type { ApprovalRequest } from '../system/types';

/** Kiểu dữ liệu khớp DTO module kho (backend inventory/application/dto). Số lượng, tiền nhận dạng number. */

export type InventoryDocumentType = 'PNK' | 'PXK' | 'PXH' | 'PCK' | 'PDC' | 'PCL' | 'PDK';
export type InventoryDocumentStatus =
  'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'IN_TRANSIT' | 'POSTED' | 'CANCELLED' | 'REVERSED';
export type MovementDirection = 'IN' | 'OUT';
export type TransactionType =
  | 'OPENING'
  | 'RECALL_IN'
  | 'INTERNAL_ISSUE'
  | 'SCRAP'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'ADJUST_IN'
  | 'ADJUST_OUT'
  | 'COUNT_GAIN'
  | 'COUNT_LOSS'
  | 'STATUS_OUT'
  | 'STATUS_IN'
  | 'REVERSAL';
export type DocumentAction =
  'update' | 'submit' | 'approve' | 'reject' | 'cancel' | 'post' | 'receive' | 'reverse';
export type SerialStatus = 'IN_STOCK' | 'ISSUED' | 'SCRAPPED';
export type StockCountStatus = 'DRAFT' | 'COUNTING' | 'SUBMITTED' | 'APPROVED' | 'POSTED' | 'CANCELLED';
export type RecallStatus = 'DRAFT' | 'SUBMITTED' | 'REJECTED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
export type PolicyStatus = 'DRAFT' | 'SUBMITTED' | 'REJECTED' | 'APPROVED' | 'CANCELLED';

export interface InventoryLineInput {
  productId: number;
  unitId: number;
  quantity: number;
  direction?: MovementDirection;
  locationId?: number;
  destinationLocationId?: number;
  lotId?: number;
  lotNo?: string;
  manufactureDate?: string;
  expiryDate?: string;
  qualityStatus?: QualityStatus;
  targetQualityStatus?: QualityStatus;
  unitCost?: number;
  lotOverrideReason?: string;
  note?: string;
  serialNumbers?: string[];
}

export interface InventoryDocumentInput {
  documentType: InventoryDocumentType;
  subType?: string;
  documentDate: string;
  postingDate?: string;
  warehouseId: number;
  destinationWarehouseId?: number;
  partnerId?: number;
  reasonText?: string;
  note?: string;
  issuePolicyId?: number;
  lines: InventoryLineInput[];
  version?: number;
}

export interface InventoryLine {
  id: number;
  lineNo: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  conversionFactor: number;
  quantity: number;
  baseQuantity: number;
  direction: MovementDirection | null;
  locationId: number | null;
  destinationLocationId: number | null;
  lotId: number | null;
  lotNo: string | null;
  manufactureDate: string | null;
  expiryDate: string | null;
  qualityStatus: QualityStatus | null;
  targetQualityStatus: QualityStatus | null;
  unitCost: number | null;
  amount: number | null;
  lotOverrideReason: string | null;
  note: string | null;
  serialNumbers: string[];
}

export interface InventoryTransaction {
  id: number;
  postingDate: string;
  postedAt: string;
  productId: number;
  productCode: string | null;
  productName: string | null;
  warehouseId: number;
  locationId: number;
  lotId: number | null;
  lotNo: string | null;
  expiryDate: string | null;
  serialId: number | null;
  serialNo: string | null;
  qualityStatus: QualityStatus;
  transactionType: TransactionType;
  direction: MovementDirection;
  quantity: number;
  unitCost: number | null;
  amount: number | null;
  partnerId: number | null;
  referenceType: string;
  referenceId: number;
  referenceNo: string;
  reversalOfId: number | null;
}

export interface InventoryDocumentSummary {
  id: number;
  documentType: InventoryDocumentType;
  subType: string | null;
  documentNo: string;
  documentDate: string;
  postingDate: string;
  status: InventoryDocumentStatus;
  branchId: number;
  warehouseId: number;
  destinationWarehouseId: number | null;
  partnerId: number | null;
  totalAmount: number | null;
  lineCount: number;
  updatedAt: string;
}

export interface InventoryDocument extends InventoryDocumentSummary {
  companyId: number;
  transitWarehouseId: number | null;
  reasonId: number | null;
  reasonText: string | null;
  note: string | null;
  recallId: number | null;
  stockCountId: number | null;
  issuePolicyId: number | null;
  reversalOfId: number | null;
  reversalOfNo: string | null;
  reversedById: number | null;
  reversedByNo: string | null;
  createdBy: number | null;
  createdAt: string;
  submittedAt: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  receivedAt: string | null;
  receivePostingDate: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  /** Chứng từ nguồn của phân hệ khác (PNH: phiếu nhận hàng). */
  sourceType: string | null;
  sourceId: number | null;
  sourceNo: string | null;
  version: number;
  lines: InventoryLine[];
  transactions: InventoryTransaction[];
  approvals: ApprovalRequest[];
  allowedActions: DocumentAction[];
}

export interface StockSummary {
  productId: number;
  productCode: string | null;
  productName: string | null;
  baseUnitId: number | null;
  warehouseId: number;
  onHandQuantity: number;
  sellableQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  incomingQuantity: number;
}

export interface StockBalance {
  id: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  warehouseId: number;
  locationId: number;
  lotId: number | null;
  lotNo: string | null;
  expiryDate: string | null;
  lotBlocked: boolean;
  serialId: number | null;
  serialNo: string | null;
  qualityStatus: QualityStatus;
  onHandQuantity: number;
}

export interface Lot {
  id: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  lotNo: string;
  manufactureDate: string | null;
  expiryDate: string | null;
  daysToExpiry: number | null;
  blocked: boolean;
  blockReason: string | null;
  onHandQuantity: number;
  version: number;
}

export interface Serial {
  id: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  serialNo: string;
  lotId: number | null;
  lifecycleStatus: SerialStatus;
  currentWarehouseId: number | null;
  blocked: boolean;
  blockReason: string | null;
  version: number;
}

export interface FefoAllocation {
  lotId: number | null;
  lotNo: string | null;
  expiryDate: string | null;
  locationId: number;
  availableQuantity: number;
  suggestedQuantity: number;
}

export interface StockAlert {
  alertType: 'EXPIRING' | 'BELOW_MIN';
  productId: number;
  productCode: string | null;
  productName: string | null;
  warehouseId: number;
  lotId: number | null;
  lotNo: string | null;
  expiryDate: string | null;
  daysToExpiry: number | null;
  onHandQuantity: number;
  minQuantity: number | null;
}

export interface StockCountLine {
  id: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  locationId: number;
  lotId: number | null;
  lotNo: string | null;
  expiryDate: string | null;
  serialId: number | null;
  serialNo: string | null;
  qualityStatus: QualityStatus;
  snapshotQuantity: number;
  countedQuantity: number | null;
  difference: number | null;
  note: string | null;
}

export interface StockCount {
  id: number;
  companyId: number;
  branchId: number;
  warehouseId: number;
  documentNo: string;
  countDate: string;
  status: StockCountStatus;
  note: string | null;
  startedAt: string | null;
  postedAt: string | null;
  adjustmentDocumentId: number | null;
  cancelReason: string | null;
  version: number;
  lines: StockCountLine[];
  approvals: ApprovalRequest[];
}

export interface RecallCustomer {
  partnerId: number;
  partnerCode: string | null;
  partnerName: string | null;
  deliveredQuantity: number;
  recoveredQuantity: number;
}

export interface Recall {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  recallDate: string;
  productId: number;
  productCode: string | null;
  productName: string | null;
  lotId: number | null;
  lotNo: string | null;
  serialId: number | null;
  serialNo: string | null;
  reason: string;
  status: RecallStatus;
  approvedAt: string | null;
  closedAt: string | null;
  cancelReason: string | null;
  version: number;
  customers: RecallCustomer[];
  approvals: ApprovalRequest[];
}

export interface StockIssuePolicy {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  warehouseId: number;
  productId: number | null;
  maxQuantity: number;
  usedQuantity: number;
  usedByDocumentId: number | null;
  validFrom: string;
  validTo: string;
  reason: string;
  status: PolicyStatus;
  approvedAt: string | null;
  cancelReason: string | null;
  version: number;
  approvals: ApprovalRequest[];
}
