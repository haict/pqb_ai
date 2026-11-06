import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { SearchAutoComplete } from './SearchAutoComplete';

interface Item {
  code: string;
  name: string;
}

function renderBox(onSearch = vi.fn()) {
  const fetch = vi.fn().mockResolvedValue({
    items: [{ code: 'M-30D', name: 'Diluent' }],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SearchAutoComplete<Item>
        queryKey="items"
        fetch={fetch}
        toOption={(item) => ({ value: item.code, label: `${item.code} - ${item.name}` })}
        onSearch={onSearch}
        placeholder="Tìm mã"
      />
    </QueryClientProvider>,
  );
  return { fetch, onSearch };
}

describe('SearchAutoComplete', () => {
  it('gợi ý từ server khi gõ; chọn gợi ý thì lọc theo mã', async () => {
    const { fetch, onSearch } = renderBox();
    fireEvent.change(screen.getByPlaceholderText('Tìm mã'), { target: { value: 'M-30' } });

    fireEvent.click(await screen.findByText('M-30D - Diluent', {}, { timeout: 2000 }));
    expect(fetch).toHaveBeenCalledWith('M-30');
    await waitFor(() => expect(onSearch).toHaveBeenCalledWith('M-30D'));
  });

  it('chưa gõ thì không gọi server; Enter tìm theo nội dung đã gõ', () => {
    const { fetch, onSearch } = renderBox();
    expect(fetch).not.toHaveBeenCalled();
    const input = screen.getByPlaceholderText('Tìm mã');
    fireEvent.change(input, { target: { value: ' ABC ' } });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
    expect(onSearch).toHaveBeenCalledWith('ABC');
  });
});
