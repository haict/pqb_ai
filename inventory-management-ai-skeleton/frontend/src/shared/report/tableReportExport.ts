import ExcelJS from 'exceljs';
import type { TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import { pdfBlob } from '../pdfmakeLoader';
import { isNumberColumn, reportCellText, type TableReport } from './tableReport';

const FONT = 'Times New Roman';
const THIN = { style: 'thin' as const };
const BORDER = { top: THIN, left: THIN, bottom: THIN, right: THIN };
const MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** Báo cáo bảng dạng .xlsx (A4 ngang, vừa một trang ngang); ô số giữ kiểu số. */
export async function tableReportToExcel(report: TableReport): Promise<Blob> {
  return new Blob([await tableReportExcelBuffer(report)], { type: MIME });
}

export async function tableReportExcelBuffer(report: TableReport) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet(report.title.slice(0, 31), {
    pageSetup: { paperSize: 9, orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0 },
  });
  const count = report.columns.length;
  sheet.columns = report.columns.map((column) => ({
    width: column.width ?? (column.digits !== undefined ? 13 : 16),
  }));
  let row = 1;
  const line = (
    value: string,
    options: { bold?: boolean; italic?: boolean; size?: number; center?: boolean },
  ) => {
    if (count > 1) {
      sheet.mergeCells(row, 1, row, count);
    }
    const cell = sheet.getCell(row, 1);
    cell.value = value;
    cell.font = { name: FONT, size: options.size ?? 11, bold: options.bold, italic: options.italic };
    cell.alignment = { horizontal: options.center ? 'center' : 'left', wrapText: true };
    row += 1;
  };
  if (report.companyName) {
    line(report.companyName, { bold: true });
  }
  if (report.companyAddress) {
    line(report.companyAddress, {});
  }
  row += 1;
  line(report.title, { bold: true, size: 14, center: true });
  if (report.subtitle) {
    line(report.subtitle, { bold: true, italic: true, center: true });
  }
  row += 1;
  const header = sheet.getRow(row);
  report.columns.forEach((column, index) => {
    const cell = header.getCell(index + 1);
    cell.value = column.title;
    cell.font = { name: FONT, size: 11, bold: true };
    cell.border = BORDER;
    cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
  });
  row += 1;
  report.rows.forEach((reportRow) => {
    const excelRow = sheet.getRow(row);
    reportRow.cells.forEach((value, index) => {
      const cell = excelRow.getCell(index + 1);
      cell.value = value;
      cell.border = BORDER;
      cell.font = { name: FONT, size: 11, bold: reportRow.bold, italic: reportRow.italic };
      cell.alignment = { vertical: 'top', wrapText: !isNumberColumn(report, index) };
      const digits = report.columns[index]?.digits;
      if (digits !== undefined && typeof value === 'number') {
        cell.numFmt = digits > 0 ? `#,##0.${'0'.repeat(digits)}` : '#,##0';
      }
    });
    row += 1;
  });
  row += 1;
  (report.notes ?? []).forEach((note) => line(note, { italic: true, size: 9 }));
  return workbook.xlsx.writeBuffer();
}

/** Định nghĩa pdfmake (A4 ngang); tách riêng để kiểm thử không cần trình duyệt. */
export function tableReportPdfDefinition(report: TableReport): TDocumentDefinitions {
  const widths = report.columns.map((column) => column.width ?? '*');
  return {
    pageSize: 'A4',
    pageOrientation: 'landscape',
    pageMargins: [20, 24, 20, 30],
    info: { title: report.title },
    defaultStyle: { fontSize: 9, lineHeight: 1.15 },
    footer: (current, total) => ({ text: `Trang ${current}/${total}`, alignment: 'center', fontSize: 8 }),
    content: [
      { text: report.companyName ?? '', bold: true },
      report.companyAddress ?? '',
      { text: report.title, fontSize: 14, bold: true, alignment: 'center', margin: [0, 10, 0, 0] },
      report.subtitle ? { text: report.subtitle, bold: true, italics: true, alignment: 'center' } : '',
      {
        margin: [0, 6, 0, 0],
        fontSize: 7.5,
        table: {
          headerRows: 1,
          widths,
          body: [
            report.columns.map((column): TableCell => ({
              text: column.title,
              bold: true,
              alignment: 'center',
            })),
            ...report.rows.map((row) =>
              report.columns.map((_, index): TableCell => ({
                text: reportCellText(report, row.cells, index),
                alignment: isNumberColumn(report, index) ? 'right' : 'left',
                bold: row.bold,
                italics: row.italic,
              })),
            ),
          ],
        },
      },
      ...(report.notes ?? []).map((note) => ({
        text: note,
        italics: true,
        fontSize: 8,
        margin: [0, 4, 0, 0],
      })),
    ] as TDocumentDefinitions['content'],
  };
}

export async function tableReportToPdf(report: TableReport): Promise<Blob> {
  return pdfBlob(tableReportPdfDefinition(report));
}
