import { useQuery } from '@tanstack/react-query';
import { useCompanyId } from '../masterdata/lookups';
import { accountApi } from './api';

/** Tài khoản chi tiết đang hoạt động để chọn trong quy tắc hạch toán / lọc bút toán. */
export function usePostableAccounts() {
  const companyId = useCompanyId();
  const query = useQuery({
    queryKey: ['accounts', 'tree', companyId],
    queryFn: () => accountApi.tree(companyId),
    enabled: companyId > 0,
    staleTime: 60_000,
  });
  const items = (query.data ?? []).filter((account) => account.postable && account.status === 'ACTIVE');
  return {
    items,
    options: items.map((account) => ({ value: account.id, label: `${account.code} - ${account.name}` })),
  };
}
