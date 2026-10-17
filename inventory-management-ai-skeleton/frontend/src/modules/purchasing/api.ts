import {
  downloadFile,
  getOne,
  getPage,
  idList,
  postWithKey,
  putJson,
  uploadFile,
} from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type { PrintCompany } from '../../shared/components/CompanyPrintButton';
import type {
  CostAllocationSource,
  OrderLineImportResult,
  PurchaseCostAllocation,
  PurchaseLedger,
  PurchaseLedgerParams,
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
  /** {@code companyId}: công ty in đầu phiếu (D-58); trống là công ty của đơn. */
  stockInSlip: (id: number, companyId?: number) =>
    getOne<StockInSlip>(`/purchase-orders/${id}/stock-in-slip`, { companyId }),
  printCompanies: () => getOne<PrintCompany[]>('/purchase-orders/print-companies'),
  downloadLineTemplate: () => downloadFile('/purchase-orders/line-import-template', 'mau-dong-don-mua.xlsx'),
  /** Chỉ đọc file, trả dòng để điền form; không lưu đơn (server không cần Idempotency-Key, gửi kèm vô hại). */
  importLines: (file: File, companyId: number, documentDate: string | undefined, key: string) =>
    uploadFile<OrderLineImportResult>('/purchase-orders/line-import', file, { companyId, documentDate }, key),
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

export const purchaseLedgerApi = {
  get: (params: PurchaseLedgerParams) =>
    getOne<PurchaseLedger>('/purchasing/reports/detail-ledger', {
      ...params,
      productIds: idList(params.productIds),
      supplierIds: idList(params.supplierIds),
    }),
};
