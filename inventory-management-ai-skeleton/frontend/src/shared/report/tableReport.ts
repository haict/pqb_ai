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
}

export type ReportCell = string | number | null;

export interface ReportRow {
  cells: ReportCell[];
  bold?: boolean;
  italic?: boolean;
  /** Ô có liên kết mở chứng từ nguồn trên màn hình (BR-RPT-11); bản in, file tải về chỉ có chữ. */
  link?: { column: number; to: string };
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
