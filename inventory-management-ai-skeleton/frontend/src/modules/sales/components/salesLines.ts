import type { Dayjs } from 'dayjs';
import type { SalesLine } from '../types';

/**
 * Dòng đơn bán trên form; giá trống thì server lấy theo bảng giá (BR-SAL-02); lô trống thì kho chọn theo FEFO khi ghi
 * sổ (BR-SAL-21).
 */
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
  lotNo?: string;
  lotOverrideReason?: string;
  serialNumbers?: string[];
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
    lotNo: line.lotNo ?? undefined,
    lotOverrideReason: line.lotOverrideReason ?? undefined,
    serialNumbers: line.serialNumbers,
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
    lotNo: line.lotNo || null,
    lotOverrideReason: line.lotOverrideReason || null,
    serialNumbers: line.serialNumbers ?? [],
    description: line.description,
  };
}
