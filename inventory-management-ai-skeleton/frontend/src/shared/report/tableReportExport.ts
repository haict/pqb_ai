import ExcelJS from 'exceljs';
import type { TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import { pdfBlob } from '../pdfmakeLoader';
import {
  cellSpan,
  headerRows,
  isNegativeCell,
  isNumberColumn,
  reportCellText,
  type TableReport,
} from './tableReport';

const FONT = 'Times New Roman';
const THIN = { style: 'thin' as const };
const BORDER = { top: THIN, left: THIN, bottom: THIN, right: THIN };
const MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const GROUP_FILL = 'FFFDF3D0';

/** Định dạng số Excel như hiển thị (D-57): số âm đỏ trong ngoặc. */
function numberFormat(digits: number) {
  const positive = digits > 0 ? `#,##0.${'0'.repeat(digits)}` : '#,##0';
  return `${positive};[Red](${positive})`;
}

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
  const headers = headerRows(report);
  const topRow = headers[0] ?? [];
  headers.forEach((cells, level) => {
    let column = 1;
    cells.forEach((headerCell) => {
      while (level > 0 && topRow.some((top, i) => top.rowSpan === 2 && topStart(topRow, i) === column)) {
        column += 1;
      }
      const cell = sheet.getCell(row + level, column);
      cell.value = headerCell.title;
      if (headerCell.colSpan > 1 || headerCell.rowSpan > 1) {
        sheet.mergeCells(
          row + level,
          column,
          row + level + headerCell.rowSpan - 1,
          column + headerCell.colSpan - 1,
        );
      }
      column += headerCell.colSpan;
    });
  });
  for (let level = 0; level < headers.length; level += 1) {
    for (let column = 1; column <= count; column += 1) {
      const cell = sheet.getCell(row + level, column);
      cell.font = { name: FONT, size: 11, bold: true };
      cell.border = BORDER;
      cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
    }
  }
  row += headers.length;
  report.rows.forEach((reportRow) => {
    const excelRow = sheet.getRow(row);
    report.columns.forEach((_, index) => {
      const value = reportRow.cells[index] ?? null;
      const cell = excelRow.getCell(index + 1);
      cell.value = cellSpan(reportRow, index) === 0 ? null : value;
      cell.border = BORDER;
      cell.font = { name: FONT, size: 11, bold: reportRow.bold, italic: reportRow.italic };
      cell.alignment = { vertical: 'top', wrapText: !isNumberColumn(report, index) };
      if (reportRow.group) {
        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: GROUP_FILL } };
      }
      const digits = report.columns[index]?.digits;
      if (digits !== undefined && typeof value === 'number') {
        cell.numFmt = numberFormat(digits);
      }
    });
    if ((reportRow.labelSpan ?? 1) > 1) {
      sheet.mergeCells(row, 1, row, reportRow.labelSpan ?? 1);
    }
    row += 1;
  });
  row += 1;
  (report.notes ?? []).forEach((note) => line(note, { italic: true, size: 9 }));
  return workbook.xlsx.writeBuffer();
}

/** Định nghĩa pdfmake (A4 ngang); tách riêng để kiểm thử không cần trình duyệt. */
export function tableReportPdfDefinition(report: TableReport): TDocumentDefinitions {
  const widths = report.columns.map((column) => column.width ?? '*');
  const headers = headerRows(report);
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
          headerRows: headers.length,
          widths,
          body: [
            ...pdfHeaderRows(report),
            ...report.rows.map((row) =>
              report.columns.map((_, index): TableCell =>
                cellSpan(row, index) === 0
                  ? {}
                  : {
                      text: reportCellText(report, row.cells, index),
                      colSpan: cellSpan(row, index),
                      alignment:
                        isNumberColumn(report, index) && cellSpan(row, index) === 1 ? 'right' : 'left',
                      bold: row.bold,
                      italics: row.italic,
                      color: isNegativeCell(row.cells, index) ? '#d4380d' : undefined,
                      fillColor: row.group ? '#fdf3d0' : undefined,
                    },
              ),
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

/** Cột Excel (từ 1) bắt đầu của ô thứ {@code index} trên hàng tiêu đề trên. */
function topStart(top: ReturnType<typeof headerRows>[number], index: number) {
  return 1 + top.slice(0, index).reduce((sum, cell) => sum + cell.colSpan, 0);
}

/** Hàng tiêu đề pdfmake: ô gộp kèm ô rỗng giữ chỗ theo quy ước colSpan / rowSpan của pdfmake. */
function pdfHeaderRows(report: TableReport): TableCell[][] {
  const headers = headerRows(report);
  const grid: TableCell[][] = headers.map(() => report.columns.map((): TableCell => ({})));
  const spansDown = (column: number) =>
    (grid[0]?.[column] as { rowSpan?: number } | undefined)?.rowSpan === 2;
  headers.forEach((cells, level) => {
    const target = grid[level];
    let column = 0;
    cells.forEach((cell) => {
      while (level > 0 && spansDown(column)) {
        column += 1;
      }
      if (!target) {
        return;
      }
      target[column] = {
        text: cell.title,
        bold: true,
        alignment: 'center',
        colSpan: cell.colSpan,
        rowSpan: cell.rowSpan,
      };
      column += cell.colSpan;
    });
  });
  return grid;
}
