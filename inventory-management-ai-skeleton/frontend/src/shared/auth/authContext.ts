import { createContext, useContext } from 'react';
import type { Me } from './types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  user: Me | null;
  workingBranchId: number | null;
  login(username: string, password: string): Promise<Me>;
  logout(): Promise<void>;
  changePassword(currentPassword: string, newPassword: string): Promise<void>;
  selectWorkingBranch(branchId: number): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth phải dùng bên trong AuthProvider');
  }
  return value;
}

/** Kiểm tra quyền để ẩn/hiện chức năng; server vẫn kiểm tra quyền thật (frontend.md mục 6). */
export function useCan(): (permission: string) => boolean {
  const { user } = useAuth();
  return (permission: string) => user?.permissions.includes(permission) ?? false;
}
