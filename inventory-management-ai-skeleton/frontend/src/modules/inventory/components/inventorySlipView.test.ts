import { toSlipView } from './inventorySlipView';
import type { InventorySlip } from '../types';

const slip: InventorySlip = {
  companyName: 'Công ty A',
  companyAddress: 'Hà Nội',
  documentId: 1,
  documentType: 'PXK',
  documentNo: 'PXK0001',
  slipDate: '2026-11-10',
  partnerName: null,
  partnerAddress: null,
  partnerTaxCode: null,
  description: 'Xuất kho',
  sourceNo: null,
  warehouseCode: 'K1, K2',
  warehouseName: 'Kho 1, Kho 2',
  lines: [
    {
      lineNo: 1,
      productCode: 'H1',
      productName: 'Hàng 1',
      warehouseCode: 'K2',
      unitId: 3,
      quantity: 5,
      unitPrice: 0,
      amount: 0,
      items: [
        { lotNo: 'L1', expiryDate: '2027-01-31', serialNo: null, baseQuantity: 2 },
        { lotNo: 'L2', expiryDate: '2027-03-31', serialNo: null, baseQuantity: 3 },
      ],
    },
  ],
  totalAmount: 0,
};

describe('toSlipView (D-78)', () => {
  it('phiếu xuất: tiêu đề, kho theo dòng, nhiều lô kèm số lượng, không thuế', () => {
    const view = toSlipView(slip, () => 'Cái');

    expect(view.title).toBe('PHIẾU XUẤT KHO');
    expect(view.slipNo).toBe('PXK0001');
    expect(view.infoRows).toContain('Xuất tại kho: Kho 1, Kho 2');
    expect(view.lines[0]).toMatchObject({
      warehouseCode: 'K2',
      unitName: 'Cái',
      lots: ['L1 (2,00)', 'L2 (3,00)'],
    });
    expect(view.taxRates).toEqual([]);
    expect(view.signers).toContain('Người nhận hàng');
  });

  it('phiếu nhập: tiêu đề và người giao hàng', () => {
    const view = toSlipView({ ...slip, documentType: 'PNK', partnerName: 'NCC B' }, () => 'Cái');

    expect(view.title).toBe('PHIẾU NHẬP KHO');
    expect(view.infoRows[0]).toBe('Họ và tên người giao hàng: NCC B');
  });
});
