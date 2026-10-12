import { useQuery } from '@tanstack/react-query';
import { useCompanyId } from '../masterdata/lookups';
import { cashAccountApi } from './api';

/** Quỹ, tài khoản ngân hàng đang hoạt động trong phạm vi để chọn trên chứng từ thu chi, sổ quỹ. */
export function useCashAccounts() {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: ['cash-accounts', 'lookup', companyId],
    queryFn: () => cashAccountApi.list({ page: 0, size: 200, companyId, status: 'ACTIVE' }),
    enabled: companyId > 0,
    staleTime: 30_000,
  });
  const items = query.data?.items ?? [];
  return {
    items,
    byId: (id: number | null | undefined) => items.find((item) => item.id === id),
    options: items.map((item) => ({ value: item.id, label: `${item.code} - ${item.name}` })),
  };
}
