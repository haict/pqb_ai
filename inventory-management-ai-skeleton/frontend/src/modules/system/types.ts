import type { RecordStatus } from '../../shared/components/StatusTag';

/** DTO khớp backend `system/application/dto` (docs/04 M01). */

export interface Company {
  id: number;
  code: string;
  name: string;
  taxCode: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  status: RecordStatus;
  version: number;
  updatedAt: string;
}

export interface Branch {
  id: number;
  companyId: number;
  code: string;
  name: string;
  address: string | null;
  phone: string | null;
  status: RecordStatus;
  version: number;
  updatedAt: string;
}

export interface Department {
  id: number;
  branchId: number;
  code: string;
  name: string;
  status: RecordStatus;
  version: number;
  updatedAt: string;
}

export type DataScopeType = 'COMPANY' | 'BRANCH' | 'WAREHOUSE';

export interface DataScope {
  type: DataScopeType;
  scopeId: number;
}

export interface UserAccount {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  companyId: number;
  defaultBranchId: number | null;
  status: RecordStatus;
  ownDataOnly: boolean;
  mustChangePassword: boolean;
  lockedUntil: string | null;
  lastLoginAt: string | null;
  roles: { id: number; code: string; name: string }[];
  dataScopes: DataScope[];
  version: number;
}

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  status: RecordStatus;
  permissions: string[];
  version: number;
}

export interface Permission {
  code: string;
  module: string;
  name: string;
}

export type LoginEvent =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILED'
  | 'ACCOUNT_LOCKED'
  | 'LOGOUT'
  | 'REFRESH_REUSE_DETECTED'
  | 'PASSWORD_CHANGED';

export interface LoginLog {
  id: number;
  occurredAt: string;
  event: LoginEvent;
  username: string | null;
  userId: number | null;
  ipAddress: string | null;
  userAgent: string | null;
}

export interface StatusFilter {
  keyword?: string;
  status?: RecordStatus;
}
