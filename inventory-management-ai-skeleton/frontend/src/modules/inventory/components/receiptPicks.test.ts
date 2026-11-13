import { issueLinesFromReceipts } from './receiptPicks';
import type { ReceiptSource } from '../../sales/types';

const source = (id: number, lotId: number, serialNo: string | null): ReceiptSource => ({
  transactionId: id,
  documentType: 'PNK',
  documentId: 1,
  documentNo: `PNK${id}`,
  postingDate: '2026-11-01',
  lotNo: `L${lotId}`,
  expiryDate: null,
  serialNo,
  receivedQuantity: 10,
  unitCost: 1000,
  availableQuantity: 10,
  lotId,
  expired: false,
});

describe('issueLinesFromReceipts (D-79)', () => {
  it('mỗi dòng nhập theo lô một dòng xuất, đơn vị cơ sở, giữ kho của dòng', () => {
    const lines = issueLinesFromReceipts(
      { productId: 7, unitId: 2, quantity: 1, warehouseId: 3, note: 'n' },
      [
        { source: source(1, 11, null), quantity: 4 },
        { source: source(2, 12, null), quantity: 2 },
      ],
      9,
      'Lý do',
    );

    expect(lines).toEqual([
      {
        productId: 7,
        warehouseId: 3,
        unitId: 9,
        quantity: 4,
        lotId: 11,
        lotOverrideReason: 'Lý do',
        serialNumbers: undefined,
        note: 'n',
      },
      {
        productId: 7,
        warehouseId: 3,
        unitId: 9,
        quantity: 2,
        lotId: 12,
        lotOverrideReason: 'Lý do',
        serialNumbers: undefined,
        note: 'n',
      },
    ]);
  });

  it('serial cùng lô gộp một dòng', () => {
    const lines = issueLinesFromReceipts(
      { productId: 7, unitId: 9, quantity: 1, warehouseId: 3 },
      [
        { source: source(1, 11, 'S1'), quantity: 1 },
        { source: source(2, 11, 'S2'), quantity: 1 },
      ],
      9,
    );

    expect(lines).toHaveLength(1);
    expect(lines[0]).toMatchObject({ quantity: 2, lotId: 11, serialNumbers: ['S1', 'S2'] });
  });
});
