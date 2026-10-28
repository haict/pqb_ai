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
import type { SalesOcrResult } from '../../ai/types';

/** Trường đầu đơn bán mà gợi ý OCR có thể điền (khớp OrderForm của trang đơn bán). */
export interface SalesOcrHeaderValues {
  customerId?: number;
  customerLabel?: string;
  documentDate?: Dayjs;
  deliveryAddress?: string;
  description?: string;
}

export interface SalesOcrFill {
  header: SalesOcrHeaderValues;
  lines: OcrLineValues[];
  notes: string[];
}

/**
 * Chuyển phiếu đặt hàng của khách thành giá trị form đơn bán (AI-2, docs/14 mục 4.7): chỉ điền ô còn trống. Địa chỉ
 * giao không tự điền vì chữ có dấu có thể mất khi nhận dạng; chỉ ghi chú để người dùng đối chiếu. Đơn giá trống thì
 * server lấy theo bảng giá khi lưu. Không lưu.
 */
export function salesOcrFill(current: SalesOcrHeaderValues, result: SalesOcrResult): SalesOcrFill {
  const header: SalesOcrHeaderValues = {};
  const notes: string[] = [...result.warnings];
  const { customer, deliveryAddress } = result.header;
  if (customer.status === 'MATCHED' && customer.id !== null) {
    if (isEmpty(current.customerId)) {
      header.customerId = customer.id;
      header.customerLabel = catalogLabel(customer);
    } else if (current.customerId !== customer.id) {
      notes.push(`Chứng từ ghi khách hàng ${catalogLabel(customer)}, khác khách hàng đang chọn.`);
    }
  } else {
    notes.push(partnerNote(customer, 'khách hàng'));
  }
  fillText(header, current, 'description', result.header.description, notes, 'Diễn giải');
  fillDate(header, current, 'documentDate', result.header.documentDate, notes, 'Ngày đơn');
  if (deliveryAddress.value !== null) {
    notes.push(
      `Địa chỉ giao trên chứng từ (chữ có dấu có thể bị mất khi nhận dạng): ${deliveryAddress.value}. ` +
        'Kiểm tra ô Địa chỉ giao.',
    );
  }
  notes.push(...result.totals.check.warnings);
  return { header, lines: result.lines.map(ocrLineValues), notes: notes.filter((note) => note !== '') };
}
