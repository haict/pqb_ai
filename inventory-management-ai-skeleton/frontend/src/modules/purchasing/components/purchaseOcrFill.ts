import dayjs, { type Dayjs } from 'dayjs';
import {
  catalogLabel,
  fillDate,
  fillText,
  isEmpty,
  ocrLineValues,
  partnerNote,
  type OcrLineValues,
} from '../../ai/ocrFill';
import type { OcrLine, PurchaseOcrResult } from '../../ai/types';

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
export function purchaseOcrFill(current: OcrHeaderValues, result: PurchaseOcrResult, prices = true): OcrFill {
  const header: OcrHeaderValues = {};
  const notes: string[] = [...result.warnings];
  const { supplier } = result.header;
  if (supplier.status === 'MATCHED' && supplier.id !== null) {
    if (isEmpty(current.supplierId)) {
      header.supplierId = supplier.id;
      header.supplierLabel = catalogLabel(supplier);
      if (supplier.matchedBy === 'NAME') {
        // Phiếu xuất kho NCC không in MST: gán theo tên, người dùng phải xác nhận (BR-AI-06).
        notes.push(
          `Nhà cung cấp gán theo tên trên chứng từ "${supplier.rawText ?? ''}": kiểm tra đúng nhà cung cấp.`,
        );
      }
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
  if (prices) {
    notes.push(...result.totals.check.warnings);
  }
  const lines = result.lines.map((line) => withLot(ocrLineValues(line, prices), line));
  return { header, lines, notes: notes.filter((note) => note !== '') };
}

/**
 * Số lô, hạn dùng in trên phiếu xuất kho NCC. Hàng không quản lý lô thì dòng hàng trên form tự bỏ (BR-INV-01); lô
 * đọc chưa chắc chắn thì nhắc kiểm tra.
 */
function withLot(values: OcrLineValues, line: OcrLine): OcrLineValues {
  const lotNo = line.lotNo?.value ?? undefined;
  const expiry = line.expiryDate?.value ?? undefined;
  if (!lotNo && !expiry) {
    return values;
  }
  const hints = [...(values.ocrHints ?? [])];
  if (line.lotNo?.status === 'LOW_CONFIDENCE' || line.expiryDate?.status === 'LOW_CONFIDENCE') {
    hints.push('Số lô / hạn dùng đọc chưa chắc chắn: kiểm tra lại.');
  }
  return { ...values, lotNo, expiryDate: expiry ? dayjs(expiry) : undefined, ocrHints: hints };
}
