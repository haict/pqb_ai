import type { Me } from './types';

/**
 * Trạng thái phiên ở mức module để client API (ngoài React) đọc được.
 * Access token chỉ nằm trong bộ nhớ, không ghi vào localStorage, URL hay console (frontend.md mục 6).
 */
type SessionEvent = 'expired' | 'passwordChangeRequired';
type Listener = (event: SessionEvent) => void;

let accessToken: string | null = null;
let workingBranchId: number | null = null;
const listeners = new Set<Listener>();

export const sessionStore = {
  getAccessToken: () => accessToken,
  setAccessToken(token: string | null) {
    accessToken = token;
  },
  getWorkingBranchId: () => workingBranchId,
  setWorkingBranchId(branchId: number | null) {
    workingBranchId = branchId;
  },
  clear() {
    accessToken = null;
    workingBranchId = null;
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  emit(event: SessionEvent) {
    listeners.forEach((listener) => listener(event));
  },
};

const WORKING_BRANCH_KEY = 'erp.workingBranch.';

/** Chi nhánh làm việc được nhớ theo người dùng trên máy này (chỉ là tiện ích, server vẫn kiểm tra). */
export function rememberedWorkingBranch(user: Me): number | null {
  const allowed = new Set(user.branches.map((branch) => branch.id));
  try {
    const stored = Number(localStorage.getItem(WORKING_BRANCH_KEY + user.id));
    if (allowed.has(stored)) {
      return stored;
    }
  } catch {
    // localStorage có thể bị chặn; dùng chi nhánh mặc định.
  }
  if (user.defaultBranchId !== null && allowed.has(user.defaultBranchId)) {
    return user.defaultBranchId;
  }
  return user.branches[0]?.id ?? null;
}

export function rememberWorkingBranch(userId: number, branchId: number) {
  try {
    localStorage.setItem(WORKING_BRANCH_KEY + userId, String(branchId));
  } catch {
    // Bỏ qua: chỉ mất tiện ích nhớ lựa chọn.
  }
}
