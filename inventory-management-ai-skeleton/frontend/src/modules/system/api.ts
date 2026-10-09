import { httpClient, IDEMPOTENCY_HEADER } from '../../shared/api/httpClient';
import type { ApiResponse, PageResponse } from '../../shared/api/types';
import type { ListParams } from '../../shared/api/useServerTable';
import type { RecordStatus } from '../../shared/components/StatusTag';
import type {
  Branch,
  Company,
  DataScope,
  Department,
  LoginLog,
  Permission,
  Role,
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

/** Danh mục đầy đủ (tối đa 200) cho ô chọn. */
export const ALL = { page: 0, size: 200 };
