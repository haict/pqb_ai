import dayjs from 'dayjs';
import { formatDateTime } from '../../shared/format';
import { formatDecimal } from '../../shared/inventorySlip/slipContent';
import { displayDate } from '../../shared/reportPeriod';
import { asciiFileName, type ReportRow, type TableReport } from '../../shared/report/tableReport';
import { debtLink } from './dashboard';
import type { CashForecast, CashForecastItems, ForecastGranularity, ForecastSource } from './types';

/** Dòng tiền dự kiến (BR-RPT-09, 30, BR-CSH-06): bảng theo kỳ và danh sách khoản cấu thành một ô (BR-RPT-11). */

const MONEY = 0;

export const GRANULARITIES: Record<ForecastGranularity, string> = {
  DAY: 'Ngày',
  WEEK: 'Tuần',
  MONTH: 'Tháng',
};

export const SOURCE_LABELS: Record<ForecastSource, string> = {
  RECEIVABLE: 'Thu: công nợ phải thu',
  SALES_ORDER: 'Thu: đơn bán đã duyệt chưa ghi sổ',
  PAYABLE: 'Chi: công nợ phải trả',
  PURCHASE_ORDER: 'Chi: đơn mua đã duyệt chưa ghi sổ',
};

type ForecastColumn = CashForecast['columns'][number];

export function columnLabel(granularity: ForecastGranularity, column: ForecastColumn): string {
  if (column.key === 'OVERDUE') {
    return 'Quá hạn';
  }
  if (column.key === 'LATER') {
    return `Từ ${displayDate(column.from)}`;
  }
  const from = dayjs(column.from);
  const to = dayjs(column.to);
  if (granularity === 'DAY') {
    return from.format('DD/MM');
  }
  if (granularity === 'MONTH' && from.date() === 1) {
    return from.format('MM/YYYY');
  }
  return `${from.format('DD/MM')}–${to.format('DD/MM')}`;
}

/** Một dòng của bảng dòng tiền; {@code source}: dòng bấm được để xem khoản cấu thành. */
export interface ForecastRow {
  key: string;
  label: string;
  values: number[];
  total: number | null;
  bold?: boolean;
  source?: ForecastSource;
}

function sum(values: number[]) {
  return values.reduce((total, value) => total + value, 0);
}

export function forecastRows(forecast: CashForecast): ForecastRow[] {
  const openings = [forecast.openingBalance, ...forecast.closing.slice(0, -1)];
  const line = (source: ForecastSource): ForecastRow => {
    const found = forecast.lines.find((item) => item.source === source);
    return {
      key: source,
      label: SOURCE_LABELS[source],
      values: found?.amounts ?? forecast.columns.map(() => 0),
      total: found?.total ?? 0,
      source,
    };
  };
  return [
    { key: 'opening', label: 'Số dư quỹ, ngân hàng đầu kỳ', values: openings, total: null, bold: true },
    line('RECEIVABLE'),
    line('SALES_ORDER'),
    { key: 'inflow', label: 'Tổng dự thu', values: forecast.inflow, total: sum(forecast.inflow), bold: true },
    line('PAYABLE'),
    line('PURCHASE_ORDER'),
    {
      key: 'outflow',
      label: 'Tổng dự chi',
      values: forecast.outflow,
      total: sum(forecast.outflow),
      bold: true,
    },
    { key: 'net', label: 'Chênh lệch thu − chi', values: forecast.net, total: sum(forecast.net) },
    {
      key: 'closing',
      label: 'Số dư cuối kỳ dự kiến',
      values: forecast.closing,
      total: forecast.closing[forecast.closing.length - 1] ?? null,
      bold: true,
    },
  ];
}

function cashNotes(forecast: CashForecast) {
  if (!forecast.cashBalanceIncluded) {
    return ['Không gồm số dư quỹ, ngân hàng (người xem chỉ được xem dữ liệu của mình).'];
  }
  return [
    `Số dư đầu kỳ: ${forecast.cashAccounts
      .map((account) => `${account.code} - ${account.name}: ${formatDecimal(account.balance, MONEY)}`)
      .join('; ')}.`,
  ];
}

export function cashForecastReport(forecast: CashForecast): TableReport {
  const title = 'DÒNG TIỀN DỰ KIẾN';
  return {
    title,
    subtitle: `Từ ngày ${displayDate(forecast.today)}, theo ${GRANULARITIES[forecast.granularity].toLowerCase()}`,
    columns: [
      { title: 'Chỉ tiêu', width: 32 },
      ...forecast.columns.map((column) => ({
        title: columnLabel(forecast.granularity, column),
        digits: MONEY,
        width: 14,
      })),
      { title: 'Cộng', digits: MONEY, width: 15 },
    ],
    rows: forecastRows(forecast).map((row): ReportRow => ({
      cells: [row.label, ...row.values, row.total],
      bold: row.bold,
    })),
    notes: [
      ...cashNotes(forecast),
      'Ngày dự kiến: hạn thanh toán của công nợ; đơn chưa ghi sổ: hạn trên đơn hoặc ngày dự kiến giao / nhận cộng số ngày được nợ. Quá hạn: ngày dự kiến trước hôm nay. Đơn mua quy đổi VND.',
      `Số liệu lấy lúc ${formatDateTime(forecast.generatedAt)}.`,
    ],
    fileName: asciiFileName(`${title}-${forecast.today}`),
  };
}

/** {@code label}: dòng và cột đang xem. */
export function forecastItemsReport(items: CashForecastItems, label: string): TableReport {
  const title = 'KHOẢN DỰ KIẾN THU / CHI';
  return {
    title,
    subtitle: label,
    columns: [
      { title: 'Ngày dự kiến', width: 11 },
      { title: 'Nguồn', width: 28 },
      { title: 'Số chứng từ', width: 20 },
      { title: 'Đối tượng', width: 34 },
      { title: 'Quá hạn (ngày)', digits: 0, width: 9 },
      { title: 'Số tiền', digits: MONEY, width: 15 },
    ],
    rows: [
      ...items.rows.map((row): ReportRow => {
        const to = debtLink(row.documentType, row.documentId);
        return {
          cells: [
            displayDate(row.expectedDate),
            SOURCE_LABELS[row.source],
            row.documentNo,
            `${row.partnerCode} - ${row.partnerName}`,
            row.overdueDays > 0 ? row.overdueDays : null,
            row.amount,
          ],
          bold: row.overdueDays > 0,
          link: to ? { column: 2, to } : undefined,
        };
      }),
      { cells: ['', '', '', `Cộng: ${items.rows.length} khoản`, null, items.total], bold: true },
    ],
    notes: [`Số liệu lấy lúc ${formatDateTime(items.generatedAt)}.`],
    fileName: asciiFileName(`${title}-${items.today}`),
  };
}
