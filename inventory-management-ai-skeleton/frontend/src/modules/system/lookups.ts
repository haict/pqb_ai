import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../shared/auth/authContext';
import { BRANCH_VISIBLE } from '../../shared/branch';
import { ALL, companyApi, documentTypeApi, roleApi } from './api';

/** Công ty trong phạm vi người dùng, dùng cho ô chọn và hiển thị tên. */
export function useCompanies() {
  const query = useQuery({ queryKey: ['companies', 'lookup'], queryFn: () => companyApi.list(ALL) });
  const items = query.data?.items ?? [];
  return {
    items,
    options: items.map((company) => ({ value: company.id, label: `${company.code} - ${company.name}` })),
    nameOf: (id: number | null | undefined) => items.find((company) => company.id === id)?.name ?? '',
  };
}

/** Chi nhánh đang hoạt động trong phạm vi người dùng (lấy từ /auth/me). */
export function useMyBranches() {
  const { user, workingBranchId } = useAuth();
  const items = user?.branches ?? [];
  return {
    items,
    /** Chi nhánh ngầm định khi lập chứng từ: chi nhánh làm việc, nếu chưa có thì chi nhánh đầu tiên. */
    defaultId: workingBranchId ?? items[0]?.id,
    /** Chi nhánh lọc sẵn trên báo cáo; khi ẩn chi nhánh thì để trống (toàn công ty). */
    filterDefaultId: BRANCH_VISIBLE ? items[0]?.id : undefined,
    options: items.map((branch) => ({ value: branch.id, label: `${branch.code} - ${branch.name}` })),
    nameOf: (id: number | null | undefined) => {
      const branch = items.find((item) => item.id === id);
      return branch ? `${branch.code} - ${branch.name}` : id ? `#${id}` : '';
    },
  };
}

export function useActiveRoles() {
  const query = useQuery({
    queryKey: ['roles', 'lookup'],
    queryFn: () => roleApi.list({ ...ALL, status: 'ACTIVE' }),
  });
  return query.data?.items ?? [];
}

/** Danh mục loại chứng từ (dùng chung mọi người dùng đã đăng nhập). */
export function useDocumentTypes() {
  const query = useQuery({
    queryKey: ['document-types'],
    queryFn: documentTypeApi.list,
    staleTime: 5 * 60_000,
  });
  const items = query.data ?? [];
  return {
    items,
    options: items.map((type) => ({ value: type.code, label: `${type.code} - ${type.name}` })),
    nameOf: (code: string | null | undefined) => items.find((type) => type.code === code)?.name ?? code ?? '',
  };
}
