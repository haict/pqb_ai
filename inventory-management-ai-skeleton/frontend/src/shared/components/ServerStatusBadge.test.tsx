import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ServerStatusBadge } from './ServerStatusBadge';
import { fetchSystemHealth } from '../api/systemHealth';
import { ApiError, NETWORK_ERROR } from '../api/apiError';

vi.mock('../api/systemHealth', () => ({ fetchSystemHealth: vi.fn() }));

const mockedFetch = vi.mocked(fetchSystemHealth);

function renderBadge() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ServerStatusBadge />
    </QueryClientProvider>,
  );
}

describe('ServerStatusBadge', () => {
  afterEach(() => mockedFetch.mockReset());

  it('hiển thị máy chủ hoạt động khi health trả UP', async () => {
    mockedFetch.mockResolvedValue({ status: 'UP' });
    renderBadge();
    expect(await screen.findByText('Máy chủ hoạt động')).toBeInTheDocument();
  });

  it('hiển thị sự cố khi health trả DOWN', async () => {
    mockedFetch.mockResolvedValue({ status: 'DOWN' });
    renderBadge();
    expect(await screen.findByText('Máy chủ đang gặp sự cố')).toBeInTheDocument();
  });

  it('hiển thị mất kết nối khi không gọi được máy chủ', async () => {
    mockedFetch.mockRejectedValue(new ApiError('Không kết nối được máy chủ.', NETWORK_ERROR, null));
    renderBadge();
    expect(await screen.findByText('Mất kết nối máy chủ')).toBeInTheDocument();
  });
});
