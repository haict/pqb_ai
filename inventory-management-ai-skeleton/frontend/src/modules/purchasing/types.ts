import type { ApprovalRequest } from '../system/types';
import type { QualityStatus } from '../masterdata/types';

/** Trạng thái chứng từ điều phối mua hàng (BR mục 5.1, nhóm B). */
export type PurchaseDocumentStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'REJECTED'
  | 'APPROVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'POSTED'
  | 'REVERSED';

export interface PurchaseDocumentBase {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  documentDate: string;
  description: string | null;
  status: PurchaseDocumentStatus;
  submittedAt: string | null;
  approvedAt: string | null;
  closedAt: string | null;
  reason: string | null;
  createdBy: number | null;
  version: number;
  approvals: ApprovalRequest[];
}

export interface PurchaseRequisitionLine {
  id: number;
  lineNo: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  baseUnitId: number | null;
  conversionFactor: number;
  quantity: number;
  baseQuantity: number;
  orderedQuantity: number;
  closedQuantity: number;
  remainingQuantity: number;
  suggestedSupplierId: number | null;
  suggestedSupplierName: string | null;
  requiredDate: string | null;
  note: string | null;
}

export interface PurchaseRequisition extends PurchaseDocumentBase {
  sourceType: 'MANUAL';
  requiredDate: string | null;
  requesterId: number | null;
  requesterName: string | null;
  lines: PurchaseRequisitionLine[];
}

export interface OpenRequisitionLine {
  requisitionId: number;
  documentNo: string;
  documentDate: string;
  lineId: number;
  lineNo: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  baseUnitId: number | null;
  remainingQuantity: number;
  suggestedSupplierId: number | null;
  requiredDate: string | null;
}

export interface PurchaseOrderLine {
  id: number;
  lineNo: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  baseUnitId: number | null;
  conversionFactor: number;
  quantity: number;
  baseQuantity: number;
  unitPrice: number;
  discountPercent: number;
  discountAmount: number;
  amount: number;
  taxRateId: number | null;
  taxRate: number | null;
  taxAmount: number;
  expectedDate: string | null;
  requisitionLineId: number | null;
  receivedQuantity: number;
  stockedQuantity: number;
  rejectedQuantity: number;
  invoicedQuantity: number;
  closedQuantity: number;
  outstandingQuantity: number;
  note: string | null;
}

export interface PurchaseOrder extends PurchaseDocumentBase {
  supplierId: number;
  supplierCode: string | null;
  supplierName: string | null;
  warehouseId: number;
  warehouseCode: string | null;
  warehouseName: string | null;
  expectedDate: string | null;
  paymentTermDays: number | null;
  currencyCode: string;
  exchangeRate: number;
  amount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  lines: PurchaseOrderLine[];
}

export type InspectionResult = 'ACCEPTED' | 'QUARANTINE' | 'RETURN';

export interface PurchaseReceiptLine {
  id: number;
  lineNo: number;
  orderLineId: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  conversionFactor: number;
  quantity: number;
  baseQuantity: number;
  inspectionResult: InspectionResult;
  rejectReason: string | null;
  locationId: number | null;
  lotNo: string | null;
  manufactureDate: string | null;
  expiryDate: string | null;
  unitCost: number;
  note: string | null;
  serialNumbers: string[];
}

export interface PurchaseReceipt extends PurchaseDocumentBase {
  postingDate: string;
  purchaseOrderId: number;
  purchaseOrderNo: string | null;
  supplierId: number;
  supplierCode: string | null;
  supplierName: string | null;
  warehouseId: number;
  warehouseCode: string | null;
  warehouseName: string | null;
  deliveryNoteNo: string | null;
  overTolerance: boolean;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  stockedAt: string | null;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  lines: PurchaseReceiptLine[];
}

export interface PurchaseInvoiceLine {
  id: number;
  lineNo: number;
  receiptLineId: number | null;
  receiptNo: string | null;
  orderLineId: number | null;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  conversionFactor: number;
  quantity: number;
  baseQuantity: number;
  unitPrice: number;
  provisionalUnitPrice: number | null;
  amount: number;
  taxRateId: number | null;
  taxRate: number | null;
  taxAmount: number;
  promotional: boolean;
  description: string | null;
}

export interface PurchaseInvoice extends PurchaseDocumentBase {
  postingDate: string;
  supplierId: number;
  supplierCode: string | null;
  supplierName: string | null;
  sellerTaxCode: string;
  invoiceSeries: string;
  invoiceNo: string;
  invoiceDate: string;
  dueDate: string;
  amount: number;
  taxAmount: number;
  totalAmount: number;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  matchWarnings: string[];
  lines: PurchaseInvoiceLine[];
}

export interface InvoiceableLine {
  receiptId: number;
  receiptNo: string;
  receiptDate: string;
  purchaseOrderId: number;
  purchaseOrderNo: string | null;
  receiptLineId: number;
  lineNo: number;
  orderLineId: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  remainingQuantity: number;
  unitPrice: number;
  taxRateId: number | null;
}

/** Trả hàng nhà cung cấp (THN) / giảm giá hàng mua (GGM). */
export type PurchaseReturnType = 'THN' | 'GGM';

export interface PurchaseReturnLine {
  id: number;
  lineNo: number;
  receiptLineId: number | null;
  receiptNo: string | null;
  invoiceLineId: number | null;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  quantity: number | null;
  baseQuantity: number | null;
  lotNo: string | null;
  qualityStatus: QualityStatus | null;
  unitPrice: number | null;
  amount: number;
  taxRate: number | null;
  taxAmount: number;
  description: string | null;
  serialNumbers: string[];
}

export interface PurchaseReturn extends PurchaseDocumentBase {
  documentType: PurchaseReturnType;
  postingDate: string;
  supplierId: number;
  supplierCode: string | null;
  supplierName: string | null;
  purchaseInvoiceId: number | null;
  purchaseInvoiceNo: string | null;
  invoiceLabel: string | null;
  warehouseId: number | null;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  amount: number;
  taxAmount: number;
  totalAmount: number;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  lines: PurchaseReturnLine[];
}

export interface ReturnableLine {
  receiptId: number | null;
  receiptNo: string | null;
  receiptDate: string | null;
  receiptLineId: number | null;
  warehouseId: number | null;
  invoiceLineId: number | null;
  productId: number | null;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  lotNo: string | null;
  expiryDate: string | null;
  qualityStatus: QualityStatus | null;
  remainingQuantity: number;
  unitPrice: number | null;
  taxRate: number | null;
  remainingAmount: number | null;
  serialNumbers: string[];
}

export type CostAllocationMethod = 'VALUE' | 'QUANTITY';

export interface CostAllocationSource {
  lineNo: number | null;
  invoiceId: number | null;
  invoiceDocumentNo: string | null;
  invoiceLabel: string | null;
  supplierName: string | null;
  invoiceLineId: number;
  productId: number | null;
  productCode: string | null;
  productName: string | null;
  amount: number;
  remainingAmount: number | null;
}

export interface CostAllocationTarget {
  lineNo: number;
  receiptId: number | null;
  receiptNo: string | null;
  receiptLineId: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  baseQuantity: number;
  basisValue: number;
  allocatedAmount: number;
}

export interface PurchaseCostAllocation extends PurchaseDocumentBase {
  postingDate: string;
  method: CostAllocationMethod;
  amount: number;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  receiptIds: number[];
  sources: CostAllocationSource[];
  targets: CostAllocationTarget[];
}
