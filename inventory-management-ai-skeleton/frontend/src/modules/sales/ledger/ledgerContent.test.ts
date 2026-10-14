import ExcelJS from 'exceljs';
import dayjs from 'dayjs';
import type { SalesLedger, SalesLedgerGroup, SalesLedgerProduct, SalesLedgerTotals } from '../types';
import {
  cellText,
  documentLink,
  ledgerFileName,
  ledgerRows,
  NUMBER_COLUMNS,
  type LedgerRow,
} from './ledgerContent';
import { periodRange } from '../../../shared/reportPeriod';
import { ledgerToExcelBuffer } from './ledgerExcel';
import { ledgerPdfDefinition } from './ledgerPdf';

const totals: SalesLedgerTotals = {
  quantity: 2,
  amount: 9523810,
  taxDeduction: 0,
  otherDeduction: 0,
  netRevenue: 9523810,
  costOfGoodsSold: null,
  grossProfit: null,
};

const product: SalesLedgerProduct = {
  productId: 7,
  productCode: 'ALT',
  productName: 'ALT (6x66ml)',
  totals,
  lines: [
    {
      postingDate: '2026-10-02',
      documentType: 'DBH',
      documentId: 11,
      documentNo: 'BH01193/24',
      documentDate: '2026-10-02',
      invoiceDate: '2026-10-02',
      invoiceNo: '00000410',
      description: 'Bán hàng Bệnh viện theo hóa đơn 00000410',
      offsetAccount: '131',
      unitName: 'Hộp',
      quantity: 2,
      unitPrice: 4761904.76,
      amount: 9523810,
      taxDeduction: null,
      otherDeduction: null,
      reversal: false,
    },
  ],
};

const group: SalesLedgerGroup = { key: null, code: null, name: null, totals, products: [product] };

const ledger: SalesLedger = {
  companyName: 'Công ty mẫu',
  companyAddress: 'Hà Nội',
  dateFrom: '2026-10-01',
  dateTo: '2026-10-09',
  groupBy: 'NONE',
  costAvailable: false,
  generatedAt: '2026-10-09T03:00:00Z',
  total: totals,
  groups: [group],
};

/** Ô dạng chữ của dòng thứ {@code index}; thiếu dòng thì lỗi rõ ràng. */
function text(rows: LedgerRow[], index: number, column: number) {
  const row = rows[index];
  if (!row) {
    throw new Error(`Thiếu dòng ${index}`);
  }
  return cellText(row.cells, column);
}

describe('sổ chi tiết bán hàng S16-DNN', () => {
  it('BR-RPT-26: khối hàng gồm tên sản phẩm, dòng phát sinh, cộng, doanh thu thuần, giá vốn, lãi gộp', () => {
    const rows = ledgerRows(ledger);
    expect(rows.map((row) => row.kind)).toEqual(['product', 'line', 'sum', 'net', 'cost', 'profit']);
    expect(rows[0]?.title).toBe('Tên sản phẩm: ALT - ALT (6x66ml)');
    expect(text(rows, 1, 2)).toBe('BH01193/24');
    expect(text(rows, 1, NUMBER_COLUMNS.unitPrice)).toBe('4.761.904,76');
    expect(text(rows, 1, NUMBER_COLUMNS.amount)).toBe('9.523.810');
    expect(text(rows, 2, NUMBER_COLUMNS.quantity)).toBe('2,00');
    expect(text(rows, 2, NUMBER_COLUMNS.other)).toBe('');
    expect(text(rows, 3, NUMBER_COLUMNS.amount)).toBe('9.523.810');
    expect(text(rows, 4, NUMBER_COLUMNS.amount)).toBe('');
    expect(text(rows, 5, NUMBER_COLUMNS.amount)).toBe('');
  });

  it('thống kê theo khách: tiêu đề nhóm và dòng cộng nhóm', () => {
    const grouped: SalesLedger = {
      ...ledger,
      groupBy: 'CUSTOMER',
      groups: [{ ...group, key: 3, code: 'KH01', name: 'Bệnh viện' }],
    };
    const rows = ledgerRows(grouped);
    expect(rows[0]).toMatchObject({ kind: 'group', title: 'Khách hàng: KH01 - Bệnh viện' });
    expect(rows[rows.length - 1]?.kind).toBe('groupSum');
  });

  it('kỳ báo cáo: đầu tháng đến hiện tại, quý này, tùy chọn giữ khoảng đang chọn', () => {
    const today = dayjs('2026-10-09');
    const format = (range: [dayjs.Dayjs, dayjs.Dayjs]) => range.map((day) => day.format('YYYY-MM-DD'));
    expect(format(periodRange('MONTH_TO_DATE', today, [today, today]))).toEqual(['2026-10-01', '2026-10-09']);
    expect(format(periodRange('THIS_QUARTER', today, [today, today]))).toEqual(['2026-10-01', '2026-12-31']);
    expect(format(periodRange('LAST_MONTH', today, [today, today]))).toEqual(['2026-09-01', '2026-09-30']);
    const custom: [dayjs.Dayjs, dayjs.Dayjs] = [dayjs('2026-01-05'), dayjs('2026-02-01')];
    expect(periodRange('CUSTOM', today, custom)).toBe(custom);
  });

  it('BR-RPT-11: số chứng từ mở đơn bán hoặc trả hàng / giảm giá', () => {
    expect(documentLink({ documentType: 'DBH', documentId: 11 })).toBe('/sales/orders?id=11');
    expect(documentLink({ documentType: 'TLB', documentId: 5 })).toBe('/sales/returns?id=5');
  });

  it('tên file theo kỳ, Excel giữ ô số, PDF khổ ngang', async () => {
    expect(ledgerFileName(ledger, 'xlsx')).toBe('So-chi-tiet-ban-hang_2026-10-01_2026-10-09.xlsx');
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(await ledgerToExcelBuffer(ledger));
    const sheet = workbook.getWorksheet('S16-DNN');
    const values: unknown[] = [];
    sheet?.eachRow((row) => row.eachCell((cell) => values.push(cell.value)));
    expect(values).toContain('SỔ CHI TIẾT BÁN HÀNG');
    expect(values).toContain('BH01193/24');
    expect(values).toContain(9523810);
    const definition = ledgerPdfDefinition(ledger);
    expect(definition.pageOrientation).toBe('landscape');
  });
});
