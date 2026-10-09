/** Dữ liệu xác thực trả từ backend (docs/07-authorization.md mục 1.1). */

export interface BranchSummary {
  id: number;
  companyId: number;
  code: string;
  name: string;
}

export interface Me {
  id: number;
  username: string;
  fullName: string;
  companyId: number;
  defaultBranchId: number | null;
  mustChangePassword: boolean;
  ownDataOnly: boolean;
  permissions: string[];
  branches: BranchSummary[];
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  sessionExpiresAt: string;
  user: Me;
}
