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

/** Phân hệ khóa kỳ riêng (BR-SYS-30). */
export type PeriodModule = 'INVENTORY' | 'DEBT' | 'CASH' | 'ACCOUNTING';

export interface PeriodLockStatus {
  module: PeriodModule;
  locked: boolean;
  changedAt: string | null;
  changedBy: string | null;
  reason: string | null;
}

export interface FiscalPeriod {
  id: number;
  companyId: number;
  year: number;
  month: number;
  startDate: string;
  endDate: string;
  locks: PeriodLockStatus[];
}

export interface PeriodLockResult {
  fiscalPeriodId: number;
  module: PeriodModule;
  locked: boolean;
  branchIds: number[];
}

export type ParameterValueType = 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'STRING' | 'ENUM';

export interface SystemParameter {
  key: string;
  module: string;
  name: string;
  description: string | null;
  valueType: ParameterValueType;
  value: string;
  defaultValue: string;
  minValue: number | null;
  maxValue: number | null;
  allowedValues: string[];
  lockAfterPosting: boolean;
  locked: boolean;
  customized: boolean;
  version: number;
}

export type NumberResetCycle = 'YEARLY' | 'MONTHLY';
export type DocumentNumberFormatStatus = 'CURRENT' | 'SCHEDULED' | 'SUPERSEDED';

export interface DocumentNumberFormat {
  id: number;
  companyId: number;
  documentType: string;
  prefix: string;
  sequenceLength: number;
  resetCycle: NumberResetCycle;
  effectiveFrom: string;
  status: DocumentNumberFormatStatus;
  version: number;
}

/** Lịch sử phê duyệt của chứng từ lập trước D-61 (chỉ đọc; hệ thống không còn bước duyệt). */
export type ApproverType = 'ROLE' | 'USER';
export type ApprovalRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type ApprovalActionType = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'CANCEL';

export interface DocumentType {
  code: string;
  module: string;
  name: string;
  version: number;
  updatedAt: string | null;
}

export interface ApprovalStep {
  stepNo: number;
  approverType: ApproverType;
  roleId: number | null;
  roleName: string | null;
  userId: number | null;
  userName: string | null;
}

export interface ApprovalAction {
  action: ApprovalActionType;
  stepNo: number | null;
  actorName: string | null;
  onBehalfOfName: string | null;
  comment: string | null;
  actedAt: string;
}

export interface ApprovalRequest {
  id: number;
  companyId: number;
  branchId: number;
  documentType: string;
  documentId: number;
  documentNo: string | null;
  amount: number | null;
  status: ApprovalRequestStatus;
  currentStepPosition: number;
  totalSteps: number;
  requestedBy: number;
  requestedByName: string | null;
  requestedAt: string;
  completedAt: string | null;
  steps: ApprovalStep[];
  actions: ApprovalAction[];
}

export interface AppNotification {
  id: number;
  category: string;
  title: string;
  message: string | null;
  linkType: string | null;
  linkId: number | null;
  createdAt: string;
  readAt: string | null;
}

/** Trạng thái bản quyền phần mềm (D-59); chỉ VALID, GRACE được ghi dữ liệu. */
export type LicenseState =
  'VALID' | 'GRACE' | 'EXPIRED' | 'MISSING' | 'INVALID' | 'CLOCK_TAMPERED' | 'COMPANY_NOT_LICENSED';

export interface LicenseStatus {
  state: LicenseState;
  writable: boolean;
  message: string;
  licenseId: string | null;
  customerName: string | null;
  taxCodes: string[];
  validTo: string | null;
  graceUntil: string | null;
  daysRemaining: number | null;
  issuedAt: string | null;
  installedAt: string | null;
  unlicensedCompanies: string[];
}
