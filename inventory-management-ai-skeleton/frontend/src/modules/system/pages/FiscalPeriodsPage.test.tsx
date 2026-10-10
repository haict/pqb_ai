import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { authValue, testUser, WithAuth } from '../../../test/authTestUtils';
import type { FiscalPeriod, PeriodModule } from '../types';
import { FiscalPeriodsPage } from './FiscalPeriodsPage';

const api = vi.hoisted(() => ({
  list: vi.fn(),
  lock: vi.fn(),
  unlock: vi.fn(),
  generate: vi.fn(),
}));

vi.mock('../api', () => ({
  ALL: { page: 0, size: 200 },
  companyApi: {
    list: vi.fn().mockResolvedValue({
      items: [{ id: 1, code: 'B2K', name: 'Công ty mẫu' }],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    }),
  },
  fiscalPeriodApi: api,
}));

function period(id: number, month: number, lockedModules: PeriodModule[]): FiscalPeriod {
  const modules: PeriodModule[] = ['INVENTORY', 'DEBT', 'CASH', 'ACCOUNTING'];
  return {
    id,
    companyId: 1,
    year: 2026,
    month,
    startDate: `2026-${String(month).padStart(2, '0')}-01`,
    endDate: `2026-${String(month).padStart(2, '0')}-28`,
    locks: modules.map((module) => ({
      module,
      locked: lockedModules.includes(module),
      changedAt: null,
      changedBy: null,
      reason: null,
    })),
  };
}

function renderPage(permissions: string[]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <WithAuth value={authValue({ user: { ...testUser, permissions } })}>
        <FiscalPeriodsPage />
      </WithAuth>
    </QueryClientProvider>,
  );
}

// Bảng và modal Ant Design render chậm trên jsdom: nới thời gian chờ.
const WAIT = { timeout: 10000 };

describe('FiscalPeriodsPage', { timeout: 30000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.list.mockResolvedValue([period(11, 1, ['INVENTORY']), period(12, 2, [])]);
    api.lock.mockResolvedValue({ fiscalPeriodId: 11, module: 'DEBT', locked: true, branchIds: [1] });
  });

  it('hiện trạng thái khóa; có quyền khóa thì khóa được, không có quyền mở thì không thấy nút Mở', async () => {
    renderPage(['period:view', 'period:manage']);

    expect(await screen.findAllByText('Đã khóa', {}, WAIT)).toHaveLength(1);
    expect(screen.queryByRole('button', { name: /Mở$/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: /Khóa$/ })[0]!);
    fireEvent.click(await screen.findByRole('button', { name: /Khóa kỳ/ }, WAIT));

    await waitFor(
      () =>
        expect(api.lock).toHaveBeenCalledWith(
          11,
          expect.objectContaining({ module: 'DEBT', branchIds: [1] }),
          expect.any(String),
        ),
      WAIT,
    );
    expect(api.list).toHaveBeenCalledWith(1, expect.any(Number), 1);
  });

  it('BR-SYS-32: mở lại kỳ bắt buộc nhập lý do', async () => {
    renderPage(['period:view', 'period:unlock']);

    fireEvent.click(await screen.findByRole('button', { name: /Mở$/ }, WAIT));
    fireEvent.click(await screen.findByRole('button', { name: /Mở lại kỳ/ }, WAIT));

    expect(await screen.findByText('Nhập lý do mở lại kỳ', {}, WAIT)).toBeInTheDocument();
    expect(api.unlock).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /Khóa$/ })).not.toBeInTheDocument();
  });
});
