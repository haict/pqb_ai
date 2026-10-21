import type { ReceiptSource } from '../types';
import { linesFromReceipts, toLineInput } from './salesLines';

function source(overrides: Partial<ReceiptSource>): ReceiptSource {
  return {
    transactionId: 1,
    documentType: 'PNK',
    documentId: 10,
    documentNo: 'PNK-HN-2026-000001',
    postingDate: '2026-10-05',
    lotNo: 'L1',
    expiryDate: '2028-01-31',
    serialNo: null,
    receivedQuantity: 10,
    unitCost: 50000,
    availableQuantity: 10,
    ...overrides,
  };
}

const current = {
  productId: 7,
  productLabel: 'VT - Vật tư',
  unitId: 1,
  quantity: 1,
  unitPrice: 100000,
  taxRateId: 3,
  description: 'ghi chú',
};

describe('chọn chứng từ nhập bằng F8 (D-63)', () => {
  it('mỗi chứng từ / lô thành một dòng đơn vị cơ sở, giữ giá khi đơn vị không đổi, gửi id dòng nhập', () => {
    const lines = linesFromReceipts(
      current,
      [
        { source: source({ transactionId: 1, lotNo: 'L1' }), quantity: 3 },
        { source: source({ transactionId: 2, lotNo: 'L2', documentNo: 'PNK-HN-2026-000002' }), quantity: 2 },
      ],
      1,
      'Khách yêu cầu',
    );
    expect(lines).toHaveLength(2);
    expect(lines[0]).toMatchObject({
      productId: 7,
      unitId: 1,
      quantity: 3,
      unitPrice: 100000,
      lotNo: 'L1',
      lotOverrideReason: 'Khách yêu cầu',
      receiptTransactionId: 1,
      receiptNo: 'PNK-HN-2026-000001',
      taxRateId: 3,
    });
    expect(lines[1]).toMatchObject({ quantity: 2, lotNo: 'L2', receiptNo: 'PNK-HN-2026-000002' });
    expect(toLineInput(lines[1] ?? {}).receiptTransactionId).toBe(2);
  });

  it('serial cùng chứng từ gộp một dòng; đổi sang đơn vị cơ sở thì bỏ đơn giá để lấy theo bảng giá', () => {
    const lines = linesFromReceipts(
      { ...current, unitId: 5 },
      [
        {
          source: source({ transactionId: 11, lotNo: null, serialNo: 'S1', availableQuantity: 1 }),
          quantity: 1,
        },
        {
          source: source({ transactionId: 12, lotNo: null, serialNo: 'S2', availableQuantity: 1 }),
          quantity: 1,
        },
      ],
      1,
    );
    expect(lines).toHaveLength(1);
    expect(lines[0]).toMatchObject({
      quantity: 2,
      unitId: 1,
      unitPrice: undefined,
      serialNumbers: ['S1', 'S2'],
    });
    expect(lines[0]?.lotOverrideReason).toBeUndefined();
  });
});
