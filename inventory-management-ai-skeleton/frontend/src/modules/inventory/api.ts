import { downloadFile, getOne, getPage, postWithKey, putJson, uploadFile } from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type {
  FefoAllocation,
  InventoryDocument,
  InventoryDocumentInput,
  InventoryDocumentSummary,
  InventoryTransaction,
  Lot,
  OpeningStockImportResult,
  Recall,
  Serial,
  StockAlert,
  StockBalance,
  StockCount,
  StockIssuePolicy,
  StockSummary,
} from './types';

/** Gọi API module kho (docs/04 mục kho). Hành động trạng thái luôn kèm Idempotency-Key. */

type Body = object | undefined;

function action<T>(base: string) {
  return (id: number, name: string, body: Body, key: string) =>
    postWithKey<T>(`${base}/${id}/${name}`, body, key);
}

export const documentApi = {
  list: <F extends object>(params: ListParams & F) =>
    getPage<InventoryDocumentSummary>('/inventory-documents', params),
  get: (id: number) => getOne<InventoryDocument>(`/inventory-documents/${id}`),
  create: (input: InventoryDocumentInput, key: string) =>
    postWithKey<InventoryDocument>('/inventory-documents', input, key),
  update: (id: number, input: InventoryDocumentInput) =>
    putJson<InventoryDocument>(`/inventory-documents/${id}`, input),
  act: action<InventoryDocument>('/inventory-documents'),
  downloadOpeningTemplate: () =>
    downloadFile('/inventory-documents/opening-import-template', 'mau-ton-kho-dau-ky.xlsx'),
  importOpening: (file: File, companyId: number, documentDate: string, key: string) =>
    uploadFile<OpeningStockImportResult>(
      '/inventory-documents/opening-import',
      file,
      { companyId, documentDate },
      key,
    ),
};

export const stockApi = {
  summaries: <F extends object>(params: ListParams & F) =>
    getPage<StockSummary>('/inventory/stock-summaries', params),
  balances: <F extends object>(params: ListParams & F) =>
    getPage<StockBalance>('/inventory/stock-balances', params),
  transactions: <F extends object>(params: ListParams & F) =>
    getPage<InventoryTransaction>('/inventory/transactions', params),
  lots: <F extends object>(params: ListParams & F) => getPage<Lot>('/inventory/lots', params),
  serials: <F extends object>(params: ListParams & F) => getPage<Serial>('/inventory/serials', params),
  fefo: (warehouseId: number, productId: number, quantity: number) =>
    getOne<FefoAllocation[]>('/inventory/fefo-suggestions', { warehouseId, productId, quantity }),
  alerts: (companyId: number, warehouseId?: number) =>
    getOne<StockAlert[]>('/inventory/alerts', { companyId, warehouseId }),
  block: (kind: 'lots' | 'serials', id: number, blocked: boolean, reason: string, key: string) =>
    postWithKey<null>(`/inventory/${kind}/${id}/${blocked ? 'block' : 'unblock'}`, { reason }, key),
};

export const stockCountApi = {
  list: <F extends object>(params: ListParams & F) => getPage<StockCount>('/stock-counts', params),
  get: (id: number) => getOne<StockCount>(`/stock-counts/${id}`),
  create: (input: object, key: string) => postWithKey<StockCount>('/stock-counts', input, key),
  count: (id: number, lines: object[]) => putJson<StockCount>(`/stock-counts/${id}/counts`, { lines }),
  act: action<StockCount>('/stock-counts'),
};

export const recallApi = {
  list: <F extends object>(params: ListParams & F) => getPage<Recall>('/recalls', params),
  get: (id: number) => getOne<Recall>(`/recalls/${id}`),
  create: (input: object, key: string) => postWithKey<Recall>('/recalls', input, key),
  act: action<Recall>('/recalls'),
};

export const policyApi = {
  list: <F extends object>(params: ListParams & F) =>
    getPage<StockIssuePolicy>('/stock-issue-policies', params),
  get: (id: number) => getOne<StockIssuePolicy>(`/stock-issue-policies/${id}`),
  create: (input: object, key: string) => postWithKey<StockIssuePolicy>('/stock-issue-policies', input, key),
  act: action<StockIssuePolicy>('/stock-issue-policies'),
};
