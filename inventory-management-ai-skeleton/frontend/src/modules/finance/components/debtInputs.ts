import { useQueries } from '@tanstack/react-query';
import { formatMoney } from '../../accounting/labels';
import { formatDate } from '../../inventory/labels';
import { useCompanyId } from '../../masterdata/lookups';
import { debtApi } from '../api';
import { DEBT_TYPES } from '../labels';
import type { DebtType } from '../types';

/** Ô nhập tiền: nhóm nghìn bằng dấu chấm; số liệu chính thức do server tính. */
export const moneyInputProps = {
  style: { width: '100%' },
  formatter: (value: number | undefined) => `${value ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.'),
  parser: (value: string | undefined) => Number((value ?? '').replace(/\./g, '')),
};

/** Hóa đơn còn nợ của đối tượng trong chi nhánh (hạn sớm nhất trước) để chọn trên dòng bù trừ / điều chỉnh. */
export function useOpenDebtItemOptions(debtTypes: DebtType[], partnerId?: number, branchId?: number) {
  const companyId = useCompanyId();
  const results = useQueries({
    queries: debtTypes.map((debtType) => ({
      queryKey: ['debt-items', 'open', companyId, debtType, partnerId, branchId],
      queryFn: () =>
        debtApi.items({
          page: 0,
          size: 200,
          sort: 'dueDate,asc',
          companyId,
          debtType,
          partnerId,
          branchId,
          openOnly: true,
        }),
      enabled: companyId > 0 && !!partnerId && !!branchId,
    })),
  });
  const items = results.flatMap((result) => result.data?.items ?? []);
  return {
    loading: results.some((result) => result.isFetching),
    options: items.map((item) => ({
      value: item.id,
      label:
        (debtTypes.length > 1 ? `[${DEBT_TYPES[item.debtType]}] ` : '') +
        `${item.invoiceNo ?? item.sourceNo} - hạn ${formatDate(item.dueDate)} - còn ${formatMoney(item.remainingAmount)}`,
    })),
  };
}
