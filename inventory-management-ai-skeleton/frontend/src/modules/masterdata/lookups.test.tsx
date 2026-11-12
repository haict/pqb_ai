import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { useCachedSuggestedWarehouse } from './lookups';

const wrapperFor = (client: QueryClient) =>
  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };

describe('useCachedSuggestedWarehouse (D-76)', () => {
  it('trả kho gợi ý đã tải xong để onProduct không xóa mất kho', () => {
    const client = new QueryClient();
    client.setQueryData(['warehouses', 'suggestion', 1, 7], [{ productId: 7, warehouseId: 9 }]);
    const { result } = renderHook(() => useCachedSuggestedWarehouse(), { wrapper: wrapperFor(client) });

    expect(result.current(1, 7)).toBe(9);
  });

  it('chưa tải, không có gợi ý hoặc thiếu chi nhánh thì trống', () => {
    const client = new QueryClient();
    client.setQueryData(['warehouses', 'suggestion', 1, 8], [{ productId: 8, warehouseId: null }]);
    const { result } = renderHook(() => useCachedSuggestedWarehouse(), { wrapper: wrapperFor(client) });

    expect(result.current(1, 7)).toBeUndefined();
    expect(result.current(1, 8)).toBeUndefined();
    expect(result.current(undefined, 7)).toBeUndefined();
  });
});
