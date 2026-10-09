import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authClient, httpClient, refreshSession } from '../api/httpClient';
import type { ApiResponse } from '../api/types';
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext';
import { rememberedWorkingBranch, rememberWorkingBranch, sessionStore } from './sessionStore';
import type { Me, TokenResponse } from './types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<Me | null>(null);
  const [workingBranchId, setWorkingBranchId] = useState<number | null>(null);

  const applySession = useCallback((tokens: TokenResponse) => {
    sessionStore.setAccessToken(tokens.accessToken);
    const branchId = rememberedWorkingBranch(tokens.user);
    sessionStore.setWorkingBranchId(branchId);
    setWorkingBranchId(branchId);
    setUser(tokens.user);
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback(() => {
    sessionStore.clear();
    queryClient.clear();
    setUser(null);
    setWorkingBranchId(null);
    setStatus('anonymous');
  }, [queryClient]);

  // Tải lại trang: access token trong bộ nhớ đã mất, khôi phục phiên bằng cookie refresh token.
  useEffect(() => {
    let active = true;
    refreshSession()
      .then((tokens) => active && applySession(tokens))
      .catch(() => active && clearSession());
    return () => {
      active = false;
    };
  }, [applySession, clearSession]);

  useEffect(
    () =>
      sessionStore.subscribe((event) => {
        if (event === 'expired') {
          clearSession();
        }
        if (event === 'passwordChangeRequired') {
          setUser((current) => (current ? { ...current, mustChangePassword: true } : current));
        }
      }),
    [clearSession],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      workingBranchId,
      async login(username, password) {
        const response = await authClient.post<ApiResponse<TokenResponse>>('/login', { username, password });
        applySession(response.data.data);
        return response.data.data.user;
      },
      async logout() {
        try {
          await authClient.post('/logout');
        } finally {
          clearSession();
        }
      },
      async changePassword(currentPassword, newPassword) {
        const response = await httpClient.post<ApiResponse<TokenResponse>>('/auth/change-password', {
          currentPassword,
          newPassword,
        });
        applySession(response.data.data);
      },
      selectWorkingBranch(branchId) {
        if (!user) {
          return;
        }
        rememberWorkingBranch(user.id, branchId);
        sessionStore.setWorkingBranchId(branchId);
        setWorkingBranchId(branchId);
        queryClient.invalidateQueries();
      },
    }),
    [status, user, workingBranchId, applySession, clearSession, queryClient],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
