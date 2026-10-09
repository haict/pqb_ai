import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { TableProps } from 'antd';
import type { SorterResult } from 'antd/es/table/interface';
import type { PageResponse } from './types';

export interface ListParams {
  page: number;
  size: number;
  sort?: string;
}

/**
 * Bảng danh sách phân trang, sắp xếp ở server (BR-UX-06, frontend.md mục 3).
 * Bộ lọc thay đổi thì quay về trang đầu.
 */
export function useServerTable<T, F extends object>(
  queryKey: string,
  fetcher: (params: ListParams & F) => Promise<PageResponse<T>>,
  filters: F,
) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<string | undefined>();
  const [lastFilters, setLastFilters] = useState(JSON.stringify(filters));

  const filterKey = JSON.stringify(filters);
  if (filterKey !== lastFilters) {
    setLastFilters(filterKey);
    setPage(0);
  }

  const query = useQuery({
    queryKey: [queryKey, { page, size, sort, ...filters }],
    queryFn: () => fetcher({ page, size, sort, ...filters }),
    placeholderData: keepPreviousData,
  });

  const tableProps: TableProps<T> = {
    rowKey: 'id',
    loading: query.isFetching,
    dataSource: query.data?.items ?? [],
    pagination: {
      current: page + 1,
      pageSize: size,
      total: query.data?.totalElements ?? 0,
      showSizeChanger: true,
      pageSizeOptions: [20, 50, 100],
      showTotal: (total) => `${total} bản ghi`,
    },
    onChange: (pagination, _filters, sorter) => {
      setPage((pagination.current ?? 1) - 1);
      setSize(pagination.pageSize ?? 20);
      const single = (Array.isArray(sorter) ? sorter[0] : sorter) as SorterResult<T> | undefined;
      setSort(
        single?.order && single.field
          ? `${String(single.field)},${single.order === 'ascend' ? 'asc' : 'desc'}`
          : undefined,
      );
    },
  };

  return { query, tableProps };
}
