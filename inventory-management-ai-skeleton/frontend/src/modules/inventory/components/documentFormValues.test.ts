import dayjs from 'dayjs';
import { toDocumentInput } from './documentFormValues';

describe('toDocumentInput', () => {
  it('đổi ngày sang YYYY-MM-DD, giữ nguyên số lượng và đơn giá', () => {
    const input = toDocumentInput({
      documentType: 'PNK',
      documentDate: dayjs('2026-10-05'),
      warehouseId: 3,
      lines: [
        {
          productId: 7,
          unitId: 1,
          quantity: 2.5,
          unitCost: 1000.1234,
          lotNo: 'L01',
          expiryDate: dayjs('2027-06-30'),
        },
      ],
    });

    expect(input.documentDate).toBe('2026-10-05');
    expect(input.postingDate).toBeUndefined();
    expect(input.lines[0]).toMatchObject({
      quantity: 2.5,
      unitCost: 1000.1234,
      lotNo: 'L01',
      expiryDate: '2027-06-30',
      manufactureDate: undefined,
    });
  });

  it('chỉ gửi máy mới / cũ cho dòng nhập (BR-INV-43)', () => {
    const line = { productId: 7, unitId: 1, quantity: 1, itemCondition: 'USED' as const };
    const issue = toDocumentInput({
      documentType: 'PXK',
      documentDate: dayjs('2026-10-05'),
      warehouseId: 3,
      lines: [line],
    });
    const adjust = toDocumentInput({
      documentType: 'PDC',
      documentDate: dayjs('2026-10-05'),
      warehouseId: 3,
      lines: [
        { ...line, direction: 'IN' },
        { ...line, direction: 'OUT' },
      ],
    });

    expect(issue.lines[0]?.itemCondition).toBeUndefined();
    expect(adjust.lines.map((item) => item.itemCondition)).toEqual(['USED', undefined]);
  });

  it('D-77: kho theo dòng, không gửi chi nhánh; phiếu chuyển kho giữ kho đầu phiếu', () => {
    const lines = [
      { productId: 7, unitId: 1, quantity: 1, warehouseId: 3 },
      { productId: 8, unitId: 1, quantity: 1, warehouseId: 4 },
    ];
    const issue = toDocumentInput({
      documentType: 'PXK',
      documentDate: dayjs('2026-10-05'),
      branchId: 1,
      warehouseId: 9,
      lines,
    });
    const transfer = toDocumentInput({
      documentType: 'PCK',
      documentDate: dayjs('2026-10-05'),
      branchId: 1,
      warehouseId: 3,
      destinationWarehouseId: 5,
      lines,
    });

    expect('branchId' in issue).toBe(false);
    expect(issue.warehouseId).toBeUndefined();
    expect(issue.lines.map((line) => line.warehouseId)).toEqual([3, 4]);
    expect(transfer.warehouseId).toBe(3);
    expect(transfer.lines.map((line) => line.warehouseId)).toEqual([undefined, undefined]);
  });
});
