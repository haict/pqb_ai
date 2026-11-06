import { formatDate, statusLabel } from '../labels';
import type { InventoryDocument } from '../types';

/**
 * Cột 1..12 trùng file mẫu nhập tồn kho đầu kỳ (OpeningStockImportService.HEADERS) để sửa rồi nhập lại được; cột sau
 * chỉ để đối chiếu, khi nhập bị bỏ qua.
 */
export const OPENING_EXPORT_HEADERS = [
  'Mã kho*',
  'Mã hàng*',
  'Mã ĐVT (trống = ĐVT cơ sở)',
  'Số lượng*',
  'Đơn giá*',
  'Số lô',
  'Ngày sản xuất (dd/mm/yyyy)',
  'Hạn dùng (dd/mm/yyyy)',
  'Số serial (phân tách dấu phẩy)',
  'Trạng thái chất lượng',
  'Ghi chú',
  'Máy cũ (x)',
  'Tên hàng',
  'Thành tiền',
];

export interface OpeningExportCodes {
  warehouseCode(id: number): string;
  unitCode(id: number): string;
}

type Cell = string | number | null;

/** Dòng dữ liệu theo {@link OPENING_EXPORT_HEADERS}; ngày dạng dd/mm/yyyy như file mẫu. */
export function openingStockRows(document: InventoryDocument, codes: OpeningExportCodes): Cell[][] {
  const warehouse = codes.warehouseCode(document.warehouseId);
  return document.lines.map((line) => [
    warehouse,
    line.productCode,
    codes.unitCode(line.unitId),
    line.quantity,
    line.unitCost,
    line.lotNo,
    line.manufactureDate ? formatDate(line.manufactureDate) : null,
    line.expiryDate ? formatDate(line.expiryDate) : null,
    line.serialNumbers.length > 0 ? line.serialNumbers.join(', ') : null,
    line.qualityStatus,
    line.note,
    line.itemCondition === 'USED' ? 'x' : null,
    line.productName,
    line.amount,
  ]);
}

/** Thông tin chứng từ ở sheet thứ hai (sheet đầu giữ đúng bố cục file mẫu để nhập lại). */
export function openingStockInfo(document: InventoryDocument, warehouseLabel: string): [string, Cell][] {
  return [
    ['Số chứng từ', document.documentNo],
    ['Trạng thái', statusLabel(document.status)],
    ['Ngày chứng từ', formatDate(document.documentDate)],
    ['Ngày hạch toán', formatDate(document.postingDate)],
    ['Kho', warehouseLabel],
    ['Ghi chú', document.note],
    ['Tổng giá trị', document.totalAmount],
  ];
}

const MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/** File .xlsx của phiếu tồn kho đầu kỳ; exceljs chỉ tải khi người dùng bấm. */
export async function openingStockToExcel(
  document: InventoryDocument,
  codes: OpeningExportCodes,
  warehouseLabel: string,
): Promise<Blob> {
  const { default: ExcelJS } = await import('exceljs');
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet('Ton dau ky');
  sheet.addRow(OPENING_EXPORT_HEADERS).font = { bold: true };
  openingStockRows(document, codes).forEach((row) => sheet.addRow(row));
  sheet.columns.forEach((column, index) => {
    column.width = Math.max(12, (OPENING_EXPORT_HEADERS[index]?.length ?? 0) + 4);
  });
  [4, 5, 14].forEach((index) => (sheet.getColumn(index).numFmt = '#,##0.####'));
  sheet.views = [{ state: 'frozen', ySplit: 1 }];

  const info = workbook.addWorksheet('Thong tin chung tu');
  openingStockInfo(document, warehouseLabel).forEach((row) => info.addRow(row));
  info.getColumn(1).font = { bold: true };
  info.getColumn(1).width = 18;
  info.getColumn(2).width = 40;
  return new Blob([await workbook.xlsx.writeBuffer()], { type: MIME });
}
