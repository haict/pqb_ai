import { reportCellText } from '../../shared/report/tableReport';
import { cashForecastReport, columnLabel, forecastItemsReport, forecastRows } from './cashForecast';
import type { CashForecast, CashForecastItems } from './types';

const generatedAt = '2026-10-14T03:00:00Z';

const forecast: CashForecast = {
  granularity: 'MONTH',
  today: '2026-10-14',
  cashBalanceIncluded: true,
  openingBalance: 1000000,
  cashAccounts: [{ id: 1, code: 'TM_HN', name: 'Quỹ tiền mặt HN', accountType: 'CASH', balance: 1000000 }],
  columns: [
    { key: 'OVERDUE', from: null, to: '2026-10-13' },
    { key: 'P1', from: '2026-10-14', to: '2026-10-31' },
    { key: 'P2', from: '2026-11-01', to: '2026-11-30' },
    { key: 'LATER', from: '2026-12-01', to: null },
  ],
  lines: [
    { source: 'RECEIVABLE', direction: 'IN', amounts: [100000, 300000, 0, 0], total: 400000 },
    { source: 'SALES_ORDER', direction: 'IN', amounts: [0, 0, 200000, 0], total: 200000 },
    { source: 'PAYABLE', direction: 'OUT', amounts: [50000, 0, 0, 0], total: 50000 },
    { source: 'PURCHASE_ORDER', direction: 'OUT', amounts: [0, 0, 0, 700000], total: 700000 },
  ],
  inflow: [100000, 300000, 200000, 0],
  outflow: [50000, 0, 0, 700000],
  net: [50000, 300000, 200000, -700000],
  closing: [1050000, 1350000, 1550000, 850000],
  generatedAt,
};

describe('dòng tiền dự kiến đợt 10D', () => {
  it('BR-RPT-30: nhãn cột theo kỳ; quá hạn, sau kỳ cuối', () => {
    expect(forecast.columns.map((column) => columnLabel('MONTH', column))).toEqual([
      'Quá hạn',
      '14/10–31/10',
      '11/2026',
      'Từ 01/12/2026',
    ]);
    expect(columnLabel('DAY', { key: 'P1', from: '2026-10-14', to: '2026-10-14' })).toBe('14/10');
  });

  it('BR-CSH-06: số dư đầu kỳ nối tiếp số dư cuối kỳ trước; nguồn bấm được, dòng tổng không', () => {
    const rows = forecastRows(forecast);
    expect(rows.map((row) => row.key)).toEqual([
      'opening',
      'RECEIVABLE',
      'SALES_ORDER',
      'inflow',
      'PAYABLE',
      'PURCHASE_ORDER',
      'outflow',
      'net',
      'closing',
    ]);
    expect(rows[0]?.values).toEqual([1000000, 1050000, 1350000, 1550000]);
    expect(rows[1]?.source).toBe('RECEIVABLE');
    expect(rows[3]?.source).toBeUndefined();
    expect(rows[8]?.total).toBe(850000);

    const report = cashForecastReport(forecast);
    expect(report.columns).toHaveLength(6);
    const closing = report.rows[8];
    expect(closing && reportCellText(report, closing.cells, 5)).toBe('850.000');
    expect(report.notes?.[0]).toContain('TM_HN - Quỹ tiền mặt HN: 1.000.000');
    expect(cashForecastReport({ ...forecast, cashBalanceIncluded: false }).notes?.[0]).toContain(
      'Không gồm số dư',
    );
  });

  it('BR-RPT-11: khoản cấu thành mở đơn bán / mua, công nợ đầu kỳ chỉ hiện số', () => {
    const items: CashForecastItems = {
      source: null,
      dateFrom: null,
      dateTo: '2026-10-13',
      today: '2026-10-14',
      rows: [
        {
          source: 'RECEIVABLE',
          documentType: 'DBH',
          documentId: 9,
          documentNo: 'DBH-HN-2026-000009',
          partnerCode: 'KH01',
          partnerName: 'Bệnh viện A',
          expectedDate: '2026-10-04',
          overdueDays: 10,
          amount: 100000,
        },
        {
          source: 'PAYABLE',
          documentType: 'CNDK',
          documentId: 3,
          documentNo: 'CNDK-0003',
          partnerCode: 'NCC01',
          partnerName: 'Nhà phân phối',
          expectedDate: '2026-10-10',
          overdueDays: 4,
          amount: 50000,
        },
      ],
      total: 150000,
      generatedAt,
    };
    const report = forecastItemsReport(items, 'Quá hạn');
    expect(report.rows[0]?.link?.to).toBe('/sales/orders?id=9');
    expect(report.rows[0]?.bold).toBe(true);
    expect(report.rows[1]?.link).toBeUndefined();
    expect(report.rows[2]?.cells[3]).toBe('Cộng: 2 khoản');
  });
});
