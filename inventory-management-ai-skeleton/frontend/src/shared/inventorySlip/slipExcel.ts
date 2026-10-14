import ExcelJS from 'exceljs';
import { moneyInWords } from '../moneyWords';
import {
  longDate,
  ORIGINAL_DOCUMENTS_LINE,
  productText,
  SIGN_DATE_LINE,
  SIGN_HINT,
  signerSpans,
  SLIP_COLUMN_KEYS,
  SLIP_COLUMNS,
  taxRatesText,
  type InventorySlipView,
} from './slipContent';

const COLUMN_COUNT = SLIP_COLUMNS.length;
const COLUMN_WIDTHS = [5, 12, 30, 12, 12, 9, 8, 11, 14, 16];
const FONT = 'Times New Roman';
const THIN = { style: 'thin' as const };
const BORDER = { top: THIN, left: THIN, bottom: THIN, right: THIN };
const QUANTITY_FORMAT = '#,##0.00';
const AMOUNT_FORMAT = '#,##0';
const MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** Phiếu nhập / xuất kho dạng .xlsx: cùng bố cục bản in; số lượng, tiền là ô số để người dùng tính tiếp. */
export async function slipToExcel(slip: InventorySlipView): Promise<Blob> {
  return new Blob([await slipToExcelBuffer(slip)], { type: MIME });
}

export async function slipToExcelBuffer(slip: InventorySlipView) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet(slip.title, {
    pageSetup: { paperSize: 9, orientation: 'portrait', fitToPage: true, fitToWidth: 1, fitToHeight: 0 },
  });
  sheet.columns = COLUMN_WIDTHS.map((width) => ({ width }));
  sheet.properties.defaultRowHeight = 18;

  let row = 1;
  const text = (
    value: string,
    options: { from?: number; to?: number; bold?: boolean; italic?: boolean } = {},
  ) => {
    const from = (options.from ?? 0) + 1;
    const to = (options.to ?? COLUMN_COUNT - 1) + 1;
    if (to > from) {
      sheet.mergeCells(row, from, row, to);
    }
    const cell = sheet.getCell(row, from);
    cell.value = value;
    cell.font = { name: FONT, size: 11, bold: options.bold, italic: options.italic };
    return cell;
  };
  const centered = (cell: ExcelJS.Cell) => {
    cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
  };

  text(slip.companyName ?? '', { bold: true });
  row += 1;
  text(slip.companyAddress ?? '');
  row += 2;
  const title = text(slip.title, { to: 7, bold: true });
  title.font = { name: FONT, size: 16, bold: true };
  centered(title);
  text(`Nợ: ${slip.debitAccounts.join(', ')}`, { from: 8 });
  row += 1;
  centered(text(longDate(slip.slipDate), { to: 7, bold: true, italic: true }));
  text(`Có: ${slip.creditAccounts.join(', ')}`, { from: 8 });
  row += 1;
  centered(text(`Số: ${slip.slipNo ?? ''}`, { to: 7 }));
  row += 2;
  slip.infoRows.forEach((info) => {
    text(`- ${info}`);
    row += 1;
  });

  const tableRow = (values: (string | number | null)[], bold = false) => {
    const excelRow = sheet.getRow(row);
    values.forEach((value, index) => {
      const cell = excelRow.getCell(index + 1);
      cell.value = value;
      cell.font = { name: FONT, size: 11, bold };
      cell.border = BORDER;
      cell.alignment = { vertical: 'top', wrapText: true };
    });
    row += 1;
    return excelRow;
  };
  [SLIP_COLUMNS, SLIP_COLUMN_KEYS].forEach((header) => {
    tableRow(header, true).eachCell((cell) => centered(cell));
  });
  slip.lines.forEach((line) => {
    const excelRow = tableRow([
      line.lineNo,
      line.productCode ?? '',
      productText(line),
      line.lots.join('\n'),
      line.expiries.join('\n'),
      line.warehouseCode ?? '',
      line.unitName,
      line.quantity,
      line.unitPrice,
      line.amount,
    ]);
    excelRow.getCell(1).alignment = { horizontal: 'center', vertical: 'top' };
    excelRow.getCell(8).numFmt = QUANTITY_FORMAT;
    excelRow.getCell(9).numFmt = QUANTITY_FORMAT;
    excelRow.getCell(10).numFmt = AMOUNT_FORMAT;
  });
  const totalRow = tableRow(['', '', 'Cộng', '', '', '', '', '', '', slip.totalAmount], true);
  centered(totalRow.getCell(3));
  totalRow.getCell(10).numFmt = AMOUNT_FORMAT;

  const summaryRow = (left: string, label: string, amount: number) => {
    const excelRow = tableRow([left, '', '', '', '', '', label, '', '', amount]);
    sheet.mergeCells(row - 1, 1, row - 1, 6);
    sheet.mergeCells(row - 1, 7, row - 1, 9);
    excelRow.getCell(10).numFmt = AMOUNT_FORMAT;
    excelRow.getCell(10).font = { name: FONT, size: 11, bold: true };
  };
  summaryRow('', 'Cộng tiền hàng:', slip.totalAmount);
  summaryRow(`Thuế suất GTGT: ${taxRatesText(slip)}`, 'Tiền thuế GTGT:', slip.taxAmount);
  summaryRow('', 'Tổng tiền thanh toán:', slip.paymentAmount);

  row += 1;
  text(`- Số tiền bằng chữ: ${moneyInWords(slip.paymentAmount)}`);
  row += 1;
  text(ORIGINAL_DOCUMENTS_LINE);
  row += 1;
  text(SIGN_DATE_LINE, { from: 6, italic: true }).alignment = { horizontal: 'right' };
  row += 1;
  const spans = signerSpans(slip.signers.length, COLUMN_COUNT);
  slip.signers.forEach((signer, index) => {
    const [start, span] = spans[index] ?? [0, 1];
    centered(text(signer, { from: start, to: start + span - 1, bold: true }));
  });
  row += 1;
  slip.signers.forEach((_, index) => {
    const [start, span] = spans[index] ?? [0, 1];
    centered(text(SIGN_HINT, { from: start, to: start + span - 1, italic: true }));
  });

  return workbook.xlsx.writeBuffer();
}
