import type { ReceiptPick } from '../../sales/components/salesLines';
import type { DocumentFormValues } from './documentFormValues';

type LineForm = DocumentFormValues['lines'][number];

/**
 * Dòng phiếu xuất kho từ chứng từ nhập chọn bằng F8 (D-79): mỗi lô một dòng theo đơn vị cơ sở, serial cùng lô gộp một
 * dòng; giữ hàng, kho, ghi chú của dòng đang sửa.
 */
export function issueLinesFromReceipts(
  current: LineForm,
  picks: ReceiptPick[],
  baseUnitId: number,
  lotOverrideReason?: string,
): LineForm[] {
  const groups = new Map<string, ReceiptPick[]>();
  for (const pick of picks) {
    const key = pick.source.serialNo ? `lot:${pick.source.lotId ?? ''}` : `tx:${pick.source.transactionId}`;
    groups.set(key, [...(groups.get(key) ?? []), pick]);
  }
  return [...groups.values()].map((group) => {
    const first = group[0]!.source;
    const serials = group.map((pick) => pick.source.serialNo).filter((serial): serial is string => !!serial);
    return {
      productId: current.productId,
      warehouseId: current.warehouseId,
      unitId: baseUnitId,
      quantity: group.reduce((sum, pick) => sum + pick.quantity, 0),
      lotId: first.lotId ?? undefined,
      lotOverrideReason: first.lotId ? lotOverrideReason || undefined : undefined,
      serialNumbers: serials.length > 0 ? serials : undefined,
      note: current.note,
    };
  });
}
