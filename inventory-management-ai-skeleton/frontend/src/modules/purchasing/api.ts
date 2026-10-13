import { getOne, getPage, postWithKey, putJson } from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type {
  CostAllocationSource,
  PurchaseCostAllocation,
  PurchaseOrder,
  PurchaseReturn,
  ReturnableLine,
  StockInSlip,
} from './types';

/** API chứng từ mua chung: danh sách, chi tiết, lập, sửa, hành động. */
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

export const purchaseOrderApi = {
  ...documentApi<PurchaseOrder>('/purchase-orders'),
  stockInSlip: (id: number) => getOne<StockInSlip>(`/purchase-orders/${id}/stock-in-slip`),
};

export const purchaseReturnApi = {
  ...documentApi<PurchaseReturn>('/purchase-returns'),
  returnableLines: (params: { branchId: number; supplierId: number; orderId: number }) =>
    getOne<ReturnableLine[]>('/purchase-returns/returnable-lines', params),
};

export const costAllocationApi = {
  ...documentApi<PurchaseCostAllocation>('/purchase-cost-allocations'),
  costLines: (params: { branchId: number }) =>
    getOne<CostAllocationSource[]>('/purchase-cost-allocations/cost-lines', params),
};
