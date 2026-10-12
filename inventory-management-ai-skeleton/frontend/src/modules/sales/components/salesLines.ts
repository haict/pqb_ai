import type { Dayjs } from 'dayjs';
import type { SalesLine } from '../types';

/** Dòng báo giá / đơn bán trên form; giá trống thì server lấy theo bảng giá (BR-SAL-02). */
export interface SalesLineForm {
  productId?: number;
  productLabel?: string;
  unitId?: number;
  quantity?: number;
  unitPrice?: number;
  discountPercent?: number;
  taxRateId?: number;
  promotional?: boolean;
  expectedDate?: Dayjs;
  description?: string;
  priceHint?: string;
}

export function toLineForm(line: SalesLine): SalesLineForm {
  return {
    productId: line.productId,
    productLabel: `${line.productCode ?? ''} - ${line.productName ?? ''}`,
    unitId: line.unitId,
    quantity: line.quantity,
    unitPrice: line.unitPrice,
    discountPercent: line.discountPercent,
    taxRateId: line.taxRateId ?? undefined,
    promotional: line.promotional,
    description: line.description ?? undefined,
  };
}

export function toLineInput(line: SalesLineForm) {
  return {
    productId: line.productId,
    unitId: line.unitId,
    quantity: line.quantity,
    unitPrice: line.unitPrice ?? null,
    discountPercent: line.discountPercent ?? null,
    taxRateId: line.taxRateId ?? null,
    promotional: !!line.promotional,
    expectedDate: line.expectedDate ? line.expectedDate.format('YYYY-MM-DD') : null,
    description: line.description,
  };
}
