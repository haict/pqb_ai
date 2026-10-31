import { orderActions as purchaseOrderActions } from '../purchasing/labels';
import { orderActions } from './labels';
import type { SalesOrder } from './types';

const all = () => true;
const without = (denied: string) => (permission: string) => permission !== denied;

function salesOrder(overrides: Partial<SalesOrder>): SalesOrder {
  return { status: 'POSTED', deliveryStatus: 'PENDING', einvoiceNo: null, ...overrides } as SalesOrder;
}

describe('bỏ ghi sổ (D-62)', () => {
  it('đơn bán đã ghi sổ, chưa giao, chưa có hóa đơn điện tử: có bỏ ghi sổ', () => {
    expect(orderActions(salesOrder({}), all)).toEqual(['ship', 'unpost', 'reverse']);
  });

  it('đơn bán đã chuyển đi giao hoặc đã có hóa đơn điện tử: chỉ còn đảo', () => {
    expect(orderActions(salesOrder({ deliveryStatus: 'IN_TRANSIT' }), all)).toEqual(['reverse']);
    expect(orderActions(salesOrder({ einvoiceNo: '0000001' }), all)).toEqual(['ship', 'reverse']);
  });

  it('thiếu quyền thì ẩn nút bỏ ghi sổ', () => {
    expect(orderActions(salesOrder({}), without('sales-order:unpost'))).toEqual(['ship', 'reverse']);
    expect(purchaseOrderActions('POSTED', without('purchase-order:unpost'))).toEqual(['reverse']);
  });

  it('đơn mua đã ghi sổ có bỏ ghi sổ; đơn nháp không có', () => {
    expect(purchaseOrderActions('POSTED', all)).toEqual(['unpost', 'reverse']);
    expect(purchaseOrderActions('DRAFT', all)).toEqual(['post', 'delete']);
  });
});

describe('xóa đơn thay cho hủy (D-65)', () => {
  it('đơn nháp và đơn cũ trước D-61 chưa ghi sổ: xóa, không còn hủy', () => {
    expect(orderActions(salesOrder({ status: 'DRAFT' }), all)).toEqual([
      'post',
      'reserve',
      'release',
      'delete',
    ]);
    expect(orderActions(salesOrder({ status: 'SUBMITTED' }), all)).toEqual(['delete']);
    expect(purchaseOrderActions('REJECTED', all)).toEqual(['delete']);
  });

  it('thiếu quyền lập đơn thì không có nút xóa', () => {
    expect(orderActions(salesOrder({ status: 'DRAFT' }), without('sales-order:create'))).toEqual(['post']);
    expect(purchaseOrderActions('DRAFT', without('purchase-order:create'))).toEqual(['post']);
  });
});
