import { formatDecimal } from '../../../shared/inventorySlip/slipContent';
import { displayDate } from '../../../shared/reportPeriod';
import type { PurchaseLedger, PurchaseLedgerLine } from '../types';

/**
 * Nội dung sổ chi tiết mua hàng (hiện trạng docs/HienTrang 15) dạng dòng phẳng, dùng chung cho màn hình, bản in,
 * Excel và PDF để các định dạng luôn khớp nhau. Cột "Tiền thuế NK", "Mã thống kê" chưa có nguồn dữ liệu nên không
 * hiển thị.
 */

export const PURCHASE_LEDGER_TITLE = 'SỔ CHI TIẾT MUA HÀNG';
export const PURCHASE_COLUMNS = [
  'Ngày hạch toán',
  'Ngày chứng từ',
  'Số chứng từ',
  'Ngày hóa đơn',
  'Số hóa đơn',
  'Mã hàng',
  'Tên hàng',
  'ĐVT',
  'Số lượng mua',
  'Đơn giá',
  'Giá trị mua',
  'Chiết khấu',
  'Số lượng trả lại',
  'Giá trị trả lại',
  'Giá trị giảm giá',
];
export const DOCUMENT_COLUMN = 2;
export const FIRST_NUMBER_COLUMN = 8;
/** Cột số lượng, đơn giá hiển thị 2 chữ số thập phân; cột tiền làm tròn đồng (D-05). */
const DECIMAL_COLUMNS = new Set([8, 9, 12]);

export type PurchaseCell = string | number | null;

export function lineCells(line: PurchaseLedgerLine): PurchaseCell[] {
  return [
    displayDate(line.postingDate),
    displayDate(line.documentDate),
    line.documentNo,
    displayDate(line.invoiceDate),
    line.invoiceNo ?? '',
    line.productCode ?? '',
    line.productName ?? '',
    line.unitName ?? '',
    line.purchaseQuantity,
    line.unitPrice,
    line.purchaseAmount,
    line.discountAmount,
    line.returnQuantity,
    line.returnAmount,
    line.priceReductionAmount,
  ];
}

/** Dòng tổng cuối sổ: "Số dòng = n" và tổng từng cột số (đơn giá để trống). */
export function totalCells(ledger: PurchaseLedger): PurchaseCell[] {
  const total = ledger.total;
  return [
    `Số dòng = ${total.lineCount}`,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    total.purchaseQuantity,
    null,
    total.purchaseAmount,
    total.discountAmount,
    total.returnQuantity,
    total.returnAmount,
    total.priceReductionAmount,
  ];
}

export function isDecimalColumn(index: number) {
  return DECIMAL_COLUMNS.has(index);
}

export function isNumberColumn(index: number) {
  return index >= FIRST_NUMBER_COLUMN;
}

/** Ô dạng chữ cho bản in, PDF theo định dạng số Việt Nam. */
export function cellText(cells: PurchaseCell[], index: number): string {
  const value = cells[index];
  if (value === null || value === undefined) {
    return '';
  }
  return typeof value === 'string' ? value : formatDecimal(value, isDecimalColumn(index) ? 2 : 0);
}

/** "Mặt hàng: AST (...); Từ ngày 01/01/2026 đến ngày 09/10/2026" khi sổ chỉ có một mặt hàng như mẫu hiện trạng. */
export function subtitle(ledger: PurchaseLedger) {
  const period = `Từ ngày ${displayDate(ledger.dateFrom)} đến ngày ${displayDate(ledger.dateTo)}`;
  const products = new Set(ledger.lines.map((line) => line.productId));
  const first = ledger.lines[0];
  return products.size === 1 && first
    ? `Mặt hàng: ${first.productName ?? first.productCode ?? ''}; ${period}`
    : period;
}

/** "So-chi-tiet-mua-hang_2026-10-01_2026-10-09.xlsx". */
export function purchaseLedgerFileName(
  ledger: Pick<PurchaseLedger, 'dateFrom' | 'dateTo'>,
  extension: string,
) {
  return `So-chi-tiet-mua-hang_${ledger.dateFrom}_${ledger.dateTo}.${extension}`;
}

/** Đường dẫn mở chứng từ nguồn của dòng sổ (truy ngược số liệu, BR-RPT-11). */
export function purchaseDocumentLink(line: Pick<PurchaseLedgerLine, 'documentType' | 'documentId'>) {
  return line.documentType === 'DMH'
    ? `/purchasing/orders?id=${line.documentId}`
    : `/purchasing/returns?id=${line.documentId}`;
}
