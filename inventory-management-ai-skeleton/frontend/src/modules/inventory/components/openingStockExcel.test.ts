import type { InventoryDocument, InventoryLine } from '../types';
import { OPENING_EXPORT_HEADERS, openingStockInfo, openingStockRows } from './openingStockExcel';

function line(overrides: Partial<InventoryLine>): InventoryLine {
  return {
    id: 1,
    lineNo: 1,
    productId: 7,
    productCode: 'VT01',
    productName: 'Vật tư 01',
    unitId: 3,
    conversionFactor: 1,
    quantity: 10,
    baseQuantity: 10,
    direction: null,
    locationId: null,
    destinationLocationId: null,
    lotId: null,
    lotNo: null,
    manufactureDate: null,
    expiryDate: null,
    qualityStatus: 'PASSED',
    targetQualityStatus: null,
    unitCost: 50000,
    amount: 500000,
    lotOverrideReason: null,
    note: null,
    serialNumbers: [],
    itemCondition: null,
    warehouseId: 1,
    ...overrides,
  };
}

const codes = { warehouseCode: () => 'KHO1', unitCode: () => 'CAI' };

describe('xuất Excel tồn kho đầu kỳ', () => {
  it('12 cột đầu theo file mẫu nhập, ngày dd/mm/yyyy, serial nối dấu phẩy, máy cũ ghi x', () => {
    const document = {
      warehouseId: 1,
      lines: [
        line({ lotNo: 'L1', manufactureDate: '2026-01-15', expiryDate: '2028-01-31' }),
        line({ productCode: 'MAY01', serialNumbers: ['S1', 'S2'], quantity: 2, itemCondition: 'USED' }),
      ],
    } as unknown as InventoryDocument;
    const rows = openingStockRows(document, codes);
    expect(OPENING_EXPORT_HEADERS.slice(0, 3)).toEqual(['Mã kho*', 'Mã hàng*', 'Mã ĐVT (trống = ĐVT cơ sở)']);
    expect(rows[0]).toEqual([
      'KHO1',
      'VT01',
      'CAI',
      10,
      50000,
      'L1',
      '15/01/2026',
      '31/01/2028',
      null,
      'PASSED',
      null,
      null,
      'Vật tư 01',
      500000,
    ]);
    expect(rows[1]?.[8]).toBe('S1, S2');
    expect(rows[1]?.[11]).toBe('x');
  });

  it('thông tin chứng từ gồm số, trạng thái, ngày', () => {
    const info = openingStockInfo(
      {
        documentNo: 'PNK-01',
        status: 'DRAFT',
        documentDate: '2026-11-01',
        postingDate: null,
        note: null,
        totalAmount: 0,
      } as unknown as InventoryDocument,
      'KHO1 - Kho 1',
    );
    expect(info[0]).toEqual(['Số chứng từ', 'PNK-01']);
    expect(info[2]).toEqual(['Ngày chứng từ', '01/11/2026']);
  });
});
