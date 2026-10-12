import { getOne, getPage, patchStatus, postWithKey, putJson } from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type { RecordStatus } from '../../shared/components/StatusTag';
import type {
  Aging,
  CashAccount,
  CashBook,
  CashDocument,
  CashCount,
  CashFlow,
  DebtAdjustment,
  DebtItem,
  DebtLedger,
  DebtOffset,
  DebtOpening,
  DebtPrepayment,
  DebtReconciliation,
  DebtStatement,
  DebtType,
} from './types';

export const debtOpeningApi = {
  list: <F extends object>(params: ListParams & F) => getPage<DebtOpening>('/debt-openings', params),
  get: (id: number) => getOne<DebtOpening>(`/debt-openings/${id}`),
  create: (input: object, key: string) => postWithKey<DebtOpening>('/debt-openings', input, key),
  update: (id: number, input: object) => putJson<DebtOpening>(`/debt-openings/${id}`, input),
  act: (id: number, action: string, body: object | undefined, key: string) =>
    postWithKey<DebtOpening>(`/debt-openings/${id}/${action}`, body, key),
};

export const debtApi = {
  items: <F extends object>(params: ListParams & F) => getPage<DebtItem>('/debt-items', params),
  aging: (params: object) => getOne<Aging>('/debt-items/aging', params),
  ledger: (params: {
    companyId: number;
    debtType: DebtType;
    partnerId: number;
    dateFrom: string;
    dateTo: string;
  }) => getOne<DebtLedger>('/debt-ledger', params),
  prepayments: (params: { companyId: number; debtType: DebtType; partnerId: number }) =>
    getOne<DebtPrepayment[]>('/debt-prepayments', params),
};

export const cashAccountApi = {
  list: <F extends object>(params: ListParams & F) => getPage<CashAccount>('/cash-accounts', params),
  create: (input: object, key: string) => postWithKey<CashAccount>('/cash-accounts', input, key),
  update: (id: number, input: object) => putJson<CashAccount>(`/cash-accounts/${id}`, input),
  changeStatus: (item: CashAccount, status: RecordStatus, reason: string) =>
    patchStatus<CashAccount>(`/cash-accounts/${item.id}`, status, item.version, reason),
};

export const cashDocumentApi = {
  list: <F extends object>(params: ListParams & F) => getPage<CashDocument>('/cash-documents', params),
  get: (id: number) => getOne<CashDocument>(`/cash-documents/${id}`),
  create: (input: object, key: string) => postWithKey<CashDocument>('/cash-documents', input, key),
  update: (id: number, input: object) => putJson<CashDocument>(`/cash-documents/${id}`, input),
  act: (id: number, action: string, body: object | undefined, key: string) =>
    postWithKey<CashDocument>(`/cash-documents/${id}/${action}`, body, key),
};

export const cashBookApi = {
  book: (params: { cashAccountId: number; dateFrom: string; dateTo: string }) =>
    getOne<CashBook>('/cash-book', params),
  flow: (params: { companyId: number; branchId?: number; dateFrom: string; dateTo: string }) =>
    getOne<CashFlow>('/cash-flow', params),
};

/** API chứng từ ghi sổ chung: danh sách, chi tiết, lập, sửa, hành động. */
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

export const debtOffsetApi = documentApi<DebtOffset>('/debt-offsets');
export const debtAdjustmentApi = documentApi<DebtAdjustment>('/debt-adjustments');
export const cashCountApi = documentApi<CashCount>('/cash-counts');

export const reconciliationApi = {
  statement: (params: {
    companyId: number;
    branchId?: number;
    debtType: DebtType;
    partnerId: number;
    dateFrom: string;
    dateTo: string;
  }) => getOne<DebtStatement>('/debt-statements', params),
  list: <F extends object>(params: ListParams & F) =>
    getPage<DebtReconciliation>('/debt-reconciliations', params),
  create: (input: object, key: string) =>
    postWithKey<DebtReconciliation>('/debt-reconciliations', input, key),
  act: (id: number, action: string, body: object, key: string) =>
    postWithKey<DebtReconciliation>(`/debt-reconciliations/${id}/${action}`, body, key),
};
