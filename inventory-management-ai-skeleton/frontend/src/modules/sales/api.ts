import { getOne, getPage, postWithKey, putJson } from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type {
  InvoiceableSalesLine,
  PriceSuggestion,
  PurchaseRequestResult,
  QuickSaleResult,
  ReturnableSalesLine,
  SalesComplaint,
  SalesDelivery,
  SalesInvoice,
  SalesOrder,
  SalesQuote,
  SalesReportGrouping,
  SalesReturn,
  SalesSourceDocument,
  SalesSummary,
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

export const salesQuoteApi = {
  ...documentApi<SalesQuote>('/sales-quotes'),
  createOrder: (id: number, input: object, key: string) =>
    postWithKey<SalesOrder>(`/sales-quotes/${id}/create-order`, input, key),
};

export const salesOrderApi = {
  ...documentApi<SalesOrder>('/sales-orders'),
  createPurchaseRequest: (id: number, key: string) =>
    postWithKey<PurchaseRequestResult>(`/sales-orders/${id}/create-purchase-request`, undefined, key),
};

export const salesDeliveryApi = documentApi<SalesDelivery>('/sales-deliveries');

export const salesInvoiceApi = {
  ...documentApi<SalesInvoice>('/sales-invoices'),
  invoiceableLines: (params: { branchId: number; customerId: number }) =>
    getOne<InvoiceableSalesLine[]>('/sales-invoices/invoiceable-lines', params),
};

export const salesReturnApi = {
  ...documentApi<SalesReturn>('/sales-returns'),
  returnableLines: (params: { branchId: number; customerId: number; invoiceId?: number }) =>
    getOne<ReturnableSalesLine[]>('/sales-returns/returnable-lines', params),
  createExchangeDelivery: (id: number, key: string) =>
    postWithKey<SalesDelivery>(`/sales-returns/${id}/create-exchange-delivery`, undefined, key),
};

export const salesComplaintApi = documentApi<SalesComplaint>('/sales-complaints');

export const salesOperationApi = {
  quickSale: (input: object, key: string) => postWithKey<QuickSaleResult>('/sales/quick-sale', input, key),
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
