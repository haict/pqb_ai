import { getOne, getPage, idList, postWithKey, putJson } from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type { PrintCompany } from '../../shared/components/CompanyPrintButton';
import type {
  PriceSuggestion,
  ReturnableSalesLine,
  SalesComplaint,
  SalesLedger,
  SalesLedgerParams,
  SalesOrder,
  SalesReportGrouping,
  SalesReturn,
  SalesSourceDocument,
  SalesSummary,
  StockOutSlip,
} from './types';

/** API chứng từ bán hàng chung: danh sách, chi tiết, lập, sửa, hành động. */
function documentApi<T>(path: string) {
  return {
    list: <F extends object>(params: ListParams & F) => getPage<T>(path, params),
    get: (id: number) => getOne<T>(`${path}/${id}`),
    create: (input: object, key: string) => postWithKey<T>(path, input, key),
    update: (id: number, input: object) => putJson<T>(`${path}/${id}`, input),
    act: (id: number, action: string, body: object | undefined, key: string) =>
      postWithKey<T>(`${path}/${id}/${action}`, body, key),
  };
}

export const salesPriceApi = {
  price: (params: {
    customerId?: number;
    productId: number;
    unitId: number;
    quantity?: number;
    date?: string;
  }) => getOne<PriceSuggestion>('/sales/price', params),
};

export const salesOrderApi = {
  ...documentApi<SalesOrder>('/sales-orders'),
  /** {@code companyId}: công ty in đầu phiếu (D-58); trống là công ty của đơn. */
  stockOutSlip: (id: number, companyId?: number) =>
    getOne<StockOutSlip>(`/sales-orders/${id}/stock-out-slip`, { companyId }),
  printCompanies: () => getOne<PrintCompany[]>('/sales-orders/print-companies'),
};

export const salesReturnApi = {
  ...documentApi<SalesReturn>('/sales-returns'),
  returnableLines: (params: { branchId: number; customerId: number; orderId: number }) =>
    getOne<ReturnableSalesLine[]>('/sales-returns/returnable-lines', params),
};

export const salesComplaintApi = documentApi<SalesComplaint>('/sales-complaints');

export const salesReportApi = {
  summary: (params: {
    companyId: number;
    branchId?: number;
    dateFrom: string;
    dateTo: string;
    groupBy: SalesReportGrouping;
  }) => getOne<SalesSummary>('/sales/reports/summary', params),
  sources: (params: {
    companyId: number;
    branchId?: number;
    dateFrom: string;
    dateTo: string;
    groupBy: SalesReportGrouping;
    key: number | null;
  }) => getOne<SalesSourceDocument[]>('/sales/reports/sources', params),
};

export const salesLedgerApi = {
  get: (params: SalesLedgerParams) =>
    getOne<SalesLedger>('/sales/reports/detail-ledger', {
      ...params,
      productIds: idList(params.productIds),
      customerIds: idList(params.customerIds),
    }),
};
