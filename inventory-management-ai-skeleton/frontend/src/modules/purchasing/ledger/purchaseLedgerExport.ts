import ExcelJS from 'exceljs';
import type { TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import { pdfBlob } from '../../../shared/pdfmakeLoader';
import type { PurchaseLedger } from '../types';
import {
  cellText,
  isDecimalColumn,
  isNumberColumn,
  lineCells,
  PURCHASE_COLUMNS,
  PURCHASE_LEDGER_TITLE,
  subtitle,
  totalCells,
} from './purchaseLedgerContent';

const FONT = 'Times New Roman';
const THIN = { style: 'thin' as const };
const BORDER = { top: THIN, left: THIN, bottom: THIN, right: THIN };
const EXCEL_WIDTHS = [11, 11, 16, 11, 12, 14, 34, 8, 10, 13, 15, 12, 10, 13, 13];
const PDF_WIDTHS = [40, 40, 52, 40, 42, 48, '*', 28, 36, 48, 54, 44, 36, 50, 50];
const MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** Sổ chi tiết mua hàng dạng .xlsx: ô số giữ kiểu số để người dùng lọc, tính tiếp. */
export async function purchaseLedgerToExcel(ledger: PurchaseLedger): Promise<Blob> {
  return new Blob([await purchaseLedgerExcelBuffer(ledger)], { type: MIME });
}

export async function purchaseLedgerExcelBuffer(ledger: PurchaseLedger) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('So chi tiet mua hang', {
    pageSetup: { paperSize: 9, orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0 },
  });
  sheet.columns = EXCEL_WIDTHS.map((width) => ({ width }));
  const count = PURCHASE_COLUMNS.length;
  const title = (
    row: number,
    value: string,
    options: { bold?: boolean; italic?: boolean; size?: number },
  ) => {
    sheet.mergeCells(row, 1, row, count);
    const cell = sheet.getCell(row, 1);
    cell.value = value;
    cell.font = { name: FONT, size: options.size ?? 11, bold: options.bold, italic: options.italic };
    cell.alignment = { horizontal: 'center' };
  };
  sheet.getCell(1, 1).value = ledger.companyName ?? '';
  sheet.getCell(1, 1).font = { name: FONT, size: 11, bold: true };
  sheet.getCell(2, 1).value = ledger.companyAddress ?? '';
  title(4, PURCHASE_LEDGER_TITLE, { bold: true, size: 14 });
  title(5, subtitle(ledger), { bold: true, italic: true });

  const writeRow = (rowNumber: number, values: (string | number | null)[], bold: boolean, italic = false) => {
    const row = sheet.getRow(rowNumber);
    values.forEach((value, index) => {
      const cell = row.getCell(index + 1);
      cell.value = value;
      cell.border = BORDER;
      cell.font = { name: FONT, size: 11, bold, italic };
      cell.alignment =
        bold && !isNumberColumn(index) && value !== null
          ? { horizontal: 'center', vertical: 'middle', wrapText: true }
          : { vertical: 'top', wrapText: !isNumberColumn(index) };
      if (isNumberColumn(index) && typeof value === 'number') {
        cell.numFmt = isDecimalColumn(index) ? '#,##0.00' : '#,##0';
      }
    });
  };
  let rowNumber = 7;
  writeRow(rowNumber, PURCHASE_COLUMNS, true);
  ledger.lines.forEach((line) => {
    rowNumber += 1;
    writeRow(rowNumber, lineCells(line), false, line.reversal);
  });
  writeRow(rowNumber + 1, totalCells(ledger), true);
  return workbook.xlsx.writeBuffer();
}

/** Định nghĩa pdfmake (A4 ngang); tách riêng để kiểm thử không cần trình duyệt. */
export function purchaseLedgerPdfDefinition(ledger: PurchaseLedger): TDocumentDefinitions {
  const row = (cells: (string | number | null)[], bold: boolean, italics = false): TableCell[] =>
    PURCHASE_COLUMNS.map((_, index) => ({
      text: cellText(cells, index),
      alignment: isNumberColumn(index) ? 'right' : 'left',
      bold,
      italics,
    }));
  return {
    pageSize: 'A4',
    pageOrientation: 'landscape',
    pageMargins: [20, 24, 20, 30],
    info: { title: `${PURCHASE_LEDGER_TITLE} ${subtitle(ledger)}` },
    defaultStyle: { fontSize: 9, lineHeight: 1.15 },
    footer: (current, total) => ({ text: `Trang ${current}/${total}`, alignment: 'center', fontSize: 8 }),
    content: [
      { text: ledger.companyName ?? '', bold: true },
      ledger.companyAddress ?? '',
      { text: PURCHASE_LEDGER_TITLE, fontSize: 14, bold: true, alignment: 'center', margin: [0, 10, 0, 0] },
      { text: subtitle(ledger), bold: true, italics: true, alignment: 'center' },
      {
        margin: [0, 6, 0, 0],
        fontSize: 7,
        table: {
          headerRows: 1,
          widths: PDF_WIDTHS,
          body: [
            PURCHASE_COLUMNS.map((label): TableCell => ({ text: label, bold: true, alignment: 'center' })),
            ...ledger.lines.map((line) => row(lineCells(line), false, line.reversal)),
            row(totalCells(ledger), true),
          ],
        },
      },
    ],
  };
}

export async function purchaseLedgerToPdf(ledger: PurchaseLedger): Promise<Blob> {
  return pdfBlob(purchaseLedgerPdfDefinition(ledger));
}
