/** Kiểu dữ liệu báo cáo kho M10 (khớp DTO backend `reporting.application.dto`). */

export interface StockCardLine {
  postingDate: string;
  transactionType: string;
  referenceType: string;
  referenceId: number;
  referenceNo: string;
  reversal: boolean;
  warehouseCode: string;
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  partnerName: string | null;
  qualityStatus: string;
  inQuantity: number | null;
  outQuantity: number | null;
  balance: number;
}

export interface StockCard {
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  dateFrom: string;
  dateTo: string;
  opening: number;
  lines: StockCardLine[];
  totalIn: number;
  totalOut: number;
  closing: number;
  generatedAt: string;
}

export interface MovementRow {
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  opening: number;
  inQuantity: number;
  outQuantity: number;
  closing: number;
}

export interface InventoryMovement {
  dateFrom: string;
  dateTo: string;
  rows: MovementRow[];
  valueAvailable: boolean;
  generatedAt: string;
}

export interface SlowRow {
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  warehouseId: number;
  warehouseCode: string;
  onHand: number;
  lastInDate: string | null;
  lastOutDate: string | null;
  idleDays: number | null;
}

export interface SlowMoving {
  thresholdDays: number;
  cutoffDate: string;
  rows: SlowRow[];
  generatedAt: string;
}

export type ExceptionReason = 'EXPIRED' | 'QUALITY' | 'BLOCKED';

export interface ExceptionRow {
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  warehouseCode: string;
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  qualityStatus: string;
  reasons: ExceptionReason[];
  onHand: number;
}

export interface StockExceptions {
  asOf: string;
  rows: ExceptionRow[];
  generatedAt: string;
}

export interface MissingRow {
  productId: number;
  productCode: string;
  productName: string;
  productType: string;
  documentTypeCode: string;
  documentTypeName: string;
  expiredDocumentNo: string | null;
  expiredOn: string | null;
  onHand: number;
}

export interface MissingDocuments {
  asOf: string;
  rows: MissingRow[];
  generatedAt: string;
}

export interface TraceLine {
  postingDate: string;
  transactionType: string;
  referenceType: string;
  referenceId: number;
  referenceNo: string;
  reversal: boolean;
  warehouseCode: string;
  partnerId: number | null;
  partnerCode: string | null;
  partnerName: string | null;
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  quantity: number;
}

export interface CustomerTotal {
  partnerId: number;
  partnerCode: string | null;
  partnerName: string | null;
  issued: number;
  returned: number;
  net: number;
}

export interface LotTrace {
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  lotNo: string | null;
  expiryDate: string | null;
  serialNo: string | null;
  lines: TraceLine[];
  customers: CustomerTotal[];
  onHand: number;
  generatedAt: string;
}

export interface CustomerTrace {
  customerId: number;
  customerCode: string;
  customerName: string;
  dateFrom: string;
  dateTo: string;
  lines: TraceLine[];
  generatedAt: string;
}

/** Báo cáo mua / bán đợt 10B (BR-RPT-25, 26). */
export interface OrderRow {
  documentId: number;
  documentNo: string;
  documentDate: string;
  postingDate: string;
  partnerId: number;
  partnerCode: string;
  partnerName: string;
  status: string;
  expectedDate: string | null;
  deliveryStatus: string | null;
  overdueDays: number | null;
  totalAmount: number;
  returnedAmount: number;
  paidAmount: number;
  remainingAmount: number;
}

export interface OrderProgress {
  documentType: 'DMH' | 'DBH';
  dateFrom: string;
  dateTo: string;
  rows: OrderRow[];
  total: {
    orderCount: number;
    overdueCount: number;
    totalAmount: number;
    returnedAmount: number;
    paidAmount: number;
    remainingAmount: number;
  };
  generatedAt: string;
}

export interface PriceRow {
  productId: number;
  productCode: string;
  productName: string;
  postingDate: string;
  documentId: number;
  documentNo: string;
  supplierCode: string;
  supplierName: string;
  unitName: string;
  quantity: number;
  unitPrice: number;
  discountPercent: number;
  netUnitPrice: number | null;
  baseUnitName: string;
  netBasePrice: number | null;
}

export interface PriceSummary {
  productId: number;
  productCode: string;
  productName: string;
  baseUnitName: string;
  purchaseCount: number;
  minPrice: number | null;
  maxPrice: number | null;
  lastPrice: number | null;
  lastDate: string;
  lastSupplierName: string;
}

export interface PriceHistory {
  dateFrom: string;
  dateTo: string;
  rows: PriceRow[];
  summaries: PriceSummary[];
  generatedAt: string;
}

export interface DefectRow {
  complaintId: number;
  complaintNo: string;
  receivedDate: string;
  customerCode: string;
  customerName: string;
  productId: number;
  productCode: string;
  productName: string;
  lotNo: string | null;
  serialNo: string | null;
  quantity: number;
  status: string;
  decision: string | null;
  supplierId: number | null;
  supplierCode: string | null;
  supplierName: string | null;
}

export interface SupplierTotal {
  supplierId: number | null;
  supplierCode: string | null;
  supplierName: string | null;
  complaintCount: number;
  productCount: number;
  quantity: number;
}

export interface SupplierDefects {
  dateFrom: string;
  dateTo: string;
  rows: DefectRow[];
  suppliers: SupplierTotal[];
  generatedAt: string;
}

export interface JournalRow {
  postingDate: string;
  documentType: string;
  documentId: number;
  documentNo: string;
  documentDate: string;
  invoiceDate: string | null;
  invoiceNo: string | null;
  customerCode: string;
  customerName: string;
  description: string | null;
  receivable: number;
  goodsAmount: number;
  serviceAmount: number;
  deductionAmount: number;
  taxAmount: number;
  reversal: boolean;
}

export interface SalesJournal {
  dateFrom: string;
  dateTo: string;
  rows: JournalRow[];
  total: {
    receivable: number;
    goodsAmount: number;
    serviceAmount: number;
    deductionAmount: number;
    taxAmount: number;
  };
  generatedAt: string;
}

export interface BacklogRow {
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  warehouseId: number;
  warehouseCode: string;
  ordered: number;
  orderCount: number;
  onHand: number;
  reserved: number;
  available: number;
  incoming: number;
  shortage: number;
}

export interface StockBacklog {
  rows: BacklogRow[];
  generatedAt: string;
}

/** Dashboard điều hành đợt 10C (BR-RPT-01, 02, 04..06, 10..13). */
export interface DashboardTrade {
  grossAmount: number;
  deductionAmount: number;
  netAmount: number;
  documentCount: number;
  costAvailable: boolean;
}

export interface DashboardDebt {
  totalAmount: number;
  itemCount: number;
  overdueAmount: number;
  overdueCount: number;
  /** Theo mốc tuổi nợ của công ty; {@code toDays} null: không giới hạn trên. */
  overdueBuckets: { fromDays: number; toDays: number | null; amount: number }[];
}

export interface DashboardBacklog {
  orderCount: number;
  totalAmount: number;
  overdueCount: number;
}

export interface DashboardRanked {
  id: number;
  code: string;
  name: string;
  amount: number;
}

export interface DashboardSummary {
  dateFrom: string;
  dateTo: string;
  today: string;
  revenue: DashboardTrade;
  purchases: DashboardTrade;
  receivables: DashboardDebt;
  payables: DashboardDebt;
  expiry: { warningDays: number; warningUntil: string; expiredCount: number; expiringCount: number };
  purchaseBacklog: DashboardBacklog;
  salesBacklog: DashboardBacklog;
  topProducts: DashboardRanked[];
  topCustomers: DashboardRanked[];
  topSuppliers: DashboardRanked[];
  generatedAt: string;
}

export type TradeKind = 'revenue' | 'purchases';

export interface TradeDetails {
  kind: TradeKind;
  dateFrom: string;
  dateTo: string;
  rows: {
    entryDate: string;
    documentType: string;
    documentId: number;
    documentNo: string;
    reversal: boolean;
    partnerCode: string;
    partnerName: string;
    amount: number;
  }[];
  total: number;
  generatedAt: string;
}

export interface DebtDetails {
  debtType: 'RECEIVABLE' | 'PAYABLE';
  today: string;
  rows: {
    debtItemId: number;
    sourceType: string;
    sourceId: number;
    sourceNo: string;
    invoiceNo: string | null;
    partnerCode: string;
    partnerName: string;
    documentDate: string;
    dueDate: string;
    originalAmount: number;
    settledAmount: number;
    remainingAmount: number;
    overdueDays: number;
  }[];
  totalAmount: number;
  overdueAmount: number;
  generatedAt: string;
}

export interface ExpiryDetails {
  warningDays: number;
  today: string;
  warningUntil: string;
  rows: {
    productId: number;
    productCode: string;
    productName: string;
    unitName: string;
    warehouseCode: string;
    lotId: number;
    lotNo: string;
    expiryDate: string;
    daysLeft: number;
    onHand: number;
  }[];
  generatedAt: string;
}

export interface BacklogDetails {
  documentType: 'DMH' | 'DBH';
  today: string;
  rows: {
    documentId: number;
    documentNo: string;
    documentDate: string;
    partnerCode: string;
    partnerName: string;
    status: string;
    deliveryStatus: string | null;
    expectedDate: string | null;
    overdueDays: number;
    totalAmount: number;
  }[];
  totalAmount: number;
  generatedAt: string;
}

/** Dòng tiền dự kiến đợt 10D (BR-RPT-09, 30, BR-CSH-06). */
export type ForecastSource = 'RECEIVABLE' | 'SALES_ORDER' | 'PAYABLE' | 'PURCHASE_ORDER';
export type ForecastGranularity = 'DAY' | 'WEEK' | 'MONTH';

export interface CashForecast {
  granularity: ForecastGranularity;
  today: string;
  cashBalanceIncluded: boolean;
  openingBalance: number;
  cashAccounts: { id: number; code: string; name: string; accountType: string; balance: number }[];
  /** OVERDUE, P1..Pn, LATER. */
  columns: { key: string; from: string | null; to: string | null }[];
  lines: { source: ForecastSource; direction: 'IN' | 'OUT'; amounts: number[]; total: number }[];
  inflow: number[];
  outflow: number[];
  net: number[];
  closing: number[];
  generatedAt: string;
}

export interface CashForecastItems {
  source: ForecastSource | null;
  dateFrom: string | null;
  dateTo: string | null;
  today: string;
  rows: {
    source: ForecastSource;
    documentType: string;
    documentId: number;
    documentNo: string;
    partnerCode: string;
    partnerName: string;
    expectedDate: string;
    overdueDays: number;
    amount: number;
  }[];
  total: number;
  generatedAt: string;
}
