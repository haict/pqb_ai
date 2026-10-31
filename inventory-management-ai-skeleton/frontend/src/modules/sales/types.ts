import type { QualityStatus } from '../masterdata/types';
import type { ApprovalRequest } from '../system/types';

/** Trạng thái chứng từ bán hàng (BR mục 5.1, D-54: mọi chứng từ bán là chứng từ ghi sổ). */
export type SalesDocumentStatus =
  'DRAFT' | 'SUBMITTED' | 'REJECTED' | 'APPROVED' | 'CANCELLED' | 'POSTED' | 'REVERSED';

export type DeliveryStatus = 'PENDING' | 'IN_TRANSIT' | 'DELIVERED';
export type PaymentDueBasis = 'ORDER_DATE' | 'DELIVERY' | 'INVOICE' | 'ACCEPTANCE';
export type SalesReturnType = 'TLB' | 'GGB';
export type ComplaintStatus = 'OPEN' | 'INSPECTED' | 'RESOLVED' | 'CANCELLED';
export type ComplaintDecision = 'EXCHANGE' | 'REPAIR' | 'RETURN' | 'WARRANTY' | 'REJECT';
export type SalesReportGrouping = 'SALES_USER' | 'CUSTOMER' | 'PRODUCT' | 'BRANCH';
export type SalesLedgerGrouping = 'NONE' | 'CUSTOMER' | 'PRODUCT_CATEGORY';

export interface SalesDocumentBase {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  documentDate: string;
  postingDate: string;
  description: string | null;
  status: SalesDocumentStatus;
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

/** Hàng thực xuất khi ghi sổ đơn bán: lô, hạn dùng, serial, trạng thái, số lượng (đơn vị cơ sở). */
export interface DeliveryItem {
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  qualityStatus: QualityStatus;
  baseQuantity: number;
}

export interface SalesLine {
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
  listPrice: number | null;
  floorPrice: number | null;
  priceSource: string | null;
  unitPrice: number;
  discountPercent: number;
  discountAmount: number;
  amount: number;
  taxRateId: number | null;
  taxRate: number | null;
  taxAmount: number;
  promotional: boolean;
  serviceLine: boolean;
  expectedDate: string | null;
  lotNo: string | null;
  lotOverrideReason: string | null;
  serialNumbers: string[];
  /** Chứng từ nhập chọn xuất đích danh (D-63, F8); trống: kho chọn FEFO. */
  receiptTransactionId: number | null;
  receiptNo: string | null;
  items: DeliveryItem[];
  originCountryCode: string | null;
  model: string | null;
  registrationNo: string | null;
  returnedQuantity: number;
  returnableQuantity: number;
  adjustedAmount: number;
  remainingAmount: number;
  availableQuantity: number | null;
  reservedQuantity: number | null;
  description: string | null;
}

export interface PaymentSchedule {
  installmentNo: number;
  percent: number;
  amount: number;
  dueBasis: PaymentDueBasis;
  dueDays: number;
  dueDate: string | null;
  paidAmount: number;
  remainingAmount: number;
  overdue: boolean;
  description: string | null;
}

/** Kết quả xóa đơn chưa từng ghi sổ (D-65). */
export interface DeletedOrder {
  id: number;
  documentNo: string;
}

/** Đơn bán (D-54): ghi sổ = xuất kho + doanh thu + công nợ phải thu + bút toán; số HĐĐT; theo dõi giao hàng. */
export interface SalesOrder extends SalesDocumentBase {
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  customerTaxCode: string | null;
  contactName: string | null;
  deliveryAddress: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  warehouseId: number;
  warehouseCode: string | null;
  warehouseName: string | null;
  expectedDate: string | null;
  paymentTermDays: number | null;
  dueDate: string | null;
  reserveStock: boolean;
  reservedUntil: string | null;
  salesUserId: number | null;
  salesUserName: string | null;
  sourceReturnId: number | null;
  currencyCode: string;
  amount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  approvalReasons: string | null;
  einvoiceSeries: string | null;
  einvoiceNo: string | null;
  einvoiceDate: string | null;
  taxAuthorityCode: string | null;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  deliveryStatus: DeliveryStatus;
  shipperName: string | null;
  shippedAt: string | null;
  receivedDate: string | null;
  receivedBy: string | null;
  signedDocumentRef: string | null;
  deliveryNote: string | null;
  availabilityWarnings: string[];
  lines: SalesLine[];
  paymentSchedule: PaymentSchedule[];
}

export interface PriceSuggestion {
  unitPrice: number | null;
  priceSource: string | null;
  priceListCode: string | null;
  floorPrice: number | null;
  defaultDiscountPercent: number | null;
  taxRateId: number | null;
}

export interface SalesReturnLine {
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
  expiryDate: string | null;
  qualityStatus: QualityStatus | null;
  unitPrice: number | null;
  amount: number;
  taxRate: number | null;
  taxAmount: number;
  description: string | null;
  serialNumbers: string[];
}

export interface SalesReturn extends SalesDocumentBase {
  documentType: SalesReturnType;
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  salesOrderId: number;
  salesOrderNo: string | null;
  invoiceLabel: string | null;
  warehouseId: number | null;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  amount: number;
  taxAmount: number;
  totalAmount: number;
  lines: SalesReturnLine[];
}

/** Dòng đơn bán đã ghi sổ còn trả / giảm giá được, chia theo lô đã xuất. */
export interface ReturnableSalesLine {
  orderId: number;
  orderNo: string;
  postingDate: string;
  invoiceLabel: string;
  warehouseId: number;
  orderLineId: number;
  serviceLine: boolean;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  lotNo: string | null;
  expiryDate: string | null;
  remainingQuantity: number;
  unitPrice: number;
  taxRate: number | null;
  remainingAmount: number;
  serialNumbers: string[];
}

export interface SalesComplaint {
  id: number;
  companyId: number;
  branchId: number;
  complaintNo: string;
  receivedDate: string;
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  salesOrderId: number | null;
  productId: number;
  productCode: string | null;
  productName: string | null;
  lotNo: string | null;
  serialNo: string | null;
  quantity: number;
  description: string;
  inspectionResult: string | null;
  decision: ComplaintDecision | null;
  resolutionNote: string | null;
  salesReturnId: number | null;
  status: ComplaintStatus;
  inspectedAt: string | null;
  resolvedAt: string | null;
  createdBy: number | null;
  version: number;
}

export interface SalesSummaryRow {
  key: number | null;
  code: string | null;
  name: string | null;
  invoiceCount: number;
  revenue: number;
  deductions: number;
  netRevenue: number;
  taxAmount: number;
  receivable: number | null;
}

export interface SalesSummary {
  groupBy: SalesReportGrouping;
  dateFrom: string;
  dateTo: string;
  rows: SalesSummaryRow[];
  total: SalesSummaryRow;
}

export interface SalesSourceDocument {
  documentType: string;
  documentId: number;
  documentNo: string;
  postingDate: string;
  customerId: number;
  amount: number;
}

/** Dữ liệu in phiếu xuất kho của đơn bán đã ghi sổ (`GET /sales-orders/{id}/stock-out-slip`). */
export interface StockOutSlipItem {
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  baseQuantity: number;
}

export interface StockOutSlipLine {
  lineNo: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  quantity: number;
  unitPrice: number;
  amount: number;
  items: StockOutSlipItem[];
}

export interface StockOutSlip {
  companyName: string | null;
  companyAddress: string | null;
  orderId: number;
  orderNo: string;
  orderDate: string;
  slipNo: string | null;
  slipDate: string;
  debitAccounts: string[];
  creditAccounts: string[];
  customerName: string | null;
  customerAddress: string | null;
  customerTaxCode: string | null;
  receiverName: string | null;
  description: string;
  einvoiceSeries: string | null;
  einvoiceNo: string | null;
  einvoiceDate: string | null;
  warehouseCode: string | null;
  warehouseName: string | null;
  lines: StockOutSlipLine[];
  totalAmount: number;
  taxRates: number[];
  taxAmount: number;
  paymentAmount: number;
}

/** Dòng sổ chi tiết bán hàng (S16-DNN); số âm ở dòng đảo. */
export interface SalesLedgerLine {
  postingDate: string;
  documentType: string;
  documentId: number;
  documentNo: string;
  documentDate: string;
  invoiceDate: string | null;
  invoiceNo: string | null;
  description: string;
  offsetAccount: string | null;
  unitName: string | null;
  quantity: number | null;
  unitPrice: number | null;
  amount: number | null;
  taxDeduction: number | null;
  otherDeduction: number | null;
  reversal: boolean;
}

/** Giá vốn, lãi gộp null khi chưa tính giá vốn (Giai đoạn 8). */
export interface SalesLedgerTotals {
  quantity: number;
  amount: number;
  taxDeduction: number;
  otherDeduction: number;
  netRevenue: number;
  costOfGoodsSold: number | null;
  grossProfit: number | null;
}

export interface SalesLedgerProduct {
  productId: number;
  productCode: string | null;
  productName: string | null;
  lines: SalesLedgerLine[];
  totals: SalesLedgerTotals;
}

export interface SalesLedgerGroup {
  key: number | null;
  code: string | null;
  name: string | null;
  products: SalesLedgerProduct[];
  totals: SalesLedgerTotals;
}

export interface SalesLedger {
  companyName: string | null;
  companyAddress: string | null;
  dateFrom: string;
  dateTo: string;
  groupBy: SalesLedgerGrouping;
  groups: SalesLedgerGroup[];
  total: SalesLedgerTotals;
  costAvailable: boolean;
  generatedAt: string;
}

export interface SalesLedgerParams {
  companyId: number;
  branchId?: number;
  dateFrom: string;
  dateTo: string;
  salesUserId?: number;
  customerGroupId?: number;
  productCategoryId?: number;
  productIds?: number[];
  customerIds?: number[];
  groupBy: SalesLedgerGrouping;
}

/** Dòng hàng đọc từ Excel để điền form đơn bán (chưa lưu); giá, chiết khấu, lô trống: server xử lý khi lưu / ghi sổ. */
export interface ImportedSalesLine {
  productId: number;
  productCode: string;
  productName: string;
  unitId: number;
  quantity: number;
  unitPrice: number | null;
  discountPercent: number | null;
  taxRateId: number | null;
  promotional: boolean;
  expectedDate: string | null;
  lotNo: string | null;
  lotOverrideReason: string | null;
  serialNumbers: string[];
  description: string | null;
}

export interface SalesLineImportResult {
  created: number;
  lines: ImportedSalesLine[];
}

/** Chứng từ nhập còn tồn để chọn cho dòng đơn bán (D-63, F8); số lượng theo đơn vị cơ sở. */
export interface ReceiptSource {
  transactionId: number;
  documentType: string;
  documentId: number;
  documentNo: string;
  postingDate: string;
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  receivedQuantity: number;
  unitCost: number | null;
  /** Tồn ĐẠT hiện có của lô / serial tại kho (lô nhập nhiều lần dùng chung). */
  availableQuantity: number;
}
