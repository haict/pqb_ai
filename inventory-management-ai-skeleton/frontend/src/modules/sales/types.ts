import type { QualityStatus } from '../masterdata/types';
import type { ApprovalRequest } from '../system/types';

/** Trạng thái chứng từ bán hàng (BR mục 5.1, BR-SAL-05). */
export type SalesDocumentStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'REJECTED'
  | 'APPROVED'
  | 'SENT'
  | 'ACCEPTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'POSTED'
  | 'REVERSED';

export type DeliveryStatus = 'PENDING' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED';
export type PaymentDueBasis = 'ORDER_DATE' | 'DELIVERY' | 'INVOICE' | 'ACCEPTANCE';
export type SalesReturnType = 'TLB' | 'GGB';
export type ComplaintStatus = 'OPEN' | 'INSPECTED' | 'RESOLVED' | 'CANCELLED';
export type ComplaintDecision = 'EXCHANGE' | 'REPAIR' | 'RETURN' | 'WARRANTY' | 'REJECT';
export type SalesReportGrouping = 'SALES_USER' | 'CUSTOMER' | 'PRODUCT' | 'BRANCH';

export interface SalesDocumentBase {
  id: number;
  companyId: number;
  branchId: number;
  documentNo: string;
  documentDate: string;
  description: string | null;
  status: SalesDocumentStatus;
  submittedAt: string | null;
  approvedAt: string | null;
  reason: string | null;
  createdBy: number | null;
  version: number;
  approvals: ApprovalRequest[];
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
  issuedQuantity: number | null;
  deliveredQuantity: number | null;
  invoicedQuantity: number | null;
  returnedQuantity: number | null;
  closedQuantity: number | null;
  outstandingQuantity: number | null;
  availableQuantity: number | null;
  reservedQuantity: number | null;
  description: string | null;
}

export interface SalesQuote extends SalesDocumentBase {
  revisionNo: number;
  previousQuoteId: number | null;
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  contactName: string | null;
  validUntil: string;
  deliveryTerms: string | null;
  warrantyTerms: string | null;
  paymentTerms: string | null;
  salesUserId: number | null;
  salesUserName: string | null;
  amount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  approvalReasons: string | null;
  salesOrderId: number | null;
  sentAt: string | null;
  respondedAt: string | null;
  closedAt: string | null;
  lines: SalesLine[];
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

export interface SalesOrder extends SalesDocumentBase {
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  quoteId: number | null;
  quoteNo: string | null;
  contactName: string | null;
  deliveryAddress: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  warehouseId: number;
  warehouseCode: string | null;
  warehouseName: string | null;
  expectedDate: string | null;
  paymentTermDays: number | null;
  reserveStock: boolean;
  reservedUntil: string | null;
  salesUserId: number | null;
  salesUserName: string | null;
  currencyCode: string;
  amount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  approvalReasons: string | null;
  closedAt: string | null;
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

export interface DeliveryItem {
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  qualityStatus: QualityStatus;
  baseQuantity: number;
}

export interface SalesDeliveryLine {
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
  lotNo: string | null;
  lotOverrideReason: string | null;
  invoicedQuantity: number;
  returnedQuantity: number;
  uninvoicedQuantity: number;
  registrationNo: string | null;
  originCountryCode: string | null;
  note: string | null;
  serialNumbers: string[];
  items: DeliveryItem[];
}

export interface SalesDelivery extends SalesDocumentBase {
  postingDate: string;
  orderId: number;
  orderNo: string | null;
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  warehouseId: number;
  warehouseCode: string | null;
  deliveryAddress: string | null;
  receiverName: string | null;
  receiverPhone: string | null;
  shipperName: string | null;
  sourceReturnId: number | null;
  deliveryStatus: DeliveryStatus;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  shippedAt: string | null;
  receivedDate: string | null;
  receivedBy: string | null;
  signedDocumentRef: string | null;
  deliveryNote: string | null;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  lines: SalesDeliveryLine[];
}

export interface SalesInvoiceLine {
  id: number;
  lineNo: number;
  deliveryLineId: number | null;
  deliveryNo: string | null;
  orderLineId: number;
  orderNo: string | null;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  quantity: number;
  baseQuantity: number;
  unitPrice: number;
  discountPercent: number;
  discountAmount: number;
  amount: number;
  taxRate: number | null;
  taxAmount: number;
  promotional: boolean;
  returnedQuantity: number;
  adjustedAmount: number;
  description: string | null;
}

export interface SalesInvoice extends SalesDocumentBase {
  postingDate: string;
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  buyerName: string | null;
  buyerTaxCode: string | null;
  buyerAddress: string | null;
  invoiceDate: string;
  dueDate: string;
  einvoiceSeries: string | null;
  einvoiceNo: string | null;
  einvoiceDate: string | null;
  taxAuthorityCode: string | null;
  salesUserId: number | null;
  amount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  warnings: string[];
  lines: SalesInvoiceLine[];
}

export interface InvoiceableSalesLine {
  deliveryId: number | null;
  deliveryNo: string | null;
  deliveryDate: string | null;
  deliveryStatus: DeliveryStatus | null;
  deliveryLineId: number | null;
  orderId: number;
  orderNo: string;
  orderLineId: number;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  remainingQuantity: number;
  unitPrice: number;
  discountPercent: number;
  taxRate: number | null;
  serviceLine: boolean;
}

export interface SalesReturnLine {
  id: number;
  lineNo: number;
  deliveryLineId: number | null;
  deliveryNo: string | null;
  invoiceLineId: number | null;
  orderLineId: number | null;
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
  postingDate: string;
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  salesInvoiceId: number | null;
  salesInvoiceNo: string | null;
  warehouseId: number | null;
  inventoryDocumentId: number | null;
  inventoryDocumentNo: string | null;
  amount: number;
  taxAmount: number;
  totalAmount: number;
  postedAt: string | null;
  reversedAt: string | null;
  reverseDate: string | null;
  lines: SalesReturnLine[];
}

export interface ReturnableSalesLine {
  deliveryId: number | null;
  deliveryNo: string | null;
  deliveryLineId: number | null;
  invoiceLineId: number | null;
  orderLineId: number | null;
  productId: number;
  productCode: string | null;
  productName: string | null;
  unitId: number;
  lotNo: string | null;
  expiryDate: string | null;
  remainingQuantity: number;
  unitPrice: number | null;
  taxRate: number | null;
  remainingAmount: number | null;
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
  deliveryId: number | null;
  salesInvoiceId: number | null;
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

export interface QuickSaleResult {
  orderId: number;
  orderNo: string;
  orderStatus: SalesDocumentStatus;
  deliveryId: number | null;
  deliveryNo: string | null;
  invoiceId: number | null;
  invoiceNo: string | null;
  message: string;
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

export interface PurchaseRequestResult {
  requisitionId: number;
  requisitionNo: string;
  lineCount: number;
}
