import type { ApprovalRequest } from '../system/types';
import type { QualityStatus } from '../masterdata/types';

/** Trạng thái chứng từ mua hàng (BR mục 5.1, D-54: mọi chứng từ mua là chứng từ ghi sổ). */
export type PurchaseDocumentStatus =
  'DRAFT' | 'SUBMITTED' | 'REJECTED' | 'APPROVED' | 'CANCELLED' | 'POSTED' | 'REVERSED';

export interface PurchaseDocumentBase {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  documentDate: string;
  postingDate: string;
  description: string | null;
  status: PurchaseDocumentStatus;
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
  serviceLine: boolean;
  locationId: number | null;
  lotNo: string | null;
  manufactureDate: string | null;
  expiryDate: string | null;
  serialNumbers: string[];
  returnedQuantity: number;
  adjustedAmount: number;
  allocatedAmount: number;
  returnableQuantity: number;
  remainingAmount: number;
  note: string | null;
}

/** Kết quả xóa đơn chưa từng ghi sổ (D-65). */
export interface DeletedOrder {
  id: number;
  documentNo: string;
}

/** Đơn mua hàng (D-54): mang hóa đơn của nhà cung cấp; ghi sổ = nhập kho + công nợ phải trả + bút toán. */
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
  supplierTaxCode: string | null;
  supplierAddress: string | null;
  invoiceSeries: string | null;
  invoiceNo: string | null;
  invoiceDate: string | null;
  dueDate: string | null;
  amount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  lines: PurchaseOrderLine[];
}

/** Trả hàng nhà cung cấp (THN) / giảm giá hàng mua (GGM). */
export type PurchaseReturnType = 'THN' | 'GGM';

export interface PurchaseReturnLine {
  id: number;
  lineNo: number;
  orderLineId: number;
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
  supplierId: number;
  supplierCode: string | null;
  supplierName: string | null;
  purchaseOrderId: number;
  purchaseOrderNo: string | null;
  invoiceLabel: string | null;
  warehouseId: number | null;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  amount: number;
  taxAmount: number;
  totalAmount: number;
  lines: PurchaseReturnLine[];
}

/** Dòng đơn mua đã ghi sổ còn trả / giảm giá được. */
export interface ReturnableLine {
  orderId: number;
  orderNo: string;
  postingDate: string;
  invoiceLabel: string;
  orderLineId: number;
  warehouseId: number;
  serviceLine: boolean;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  lotNo: string | null;
  expiryDate: string | null;
  qualityStatus: QualityStatus | null;
  remainingQuantity: number;
  unitPrice: number;
  taxRate: number | null;
  remainingAmount: number;
  serialNumbers: string[];
}

export type CostAllocationMethod = 'VALUE' | 'QUANTITY';

export interface CostAllocationSource {
  lineNo: number | null;
  orderId: number | null;
  orderNo: string | null;
  invoiceLabel: string | null;
  supplierName: string | null;
  orderLineId: number;
  productId: number | null;
  productCode: string | null;
  productName: string | null;
  amount: number;
  remainingAmount: number | null;
}

export interface CostAllocationTarget {
  lineNo: number;
  orderId: number | null;
  orderNo: string | null;
  orderLineId: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  baseQuantity: number;
  basisValue: number;
  allocatedAmount: number;
}

export interface PurchaseCostAllocation extends PurchaseDocumentBase {
  method: CostAllocationMethod;
  amount: number;
  orderIds: number[];
  sources: CostAllocationSource[];
  targets: CostAllocationTarget[];
}

/** Dữ liệu in phiếu nhập kho của đơn mua đã ghi sổ (`GET /purchase-orders/{id}/stock-in-slip`). */
export interface StockInSlipLine {
  lineNo: number;
  productCode: string | null;
  productName: string | null;
  lotNo: string | null;
  expiryDate: string | null;
  unitId: number;
  quantity: number;
  unitPrice: number;
  amount: number;
}

export interface StockInSlip {
  companyName: string | null;
  companyAddress: string | null;
  orderId: number;
  orderNo: string;
  slipNo: string | null;
  slipDate: string;
  debitAccounts: string[];
  creditAccounts: string[];
  supplierName: string | null;
  supplierAddress: string | null;
  supplierTaxCode: string | null;
  description: string;
  invoiceSeries: string | null;
  invoiceNo: string | null;
  invoiceDate: string | null;
  warehouseCode: string | null;
  warehouseName: string | null;
  lines: StockInSlipLine[];
  totalAmount: number;
  taxRates: number[];
  taxAmount: number;
  paymentAmount: number;
}

/** Dòng sổ chi tiết mua hàng; số âm ở dòng đảo. */
export interface PurchaseLedgerLine {
  postingDate: string;
  documentType: string;
  documentId: number;
  documentNo: string;
  documentDate: string;
  invoiceDate: string | null;
  invoiceNo: string | null;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitName: string | null;
  purchaseQuantity: number | null;
  unitPrice: number | null;
  purchaseAmount: number | null;
  discountAmount: number | null;
  returnQuantity: number | null;
  returnAmount: number | null;
  priceReductionAmount: number | null;
  reversal: boolean;
}

export interface PurchaseLedgerTotals {
  lineCount: number;
  purchaseQuantity: number;
  purchaseAmount: number;
  discountAmount: number;
  returnQuantity: number;
  returnAmount: number;
  priceReductionAmount: number;
}

export interface PurchaseLedger {
  companyName: string | null;
  companyAddress: string | null;
  dateFrom: string;
  dateTo: string;
  lines: PurchaseLedgerLine[];
  total: PurchaseLedgerTotals;
  generatedAt: string;
}

export interface PurchaseLedgerParams {
  companyId: number;
  branchId?: number;
  dateFrom: string;
  dateTo: string;
  purchaserId?: number;
  supplierGroupId?: number;
  productCategoryId?: number;
  productIds?: number[];
  supplierIds?: number[];
}

/** Dòng hàng đọc từ Excel để điền form đơn mua (chưa lưu). */
export interface ImportedOrderLine {
  productId: number;
  productCode: string;
  productName: string;
  serviceLine: boolean;
  unitId: number;
  quantity: number;
  unitPrice: number;
  discountPercent: number | null;
  taxRateId: number | null;
  lotNo: string | null;
  manufactureDate: string | null;
  expiryDate: string | null;
  serialNumbers: string[];
  note: string | null;
}

export interface OrderLineImportResult {
  created: number;
  lines: ImportedOrderLine[];
}
