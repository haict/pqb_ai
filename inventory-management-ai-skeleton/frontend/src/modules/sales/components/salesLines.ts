import type { Dayjs } from 'dayjs';
import type { ReceiptSource, SalesLine } from '../types';

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
  /** Chứng từ nhập chọn bằng F8 (D-63). */
  receiptTransactionId?: number;
  receiptNo?: string;
  description?: string;
  priceHint?: string;
}

/** Dòng nhập được chọn trong hộp F8 và số lượng lấy (đơn vị cơ sở). */
export interface ReceiptPick {
  source: ReceiptSource;
  quantity: number;
}

/**
 * Dòng đơn bán từ các chứng từ nhập chọn bằng F8 (D-63): mỗi (chứng từ nhập, lô) một dòng, đơn vị cơ sở; serial cùng
 * chứng từ / lô gộp một dòng. Giữ hàng, thuế, chiết khấu, ghi chú của dòng đang sửa; đơn giá giữ khi dòng đã ở đơn vị
 * cơ sở (khác thì để trống, server lấy theo bảng giá).
 */
export function linesFromReceipts(
  current: SalesLineForm,
  picks: ReceiptPick[],
  baseUnitId: number,
  lotOverrideReason?: string,
): SalesLineForm[] {
  const groups = new Map<string, { first: ReceiptSource; picks: ReceiptPick[] }>();
  for (const pick of picks) {
    const key = pick.source.serialNo
      ? `${pick.source.documentNo}|${pick.source.lotNo ?? ''}`
      : String(pick.source.transactionId);
    const group = groups.get(key) ?? { first: pick.source, picks: [] };
    group.picks.push(pick);
    groups.set(key, group);
  }
  return [...groups.values()].map(({ first, picks: group }) => {
    const serials = group.map((pick) => pick.source.serialNo).filter((serial): serial is string => !!serial);
    return {
      productId: current.productId,
      productLabel: current.productLabel,
      unitId: baseUnitId,
      quantity: group.reduce((sum, pick) => sum + pick.quantity, 0),
      unitPrice: current.unitId === baseUnitId ? current.unitPrice : undefined,
      discountPercent: current.discountPercent,
      taxRateId: current.taxRateId,
      promotional: current.promotional,
      expectedDate: current.expectedDate,
      lotNo: first.lotNo ?? undefined,
      lotOverrideReason: first.lotNo ? lotOverrideReason || undefined : undefined,
      serialNumbers: serials,
      receiptTransactionId: first.transactionId,
      receiptNo: first.documentNo,
      description: current.description,
    };
  });
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
    receiptTransactionId: line.receiptTransactionId ?? undefined,
    receiptNo: line.receiptNo ?? undefined,
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
    receiptTransactionId: line.receiptTransactionId ?? null,
    description: line.description,
  };
}
