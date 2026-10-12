import {
  downloadFile,
  getOne,
  getPage,
  patchStatus,
  postWithKey,
  putJson,
  uploadFile,
} from '../../shared/api/rest';
import type { ListParams } from '../../shared/api/useServerTable';
import type { RecordStatus } from '../../shared/components/StatusTag';
import type { Account, AccountImportResult, JournalEntry, JournalEntrySummary, PostingRule } from './types';

export const accountApi = {
  list: <F extends object>(params: ListParams & F) => getPage<Account>('/accounts', params),
  tree: (companyId: number) => getOne<Account[]>('/accounts/tree', { companyId }),
  create: (input: object, key: string) => postWithKey<Account>('/accounts', input, key),
  update: (id: number, input: object) => putJson<Account>(`/accounts/${id}`, input),
  changeStatus: (item: Account, status: RecordStatus, reason: string) =>
    patchStatus<Account>(`/accounts/${item.id}`, status, item.version, reason),
  downloadTemplate: () => downloadFile('/accounts/import-template', 'mau-he-thong-tai-khoan.xlsx'),
  importFile: (file: File, companyId: number, key: string) =>
    uploadFile<AccountImportResult>('/accounts/import', file, { companyId }, key),
};

export const postingRuleApi = {
  list: <F extends object>(params: ListParams & F) => getPage<PostingRule>('/posting-rules', params),
  create: (input: object, key: string) => postWithKey<PostingRule>('/posting-rules', input, key),
  update: (id: number, input: object) => putJson<PostingRule>(`/posting-rules/${id}`, input),
  changeStatus: (item: PostingRule, status: RecordStatus, reason: string) =>
    patchStatus<PostingRule>(`/posting-rules/${item.id}`, status, item.version, reason),
};

export const journalApi = {
  list: <F extends object>(params: ListParams & F) =>
    getPage<JournalEntrySummary>('/journal-entries', params),
  get: (id: number) => getOne<JournalEntry>(`/journal-entries/${id}`),
  bySource: (companyId: number, sourceType: string, sourceId: number) =>
    getOne<JournalEntry[]>('/journal-entries/by-source', { companyId, sourceType, sourceId }),
};
