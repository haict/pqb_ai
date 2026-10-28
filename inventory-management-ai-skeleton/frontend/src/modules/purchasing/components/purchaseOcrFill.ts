import type { Dayjs } from 'dayjs';
import {
  catalogLabel,
  fillDate,
  fillText,
  isEmpty,
  ocrLineValues,
  partnerNote,
  type OcrLineValues,
} from '../../ai/ocrFill';
import type { PurchaseOcrResult } from '../../ai/types';

export type { OcrLineValues } from '../../ai/ocrFill';

/** Trường của form đơn mua mà gợi ý OCR có thể điền (khớp OrderForm / LineForm của trang đơn mua). */
export interface OcrHeaderValues {
  supplierId?: number;
  supplierLabel?: string;
  documentDate?: Dayjs;
  invoiceSeries?: string;
  invoiceNo?: string;
  invoiceDate?: Dayjs;
  description?: string;
}

export interface OcrFill {
  /** Chỉ các trường đầu phiếu đang trống được điền. */
  header: OcrHeaderValues;
  lines: OcrLineValues[];
  /** Ghi chú cho cả chứng từ: nhà cung cấp, loại tiền, tổng tiền, giá trị không điền vì ô đã có dữ liệu. */
  notes: string[];
}

/**
 * Chuyển kết quả nhận dạng thành giá trị form đơn mua (D-64, docs/14 mục 4.7): chỉ điền ô còn trống, không ghi đè dữ
 * liệu người dùng đã nhập; dòng hàng thêm mới kèm điều cần kiểm tra. Không lưu: người dùng bấm Lưu như nhập tay.
 */
export function purchaseOcrFill(current: OcrHeaderValues, result: PurchaseOcrResult): OcrFill {
  const header: OcrHeaderValues = {};
  const notes: string[] = [...result.warnings];
  const { supplier } = result.header;
  if (supplier.status === 'MATCHED' && supplier.id !== null) {
    if (isEmpty(current.supplierId)) {
      header.supplierId = supplier.id;
      header.supplierLabel = catalogLabel(supplier);
    } else if (current.supplierId !== supplier.id) {
      notes.push(`Chứng từ ghi nhà cung cấp ${catalogLabel(supplier)}, khác nhà cung cấp đang chọn.`);
    }
  } else {
    notes.push(partnerNote(supplier, 'nhà cung cấp'));
  }
  fillText(header, current, 'invoiceSeries', result.header.invoiceSeries, notes, 'Ký hiệu hóa đơn');
  fillText(header, current, 'invoiceNo', result.header.invoiceNo, notes, 'Số hóa đơn');
  fillText(header, current, 'description', result.header.description, notes, 'Diễn giải');
  fillDate(header, current, 'invoiceDate', result.header.invoiceDate, notes, 'Ngày hóa đơn');
  fillDate(header, current, 'documentDate', result.header.documentDate, notes, 'Ngày đơn');
  notes.push(...result.totals.check.warnings);
  return { header, lines: result.lines.map(ocrLineValues), notes: notes.filter((note) => note !== '') };
}
