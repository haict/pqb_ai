import dayjs from 'dayjs';
import { partnerField, toDocumentInput } from './documentFormValues';

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

  it('chỉ gửi đối tác cho loại chứng từ có ô đối tác', () => {
    const base = { documentDate: dayjs('2026-11-15'), warehouseId: 3, partnerId: 11, lines: [] };

    expect(toDocumentInput({ ...base, documentType: 'PNK' }).partnerId).toBe(11);
    expect(toDocumentInput({ ...base, documentType: 'PXK' }).partnerId).toBe(11);
    expect(toDocumentInput({ ...base, documentType: 'PCK' }).partnerId).toBe(11);
    expect(toDocumentInput({ ...base, documentType: 'PNK', subType: 'OPENING' }).partnerId).toBeUndefined();
    expect(toDocumentInput({ ...base, documentType: 'PXH' }).partnerId).toBeUndefined();
    expect(toDocumentInput({ ...base, documentType: 'PDC' }).partnerId).toBeUndefined();
  });
});

describe('partnerField', () => {
  it('nhập kho = nhà cung cấp; xuất / chuyển kho = khách hàng; nhập thu hồi bắt buộc khách trả hàng', () => {
    expect(partnerField('PNK', undefined)).toEqual({ label: 'Nhà cung cấp', role: 'SUPPLIER', required: false });
    expect(partnerField('PXK', null)).toEqual({ label: 'Khách hàng', role: 'CUSTOMER', required: false });
    expect(partnerField('PCK', null)).toEqual({ label: 'Khách hàng', role: 'CUSTOMER', required: false });
    expect(partnerField('PNK', 'RECALL_IN')).toEqual({ label: 'Khách trả hàng', role: 'CUSTOMER', required: true });
    expect(partnerField('PNK', 'OPENING')).toBeNull();
    expect(partnerField('PXH', null)).toBeNull();
    expect(partnerField('PCL', null)).toBeNull();
  });
});
