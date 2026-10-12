import { getOne, getPage, postWithKey, putJson } from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type {
  CostAllocationSource,
  InvoiceableLine,
  OpenRequisitionLine,
  PurchaseCostAllocation,
  PurchaseInvoice,
  PurchaseOrder,
  PurchaseReceipt,
  PurchaseRequisition,
  PurchaseReturn,
  ReturnableLine,
} from './types';

/** API chứng từ điều phối chung: danh sách, chi tiết, lập, sửa, hành động. */
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

export const requisitionApi = {
  ...documentApi<PurchaseRequisition>('/purchase-requisitions'),
  openLines: (params: { branchId: number; productId?: number }) =>
    getOne<OpenRequisitionLine[]>('/purchase-requisitions/open-lines', params),
};

export const purchaseOrderApi = documentApi<PurchaseOrder>('/purchase-orders');

export const purchaseReceiptApi = {
  ...documentApi<PurchaseReceipt>('/purchase-receipts'),
  receiveAndStock: (input: object, key: string) =>
    postWithKey<PurchaseReceipt>('/purchase-receipts/receive-and-stock', input, key),
};

export const purchaseInvoiceApi = {
  ...documentApi<PurchaseInvoice>('/purchase-invoices'),
  invoiceableLines: (params: { branchId: number; supplierId: number }) =>
    getOne<InvoiceableLine[]>('/purchase-invoices/invoiceable-lines', params),
};

export const purchaseReturnApi = {
  ...documentApi<PurchaseReturn>('/purchase-returns'),
  returnableLines: (params: { branchId: number; supplierId: number; invoiceId?: number }) =>
    getOne<ReturnableLine[]>('/purchase-returns/returnable-lines', params),
};

export const costAllocationApi = {
  ...documentApi<PurchaseCostAllocation>('/purchase-cost-allocations'),
  costLines: (params: { branchId: number }) =>
    getOne<CostAllocationSource[]>('/purchase-cost-allocations/cost-lines', params),
};
