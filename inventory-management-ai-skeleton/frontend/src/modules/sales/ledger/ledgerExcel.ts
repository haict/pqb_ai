import ExcelJS from 'exceljs';
import type { SalesLedger } from '../types';
import {
  COLUMN_COUNT,
  COST_NOTE,
  FORM_CODE,
  FORM_NOTE,
  HEADER_BOTTOM,
  HEADER_TOP,
  isNumberColumn,
  LEDGER_TITLE,
  ledgerRows,
  NUMBER_COLUMNS,
  OPEN_DATE_NOTE,
  PAGE_NOTE,
  periodText,
  SIGN_DATE,
  SIGN_HINT,
  SIGNERS,
} from './ledgerContent';

const COLUMN_WIDTHS = [11, 11, 16, 11, 11, 36, 9, 8, 10, 13, 15, 11, 13];
const FONT = 'Times New Roman';
const THIN = { style: 'thin' as const };
const BORDER = { top: THIN, left: THIN, bottom: THIN, right: THIN };
const DECIMAL_FORMAT = '#,##0.00';
const AMOUNT_FORMAT = '#,##0';
const MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const BOLD_KINDS = new Set(['sum', 'net', 'cost', 'profit', 'groupSum', 'total']);

/** Sổ chi tiết bán hàng dạng .xlsx: cùng bố cục bản in; số lượng, tiền là ô số để người dùng tính tiếp. */
export async function ledgerToExcel(ledger: SalesLedger): Promise<Blob> {
  return new Blob([await ledgerToExcelBuffer(ledger)], { type: MIME });
}

export async function ledgerToExcelBuffer(ledger: SalesLedger) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('S16-DNN', {
    pageSetup: { paperSize: 9, orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0 },
  });
  sheet.columns = COLUMN_WIDTHS.map((width) => ({ width }));

  let row = 1;
  const text = (
    value: string,
    options: {
      from?: number;
      to?: number;
      bold?: boolean;
      italic?: boolean;
      center?: boolean;
      size?: number;
    } = {},
  ) => {
    const from = (options.from ?? 0) + 1;
    const to = (options.to ?? COLUMN_COUNT - 1) + 1;
    if (to > from) {
      sheet.mergeCells(row, from, row, to);
    }
    const cell = sheet.getCell(row, from);
    cell.value = value;
    cell.font = { name: FONT, size: options.size ?? 11, bold: options.bold, italic: options.italic };
    cell.alignment = { horizontal: options.center ? 'center' : 'left', vertical: 'middle', wrapText: true };
    return cell;
  };

  text(ledger.companyName ?? '', { to: 7, bold: true });
  text(FORM_CODE, { from: 8, bold: true, center: true });
  row += 1;
  text(ledger.companyAddress ?? '', { to: 7 });
  text(FORM_NOTE, { from: 8, italic: true, center: true, size: 9 });
  sheet.getRow(row).height = 30;
  row += 2;
  text(LEDGER_TITLE, { bold: true, center: true, size: 14 });
  row += 1;
  text(periodText(ledger), { bold: true, italic: true, center: true });
  row += 2;

  const header = (column: number, label: string, toColumn: number, toRow: number) => {
    if (toColumn > column || toRow > row) {
      sheet.mergeCells(row, column, toRow, toColumn);
    }
    const cell = sheet.getCell(row, column);
    cell.value = label;
    cell.font = { name: FONT, size: 11, bold: true };
    cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
  };
  let column = 1;
  HEADER_TOP.forEach((item) => {
    header(column, item.label, column + item.span - 1, row + item.rows - 1);
    column += item.span;
  });
  const bottomColumns = HEADER_TOP.flatMap((item, index) => {
    const start = HEADER_TOP.slice(0, index).reduce((sum, previous) => sum + previous.span, 1);
    return item.rows === 1 ? Array.from({ length: item.span }, (_, offset) => start + offset) : [];
  });
  row += 1;
  bottomColumns.forEach((bottomColumn, index) =>
    header(bottomColumn, HEADER_BOTTOM[index] ?? '', bottomColumn, row),
  );
  for (let headerRow = row - 1; headerRow <= row; headerRow += 1) {
    for (let col = 1; col <= COLUMN_COUNT; col += 1) {
      sheet.getCell(headerRow, col).border = BORDER;
    }
  }
  row += 1;

  ledgerRows(ledger).forEach((ledgerRow) => {
    if (ledgerRow.title !== undefined) {
      const cell = text(ledgerRow.title, { bold: true });
      cell.border = BORDER;
      row += 1;
      return;
    }
    const excelRow = sheet.getRow(row);
    ledgerRow.cells.forEach((value, index) => {
      const cell = excelRow.getCell(index + 1);
      cell.value = value;
      cell.border = BORDER;
      cell.font = {
        name: FONT,
        size: 11,
        bold: BOLD_KINDS.has(ledgerRow.kind),
        italic: ledgerRow.line?.reversal ?? false,
      };
      cell.alignment = { vertical: 'top', wrapText: !isNumberColumn(index) };
      if (isNumberColumn(index)) {
        cell.numFmt =
          index === NUMBER_COLUMNS.quantity || index === NUMBER_COLUMNS.unitPrice
            ? DECIMAL_FORMAT
            : AMOUNT_FORMAT;
      }
    });
    row += 1;
  });

  if (!ledger.costAvailable) {
    text(COST_NOTE, { italic: true, size: 9 });
    row += 1;
  }
  row += 1;
  text(PAGE_NOTE);
  row += 1;
  text(OPEN_DATE_NOTE);
  row += 1;
  text(SIGN_DATE, { from: 8, italic: true, center: true });
  row += 1;
  const half = Math.floor(COLUMN_COUNT / SIGNERS.length);
  SIGNERS.forEach((signer, index) => {
    text(signer, {
      from: index * half,
      to: index === SIGNERS.length - 1 ? COLUMN_COUNT - 1 : (index + 1) * half - 1,
      bold: true,
      center: true,
    });
  });
  row += 1;
  SIGNERS.forEach((_, index) => {
    text(SIGN_HINT, {
      from: index * half,
      to: index === SIGNERS.length - 1 ? COLUMN_COUNT - 1 : (index + 1) * half - 1,
      italic: true,
      center: true,
    });
  });

  return workbook.xlsx.writeBuffer();
}
