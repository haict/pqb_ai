import { getOne, idList } from '../../shared/api/rest';
import type {
  BacklogDetails,
  CashForecast,
  CashForecastItems,
  ForecastGranularity,
  ForecastSource,
  CustomerTrace,
  DashboardSummary,
  DebtDetails,
  ExpiryDetails,
  TradeDetails,
  TradeKind,
  OrderProgress,
  PriceHistory,
  SalesJournal,
  StockBacklog,
  SupplierDefects,
  InventoryMovement,
  LotTrace,
  MissingDocuments,
  SlowMoving,
  StockCard,
  StockExceptions,
} from './types';

/** Báo cáo kho M10 (`/reports/...`, ADR-18). */
export const inventoryReportApi = {
  stockCard: (params: {
    companyId: number;
    warehouseId?: number;
    productId?: number;
    lotId?: number;
    serialId?: number;
    dateFrom: string;
    dateTo: string;
  }) => getOne<StockCard>('/reports/stock-card', params),
  movement: (params: {
    companyId: number;
    warehouseId?: number;
    productCategoryId?: number;
    productIds?: number[];
    dateFrom: string;
    dateTo: string;
  }) =>
    getOne<InventoryMovement>('/reports/inventory-movement', {
      ...params,
      productIds: idList(params.productIds),
    }),
  slowMoving: (params: { companyId: number; warehouseId?: number; days?: number }) =>
    getOne<SlowMoving>('/reports/slow-moving', params),
  exceptions: (params: { companyId: number; warehouseId?: number }) =>
    getOne<StockExceptions>('/reports/stock-exceptions', params),
  missingDocuments: (params: { companyId: number; warehouseId?: number }) =>
    getOne<MissingDocuments>('/reports/missing-documents', params),
  lotTrace: (params: { companyId: number; lotId?: number; serialId?: number }) =>
    getOne<LotTrace>('/reports/lot-trace', params),
  customerTrace: (params: { companyId: number; customerId: number; dateFrom: string; dateTo: string }) =>
    getOne<CustomerTrace>('/reports/customer-trace', params),
};

interface PeriodParams {
  companyId: number;
  branchId?: number;
  dateFrom: string;
  dateTo: string;
}

/** Báo cáo mua / bán đợt 10B (`/reports/...`). */
export const orderReportApi = {
  purchaseProgress: (params: PeriodParams & { supplierId?: number; status?: string }) =>
    getOne<OrderProgress>('/reports/purchase-order-progress', params),
  priceHistory: (params: PeriodParams & { productIds?: number[]; supplierId?: number }) =>
    getOne<PriceHistory>('/reports/purchase-price-history', {
      ...params,
      productIds: idList(params.productIds),
    }),
  supplierDefects: (params: PeriodParams) => getOne<SupplierDefects>('/reports/supplier-defects', params),
  salesProgress: (params: PeriodParams & { customerId?: number; status?: string }) =>
    getOne<OrderProgress>('/reports/sales-order-progress', params),
  salesJournal: (params: PeriodParams) => getOne<SalesJournal>('/reports/sales-journal', params),
  stockBacklog: (params: { companyId: number; warehouseId?: number; productIds?: number[] }) =>
    getOne<StockBacklog>('/reports/stock-backlog', { ...params, productIds: idList(params.productIds) }),
};

interface ScopeParams {
  companyId: number;
  branchId?: number;
}

/** Dashboard điều hành đợt 10C (`/dashboard/...`): chỉ tiêu và danh sách chi tiết đến chứng từ nguồn (BR-RPT-11). */
export const dashboardApi = {
  summary: (params: PeriodParams & { top?: number }) =>
    getOne<DashboardSummary>('/dashboard/summary', params),
  trade: (kind: TradeKind, params: PeriodParams & { partnerId?: number; productId?: number }) =>
    getOne<TradeDetails>(`/dashboard/${kind}/details`, params),
  debts: (kind: 'receivables' | 'payables', params: ScopeParams) =>
    getOne<DebtDetails>(`/dashboard/${kind}/details`, params),
  expiring: (params: ScopeParams) => getOne<ExpiryDetails>('/dashboard/expiring/details', params),
  backlog: (kind: 'purchase-backlog' | 'sales-backlog', params: ScopeParams) =>
    getOne<BacklogDetails>(`/dashboard/${kind}/details`, params),
};

/** Dòng tiền dự kiến đợt 10D (`/reports/cash-flow-forecast`). */
export const cashForecastApi = {
  forecast: (params: ScopeParams & { granularity?: ForecastGranularity; periods?: number }) =>
    getOne<CashForecast>('/reports/cash-flow-forecast', params),
  items: (params: ScopeParams & { source?: ForecastSource; dateFrom?: string; dateTo?: string }) =>
    getOne<CashForecastItems>('/reports/cash-flow-forecast/items', params),
};
