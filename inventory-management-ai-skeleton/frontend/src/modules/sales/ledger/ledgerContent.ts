import { formatDecimal } from '../../../shared/inventorySlip/slipContent';
import { displayDate } from '../../../shared/reportPeriod';
import { LEDGER_GROUPINGS } from '../labels';
import type { SalesLedger, SalesLedgerLine, SalesLedgerTotals } from '../types';

/**
 * Nội dung sổ chi tiết bán hàng (S16-DNN) dạng dòng phẳng, dùng chung cho màn hình, bản in, Excel và PDF để các
 * định dạng luôn khớp nhau.
 */

export const LEDGER_TITLE = 'SỔ CHI TIẾT BÁN HÀNG';
/**
 * Ký hiệu mẫu theo hiện trạng doanh nghiệp (H-04, [Chưa kiểm chứng]: chế độ kế toán áp dụng do kế toán trưởng xác
 * nhận trước UAT).
 */
export const FORM_CODE = 'Mẫu số: S16-DNN';
export const FORM_NOTE = '(Ban hành theo Thông tư số 133/2016/TT-BTC Ngày 26/08/2016 của Bộ Tài chính)';
export const SIGNERS = ['Người ghi sổ', 'Kế toán trưởng'];
export const SIGN_DATE = 'Ngày ..... tháng ..... năm .........';
export const SIGN_HINT = '(Ký, họ tên)';
export const PAGE_NOTE = '- Sổ này có ..... trang, đánh số từ trang số 01 đến trang .....';
export const OPEN_DATE_NOTE = '- Ngày mở sổ: ....................';
export const COST_NOTE = 'Giá vốn hàng bán và lãi gộp chưa có số liệu: giá vốn được tính từ Giai đoạn 8.';

/** Hai dòng tiêu đề bảng; {@code span}: số cột gộp ngang, {@code rows}: 2 khi gộp dọc. */
export const HEADER_TOP: { label: string; span: number; rows: number }[] = [
  { label: 'Ngày hạch toán', span: 1, rows: 2 },
  { label: 'Chứng từ', span: 2, rows: 1 },
  { label: 'Hóa đơn', span: 2, rows: 1 },
  { label: 'Diễn giải', span: 1, rows: 2 },
  { label: 'TK đối ứng', span: 1, rows: 2 },
  { label: 'ĐVT', span: 1, rows: 2 },
  { label: 'Doanh thu', span: 3, rows: 1 },
  { label: 'Các khoản tính trừ', span: 2, rows: 1 },
];
export const HEADER_BOTTOM = [
  'Ngày',
  'Số hiệu',
  'Ngày',
  'Số',
  'Số lượng',
  'Đơn giá',
  'Thành tiền',
  'Thuế',
  'Khác',
];
export const COLUMN_COUNT = 13;
/** Chỉ số cột (từ 0) của diễn giải, số lượng, đơn giá, thành tiền, giảm trừ. */
export const DESCRIPTION_COLUMN = 5;
export const NUMBER_COLUMNS = { quantity: 8, unitPrice: 9, amount: 10, tax: 11, other: 12 };
export const QUANTITY_DIGITS = 2;
export const PRICE_DIGITS = 2;
export const AMOUNT_DIGITS = 0;

export type LedgerRowKind =
  'group' | 'product' | 'line' | 'sum' | 'net' | 'cost' | 'profit' | 'groupSum' | 'total';

/** Một ô: chuỗi đã định dạng ngày, số để Excel giữ kiểu số, null là ô trống. */
export type LedgerCell = string | number | null;

export interface LedgerRow {
  kind: LedgerRowKind;
  /** Nhãn dòng tiêu đề (nhóm, sản phẩm) chiếm cả hàng. */
  title?: string;
  cells: LedgerCell[];
  /** Dòng phát sinh: chứng từ để mở chi tiết. */
  line?: SalesLedgerLine;
}

/** Dòng phẳng theo thứ tự in: (nhóm) → tên sản phẩm → dòng phát sinh → cộng, doanh thu thuần, giá vốn, lãi gộp. */
export function ledgerRows(ledger: SalesLedger): LedgerRow[] {
  const rows: LedgerRow[] = [];
  const grouped = ledger.groupBy !== 'NONE';
  let blockCount = 0;
  ledger.groups.forEach((group) => {
    if (grouped) {
      const label = [group.code, group.name].filter(Boolean).join(' - ') || '(trống)';
      rows.push({ kind: 'group', title: `${LEDGER_GROUPINGS[ledger.groupBy]}: ${label}`, cells: [] });
    }
    group.products.forEach((product) => {
      blockCount += 1;
      const name = [product.productCode, product.productName].filter(Boolean).join(' - ');
      rows.push({ kind: 'product', title: `Tên sản phẩm: ${name}`, cells: [] });
      product.lines.forEach((line) => rows.push({ kind: 'line', cells: lineCells(line), line }));
      rows.push(...totalRows(product.totals));
    });
    if (grouped) {
      rows.push({ kind: 'groupSum', cells: sumCells('Cộng nhóm', group.totals) });
    }
  });
  if (blockCount > 1) {
    rows.push({ kind: 'total', cells: sumCells('Tổng cộng', ledger.total) });
  }
  return rows;
}

function lineCells(line: SalesLedgerLine): LedgerCell[] {
  return [
    displayDate(line.postingDate),
    displayDate(line.documentDate),
    line.documentNo,
    displayDate(line.invoiceDate),
    line.invoiceNo ?? '',
    line.description,
    line.offsetAccount ?? '',
    line.unitName ?? '',
    line.quantity,
    line.unitPrice,
    line.amount,
    line.taxDeduction,
    line.otherDeduction,
  ];
}

function summaryCells(label: string, amount: number | null): LedgerCell[] {
  const cells: LedgerCell[] = Array(COLUMN_COUNT).fill(null);
  cells[DESCRIPTION_COLUMN] = label;
  cells[NUMBER_COLUMNS.amount] = amount;
  return cells;
}

/** Ô giảm trừ bằng 0 để trống như mẫu sổ. */
function blankZero(value: number) {
  return value === 0 ? null : value;
}

function sumCells(label: string, totals: SalesLedgerTotals): LedgerCell[] {
  const cells = summaryCells(label, totals.amount);
  cells[NUMBER_COLUMNS.quantity] = totals.quantity;
  cells[NUMBER_COLUMNS.tax] = blankZero(totals.taxDeduction);
  cells[NUMBER_COLUMNS.other] = blankZero(totals.otherDeduction);
  return cells;
}

function totalRows(totals: SalesLedgerTotals): LedgerRow[] {
  return [
    { kind: 'sum', cells: sumCells('Cộng số phát sinh', totals) },
    { kind: 'net', cells: summaryCells('- Doanh thu thuần', totals.netRevenue) },
    { kind: 'cost', cells: summaryCells('- Giá vốn hàng bán', totals.costOfGoodsSold) },
    { kind: 'profit', cells: summaryCells('- Lãi gộp', totals.grossProfit) },
  ];
}

/** Ô dạng chữ cho bản in, PDF: số theo định dạng Việt Nam, số lượng / đơn giá 2 chữ số, tiền làm tròn đồng (D-05). */
export function cellText(cells: LedgerCell[], index: number): string {
  const value = cells[index];
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  const digits =
    index === NUMBER_COLUMNS.quantity
      ? QUANTITY_DIGITS
      : index === NUMBER_COLUMNS.unitPrice
        ? PRICE_DIGITS
        : AMOUNT_DIGITS;
  return formatDecimal(value, digits);
}

export function isNumberColumn(index: number) {
  return index >= NUMBER_COLUMNS.quantity;
}

export function periodText(ledger: Pick<SalesLedger, 'dateFrom' | 'dateTo'>) {
  return `Từ ngày ${displayDate(ledger.dateFrom)} đến ngày ${displayDate(ledger.dateTo)}`;
}

/** "So-chi-tiet-ban-hang_2026-10-01_2026-10-09.xlsx". */
export function ledgerFileName(ledger: Pick<SalesLedger, 'dateFrom' | 'dateTo'>, extension: string) {
  return `So-chi-tiet-ban-hang_${ledger.dateFrom}_${ledger.dateTo}.${extension}`;
}

/** Đường dẫn mở chứng từ nguồn của dòng sổ (truy ngược số liệu, BR-RPT-11). */
export function documentLink(line: Pick<SalesLedgerLine, 'documentType' | 'documentId'>) {
  return line.documentType === 'DBH'
    ? `/sales/orders?id=${line.documentId}`
    : `/sales/returns?id=${line.documentId}`;
}
