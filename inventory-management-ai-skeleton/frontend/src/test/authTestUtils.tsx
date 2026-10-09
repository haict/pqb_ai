import { App as AntApp } from 'antd';
import type { ReactNode } from 'react';
import { AuthContext, type AuthContextValue } from '../shared/auth/authContext';
import type { Me } from '../shared/auth/types';

export const testUser: Me = {
  id: 1,
  username: 'admin',
  fullName: 'Quản trị',
  companyId: 1,
  defaultBranchId: 1,
  mustChangePassword: false,
  ownDataOnly: false,
  permissions: ['branch:view'],
  branches: [{ id: 1, companyId: 1, code: 'HN', name: 'Hà Nội' }],
};

export function authValue(overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  return {
    status: 'authenticated',
    user: testUser,
    workingBranchId: 1,
    login: vi.fn(),
    logout: vi.fn(),
    changePassword: vi.fn(),
    selectWorkingBranch: vi.fn(),
    ...overrides,
  };
}

export function WithAuth({ value, children }: { value: AuthContextValue; children: ReactNode }) {
  return (
    <AntApp>
      <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
    </AntApp>
  );
}
