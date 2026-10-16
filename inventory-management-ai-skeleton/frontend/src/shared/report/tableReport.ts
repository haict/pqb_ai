import { formatDecimal } from '../inventorySlip/slipContent';

/**
 * Báo cáo dạng bảng dùng chung cho các báo cáo M10 (ADR-18): cùng một nội dung cho bản in, Excel và PDF. Số giữ kiểu
 * số để Excel tính tiếp; {@code digits} là số chữ số thập phân hiển thị.
 */
export interface ReportColumn {
  title: string;
  /** Có giá trị: cột số, căn phải, hiển thị số chữ số thập phân này. */
  digits?: number;
  /** Độ rộng tương đối (Excel: ký tự; PDF: điểm). */
  width?: number;
  /** Tiêu đề nhóm ở hàng tiêu đề trên; các cột liền nhau cùng nhóm gộp một ô (Đầu kỳ: Số lượng, Giá trị). */
  group?: string;
}

export type ReportCell = string | number | null;

export interface ReportRow {
  cells: ReportCell[];
  bold?: boolean;
  italic?: boolean;
  /** Ô có liên kết mở chứng từ nguồn trên màn hình (BR-RPT-11); bản in, file tải về chỉ có chữ. */
  link?: { column: number; to: string };
  /** Dòng nhóm (ví dụ tổng theo kho): nền tô màu; ô đầu gộp {@code labelSpan} cột. */
  group?: boolean;
  labelSpan?: number;
}

/** Ô tiêu đề: {@code rowSpan} 2 khi cột không thuộc nhóm (tiêu đề hai tầng). */
export interface HeaderCell {
  title: string;
  colSpan: number;
  rowSpan: number;
}

export function hasGroupedHeader(report: TableReport) {
  return report.columns.some((column) => column.group !== undefined);
}

/** Hàng tiêu đề trên (nhóm hoặc cột không nhóm) và hàng dưới (cột thuộc nhóm). */
export function headerRows(report: TableReport): HeaderCell[][] {
  if (!hasGroupedHeader(report)) {
    return [report.columns.map((column) => ({ title: column.title, colSpan: 1, rowSpan: 1 }))];
  }
  const top: HeaderCell[] = [];
  const bottom: HeaderCell[] = [];
  report.columns.forEach((column, index) => {
    if (column.group === undefined) {
      top.push({ title: column.title, colSpan: 1, rowSpan: 2 });
      return;
    }
    const previous = top[top.length - 1];
    if (index > 0 && report.columns[index - 1]?.group === column.group && previous) {
      previous.colSpan += 1;
    } else {
      top.push({ title: column.group, colSpan: 1, rowSpan: 1 });
    }
    bottom.push({ title: column.title, colSpan: 1, rowSpan: 1 });
  });
  return [top, bottom];
}

/** Số cột ô {@code index} của dòng chiếm (0: ô bị gộp vào ô nhãn của dòng nhóm). */
export function cellSpan(row: ReportRow, index: number) {
  const span = row.labelSpan ?? 1;
  if (index === 0) {
    return span;
  }
  return index < span ? 0 : 1;
}

export function isNegativeCell(cells: ReportCell[], index: number) {
  const value = cells[index];
  return typeof value === 'number' && value < 0;
}

export interface TableReport {
  title: string;
  subtitle?: string;
  companyName?: string | null;
  companyAddress?: string | null;
  columns: ReportColumn[];
  rows: ReportRow[];
  /** Ghi chú cuối báo cáo (thời điểm số liệu, hạn chế). */
  notes?: string[];
  /** Tên file không đuôi, không dấu. */
  fileName: string;
}

export function reportCellText(report: TableReport, cells: ReportCell[], index: number): string {
  const value = cells[index];
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  return formatDecimal(value, report.columns[index]?.digits ?? 0);
}

export function isNumberColumn(report: TableReport, index: number) {
  return report.columns[index]?.digits !== undefined;
}

/** Chuỗi không dấu, chỉ chữ, số, gạch nối cho tên file. */
export function asciiFileName(value: string) {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/[^A-Za-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
