import dayjs, { type Dayjs } from 'dayjs';
import type { CatalogMatch, OcrLine, OcrValue } from './types';

/** Giá trị dòng hàng điền từ chứng từ, chung cho form đơn mua và đơn bán. */
export interface OcrLineValues {
  productId?: number;
  productLabel?: string;
  unitId?: number;
  quantity?: number;
  unitPrice?: number;
  discountPercent?: number;
  taxRateId?: number;
  /** Chỉ hiển thị trên form, không gửi server: điều cần kiểm tra của dòng nhận dạng. */
  ocrHints?: string[];
  ocrLevel?: 'warning' | 'error';
}

export const catalogLabel = (match: CatalogMatch) => `${match.code} - ${match.name}`;
const toNumber = (value: OcrValue) => (value.value === null ? undefined : Number(value.value));
const toDate = (value: OcrValue) => (value.value === null ? undefined : dayjs(value.value));
export const isEmpty = (value: unknown) => value === undefined || value === null || value === '';

/**
 * Điền một ô chữ khi đang trống; ô đã có dữ liệu khác thì chỉ ghi chú giá trị trên chứng từ (docs/14 mục 4.7: không
 * ghi đè dữ liệu người dùng đã nhập).
 */
export function fillText<T extends object>(
  target: T,
  current: T,
  field: { [K in keyof T]: T[K] extends string | undefined ? K : never }[keyof T],
  value: OcrValue,
  notes: string[],
  name: string,
) {
  if (value.value === null) {
    return;
  }
  if (isEmpty(current[field])) {
    target[field] = value.value as T[typeof field];
  } else if (current[field] !== value.value) {
    notes.push(`${name} trên chứng từ: ${value.value} (không điền vì ô đã có dữ liệu).`);
  }
}

/** Như {@link fillText} cho ô ngày. */
export function fillDate<T extends object>(
  target: T,
  current: T,
  field: { [K in keyof T]: T[K] extends Dayjs | undefined ? K : never }[keyof T],
  value: OcrValue,
  notes: string[],
  name: string,
) {
  const date = toDate(value);
  if (!date) {
    return;
  }
  const existing = current[field] as Dayjs | undefined;
  if (!existing) {
    target[field] = date as T[typeof field];
  } else if (!existing.isSame(date, 'day')) {
    notes.push(`${name} trên chứng từ: ${date.format('DD/MM/YYYY')} (không điền vì ô đã có dữ liệu).`);
  }
}

/** Ghi chú khi đối tác (nhà cung cấp / khách hàng) không gán được theo MST. */
export function partnerNote(partner: CatalogMatch, role: string): string {
  const raw = partner.rawText ?? '';
  switch (partner.status) {
    case 'AMBIGUOUS':
      // Trùng MST hoặc chứng từ không in MST (gợi ý theo tên): không tự chọn (BR-AI-06).
      return `Chứng từ ghi ${role} "${raw}", chưa xác định được. Gợi ý: ${partner.candidates
        .map((c) => `${c.code} - ${c.name}`)
        .join('; ')}. Chọn ${role}.`;
    case 'NOT_FOUND':
      return `Không tìm thấy ${role} ${raw} trong danh mục. Chọn hoặc khai báo ${role}.`;
    default:
      return raw ? `Chứng từ không đọc được MST ${role} (${raw}). Chọn ${role}.` : '';
  }
}

/** Dòng hàng nhận dạng thành giá trị form kèm điều cần kiểm tra (BR-AI-06: chỉ gán id khi MATCHED). */
export function ocrLineValues(line: OcrLine): OcrLineValues {
  const hints: string[] = [];
  const values: OcrLineValues = {
    quantity: toNumber(line.quantity),
    unitPrice: toNumber(line.unitPrice),
    discountPercent: toNumber(line.discountPercent),
  };
  const { product, unit, taxRate } = line;
  if (product.status === 'MATCHED' && product.id !== null) {
    values.productId = product.id;
    values.productLabel = catalogLabel(product);
    if (product.matchedBy === 'NAME') {
      hints.push(`Hàng gán theo tên trên chứng từ "${product.rawText ?? ''}": kiểm tra đúng mã hàng.`);
    } else if (product.reviewRequired) {
      hints.push('Mã hàng đọc chưa chắc chắn: kiểm tra lại.');
    }
  } else {
    hints.push(productHint(product));
  }
  if (unit.status === 'MATCHED' && unit.id !== null) {
    values.unitId = unit.id;
  } else if (unit.rawText) {
    hints.push(`ĐVT trên chứng từ "${unit.rawText}" chưa xác định: chọn ĐVT.`);
  }
  if (taxRate.status === 'MATCHED' && taxRate.id !== null) {
    values.taxRateId = taxRate.id;
  } else if (taxRate.status !== 'UNKNOWN') {
    hints.push(`Thuế suất ${taxRate.rawText ?? ''} không có hoặc trùng trong danh mục: chọn thuế suất.`);
  }
  for (const [name, value] of [
    ['Số lượng', line.quantity],
    ['Đơn giá', line.unitPrice],
  ] as const) {
    if (value.status === 'LOW_CONFIDENCE') {
      hints.push(`${name} đọc chưa chắc chắn: kiểm tra lại.`);
    }
  }
  hints.push(...line.amountCheck.warnings);
  return {
    ...values,
    ocrHints: hints.filter((hint) => hint !== ''),
    ocrLevel: line.amountCheck.status === 'MISMATCH' ? 'error' : 'warning',
  };
}

function productHint(product: CatalogMatch): string {
  const raw = product.rawText ?? '';
  switch (product.status) {
    case 'AMBIGUOUS':
      return `Chứng từ ghi "${raw}". Gợi ý: ${product.candidates.map((c) => `${c.code} - ${c.name}`).join('; ')}. Chọn hàng.`;
    case 'NOT_FOUND':
      return `Không tìm thấy hàng "${raw}" trong danh mục: chọn hàng hoặc khai báo hàng mới.`;
    default:
      return 'Chứng từ không đọc được tên / mã hàng của dòng này: chọn hàng.';
  }
}
