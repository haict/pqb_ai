import { httpClient, IDEMPOTENCY_HEADER } from '../../shared/api/httpClient';
import type { ApiResponse, PageResponse } from '../../shared/api/types';
import type { ListParams } from '../../shared/api/useServerTable';
import type { RecordStatus } from '../../shared/components/StatusTag';
import type {
  ApprovalMode,
  ApprovalRequest,
  ApprovalWorkflow,
  ApproverType,
  AppNotification,
  Delegation,
  DocumentType,
  Branch,
  Company,
  DataScope,
  Department,
  DocumentNumberFormat,
  FiscalPeriod,
  LicenseStatus,
  LoginLog,
  NumberResetCycle,
  PeriodLockResult,
  PeriodModule,
  Permission,
  Role,
  SystemParameter,
  UserAccount,
} from './types';

/** Tham số danh sách: phân trang, sắp xếp và các bộ lọc server hỗ trợ (docs/04 M01). */
export type ListQuery = ListParams &
  Partial<{
    keyword: string;
    status: RecordStatus;
    companyId: number;
    branchId: number;
    username: string;
    event: string;
    documentType: string;
    from: string;
    to: string;
  }>;

async function getPage<T>(url: string, params: object): Promise<PageResponse<T>> {
  const response = await httpClient.get<ApiResponse<PageResponse<T>>>(url, { params: withoutEmpty(params) });
  return response.data.data;
}

async function create<T>(url: string, body: object, idempotencyKey: string): Promise<T> {
  const response = await httpClient.post<ApiResponse<T>>(url, body, {
    headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });
  return response.data.data;
}

async function put<T>(url: string, body: object): Promise<T> {
  const response = await httpClient.put<ApiResponse<T>>(url, body);
  return response.data.data;
}

async function changeStatus<T>(
  url: string,
  status: RecordStatus,
  version: number,
  reason: string,
): Promise<T> {
  const response = await httpClient.patch<ApiResponse<T>>(`${url}/status`, { status, version, reason });
  return response.data.data;
}

function withoutEmpty(params: object): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== ''),
  );
}

export interface CompanyInput {
  code?: string;
  name: string;
  taxCode?: string;
  address?: string;
  phone?: string;
  email?: string;
}

export const companyApi = {
  list: (params: ListQuery) => getPage<Company>('/companies', params),
  create: (input: CompanyInput, key: string) => create<Company>('/companies', input, key),
  update: (id: number, input: CompanyInput & { version: number }) => put<Company>(`/companies/${id}`, input),
  changeStatus: (item: Company, status: RecordStatus, reason: string) =>
    changeStatus<Company>(`/companies/${item.id}`, status, item.version, reason),
};

export interface BranchInput {
  companyId?: number;
  code?: string;
  name: string;
  address?: string;
  phone?: string;
}

export const branchApi = {
  list: (params: ListQuery) => getPage<Branch>('/branches', params),
  create: (input: BranchInput, key: string) => create<Branch>('/branches', input, key),
  update: (id: number, input: BranchInput & { version: number }) => put<Branch>(`/branches/${id}`, input),
  changeStatus: (item: Branch, status: RecordStatus, reason: string) =>
    changeStatus<Branch>(`/branches/${item.id}`, status, item.version, reason),
};

export interface DepartmentInput {
  branchId?: number;
  code?: string;
  name: string;
}

export const departmentApi = {
  list: (params: ListQuery) => getPage<Department>('/departments', params),
  create: (input: DepartmentInput, key: string) => create<Department>('/departments', input, key),
  update: (id: number, input: { name: string; version: number }) =>
    put<Department>(`/departments/${id}`, input),
  changeStatus: (item: Department, status: RecordStatus, reason: string) =>
    changeStatus<Department>(`/departments/${item.id}`, status, item.version, reason),
};

export interface CreateUserInput {
  username: string;
  fullName: string;
  email?: string;
  phone?: string;
  companyId: number;
  defaultBranchId?: number;
  ownDataOnly: boolean;
  initialPassword: string;
  roleIds: number[];
  dataScopes: DataScope[];
}

export interface UpdateUserInput {
  fullName: string;
  email?: string;
  phone?: string;
  defaultBranchId?: number;
  ownDataOnly: boolean;
  version: number;
}

export const userApi = {
  list: (params: ListQuery) => getPage<UserAccount>('/users', params),
  create: (input: CreateUserInput, key: string) => create<UserAccount>('/users', input, key),
  update: (id: number, input: UpdateUserInput) => put<UserAccount>(`/users/${id}`, input),
  changeStatus: (item: UserAccount, status: RecordStatus, reason: string) =>
    changeStatus<UserAccount>(`/users/${item.id}`, status, item.version, reason),
  resetPassword: (id: number, newPassword: string, key: string) =>
    create<UserAccount>(`/users/${id}/reset-password`, { newPassword }, key),
  assignRoles: (id: number, roleIds: number[]) => put<UserAccount>(`/users/${id}/roles`, { roleIds }),
  assignDataScopes: (id: number, dataScopes: DataScope[]) =>
    put<UserAccount>(`/users/${id}/data-scopes`, { dataScopes }),
};

export const roleApi = {
  list: (params: ListQuery) => getPage<Role>('/roles', params),
  create: (input: { code: string; name: string; description?: string }, key: string) =>
    create<Role>('/roles', input, key),
  update: (id: number, input: { name: string; description?: string; version: number }) =>
    put<Role>(`/roles/${id}`, input),
  changeStatus: (item: Role, status: RecordStatus, reason: string) =>
    changeStatus<Role>(`/roles/${item.id}`, status, item.version, reason),
  assignPermissions: (id: number, permissionCodes: string[]) =>
    put<Role>(`/roles/${id}/permissions`, { permissionCodes }),
  permissions: async (): Promise<Permission[]> => {
    const response = await httpClient.get<ApiResponse<Permission[]>>('/permissions');
    return response.data.data;
  },
};

export const loginLogApi = {
  list: (params: ListQuery) => getPage<LoginLog>('/login-logs', params),
};

export interface PeriodLockInput {
  module: PeriodModule;
  branchIds: number[];
  reason?: string;
}

export const fiscalPeriodApi = {
  list: async (companyId: number, year: number, branchId: number): Promise<FiscalPeriod[]> => {
    const response = await httpClient.get<ApiResponse<FiscalPeriod[]>>('/fiscal-periods', {
      params: { companyId, year, branchId },
    });
    return response.data.data;
  },
  generate: (companyId: number, year: number, key: string) =>
    create<{ companyId: number; year: number; created: number }>(
      '/fiscal-periods/generate',
      { companyId, year },
      key,
    ),
  lock: (periodId: number, input: PeriodLockInput, key: string) =>
    create<PeriodLockResult>(`/fiscal-periods/${periodId}/lock`, input, key),
  unlock: (periodId: number, input: PeriodLockInput, key: string) =>
    create<PeriodLockResult>(`/fiscal-periods/${periodId}/unlock`, input, key),
};

export const LICENSE_QUERY_KEY = ['license'];

/** Bản quyền phần mềm (D-59): trạng thái cho mọi người đã đăng nhập; nạp license cần quyền license:manage. */
export const licenseApi = {
  status: async (): Promise<LicenseStatus> => {
    const response = await httpClient.get<ApiResponse<LicenseStatus>>('/license');
    return response.data.data;
  },
  install: (license: string) => put<LicenseStatus>('/license', { license }),
};

export const parameterApi = {
  list: async (companyId: number): Promise<SystemParameter[]> => {
    const response = await httpClient.get<ApiResponse<SystemParameter[]>>('/parameters', {
      params: { companyId },
    });
    return response.data.data;
  },
  update: (key: string, input: { companyId: number; value: string; version: number }) =>
    put<SystemParameter>(`/parameters/${encodeURIComponent(key)}`, input),
};

export interface DocumentNumberFormatInput {
  companyId: number;
  documentType: string;
  prefix: string;
  sequenceLength: number;
  resetCycle: NumberResetCycle;
}

export const documentNumberFormatApi = {
  list: (params: ListQuery) => getPage<DocumentNumberFormat>('/document-number-formats', params),
  schedule: (input: DocumentNumberFormatInput, key: string) =>
    create<DocumentNumberFormat>('/document-number-formats', input, key),
  remove: async (id: number): Promise<void> => {
    await httpClient.delete(`/document-number-formats/${id}`);
  },
};

export interface ApprovalWorkflowInput {
  companyId?: number;
  documentType?: string;
  name: string;
  branchId?: number | null;
  minAmount?: number | null;
  minDiscountPercent?: number | null;
  priority: number;
  steps: { approverType: ApproverType; roleId?: number | null; userId?: number | null }[];
}

export const approvalWorkflowApi = {
  list: (params: ListQuery & { documentType?: string }) =>
    getPage<ApprovalWorkflow>('/approval-workflows', params),
  create: (input: ApprovalWorkflowInput, key: string) =>
    create<ApprovalWorkflow>('/approval-workflows', input, key),
  update: (id: number, input: ApprovalWorkflowInput & { version: number }) =>
    put<ApprovalWorkflow>(`/approval-workflows/${id}`, input),
  changeStatus: (item: ApprovalWorkflow, status: RecordStatus, reason: string) =>
    changeStatus<ApprovalWorkflow>(`/approval-workflows/${item.id}`, status, item.version, reason),
};

export const documentTypeApi = {
  list: async (): Promise<DocumentType[]> => {
    const response = await httpClient.get<ApiResponse<DocumentType[]>>('/document-types');
    return response.data.data;
  },
  update: (
    code: string,
    input: { approvalMode: ApprovalMode; selfApprovalAllowed: boolean; version: number },
  ) => put<DocumentType>(`/document-types/${code}`, input),
};

export const approvalApi = {
  pending: (params: ListParams) => getPage<ApprovalRequest>('/approvals/pending', params),
  get: async (id: number): Promise<ApprovalRequest> => {
    const response = await httpClient.get<ApiResponse<ApprovalRequest>>(`/approvals/${id}`);
    return response.data.data;
  },
};

export interface DelegationInput {
  delegatorUserId?: number;
  delegateUserId: number;
  documentType?: string;
  validFrom?: string;
  validTo: string;
  reason?: string;
}

export const delegationApi = {
  list: (params: ListParams) => getPage<Delegation>('/approval-delegations', params),
  create: (input: DelegationInput, key: string) => create<Delegation>('/approval-delegations', input, key),
  revoke: async (id: number): Promise<void> => {
    await httpClient.post(`/approval-delegations/${id}/revoke`);
  },
};

export interface UserLookup {
  id: number;
  username: string;
  fullName: string;
}

export const userLookupApi = {
  search: async (keyword?: string): Promise<UserLookup[]> => {
    const response = await httpClient.get<ApiResponse<UserLookup[]>>('/users/lookup', {
      params: keyword ? { keyword } : {},
    });
    return response.data.data;
  },
};

export const notificationApi = {
  list: (params: ListParams & { unreadOnly?: boolean }) => getPage<AppNotification>('/notifications', params),
  unreadCount: async (): Promise<number> => {
    const response = await httpClient.get<ApiResponse<{ count: number }>>('/notifications/unread-count');
    return response.data.data.count;
  },
  markRead: async (id: number): Promise<void> => {
    await httpClient.post(`/notifications/${id}/read`);
  },
  markAllRead: async (): Promise<void> => {
    await httpClient.post('/notifications/read-all');
  },
};

/** Danh mục đầy đủ (tối đa 200) cho ô chọn. */
export const ALL = { page: 0, size: 200 };
