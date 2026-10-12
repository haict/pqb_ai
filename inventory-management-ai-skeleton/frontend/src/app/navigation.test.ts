import { visibleNavGroups } from './navigation';

describe('visibleNavGroups', () => {
  it('chỉ hiện mục người dùng có quyền xem', () => {
    const groups = visibleNavGroups((permission) => ['branch:view', 'department:view'].includes(permission));

    expect(groups.map((group) => group.key)).toEqual(['work', 'system']);
    expect(groups.find((group) => group.key === 'system')?.items.map((item) => item.label)).toEqual([
      'Chi nhánh',
      'Phòng ban',
    ]);
  });

  it('kỳ, tham số, số chứng từ hiện theo quyền xem riêng', () => {
    const groups = visibleNavGroups((permission) =>
      ['period:view', 'document-number:view'].includes(permission),
    );

    expect(groups.find((group) => group.key === 'system')?.items.map((item) => item.label)).toEqual([
      'Kỳ kế toán, kỳ kho',
      'Số chứng từ',
    ]);
  });

  it('nhóm kho hiện theo quyền xem tồn, kiểm kê, thu hồi, chính sách', () => {
    const groups = visibleNavGroups((permission) =>
      ['inventory:view', 'stock-count:view'].includes(permission),
    );

    expect(groups.find((group) => group.key === 'inventory')?.items.map((item) => item.label)).toEqual([
      'Tồn kho',
      'Chứng từ kho',
      'Lô, serial',
      'Kiểm kê',
    ]);
  });

  it('nhóm công nợ hiện theo quyền xem công nợ, công nợ đầu kỳ', () => {
    const groups = visibleNavGroups((permission) => permission === 'debt:view');

    expect(groups.find((group) => group.key === 'finance')?.items.map((item) => item.label)).toEqual([
      'Công nợ phải thu',
      'Công nợ phải trả',
      'Công nợ tạm ứng',
    ]);
  });

  it('thủ quỹ thấy thu chi, sổ quỹ, quỹ trước công nợ', () => {
    const groups = visibleNavGroups((permission) =>
      ['cash-document:view', 'cash-book:view', 'cash-account:view', 'debt:view'].includes(permission),
    );

    const finance = groups.find((group) => group.key === 'finance');
    expect(finance?.label).toBe('Công nợ, quỹ');
    expect(finance?.items.map((item) => item.label)).toEqual([
      'Thu chi, ngân hàng',
      'Sổ quỹ, dòng tiền',
      'Công nợ phải thu',
      'Công nợ phải trả',
      'Công nợ tạm ứng',
      'Quỹ, tài khoản ngân hàng',
    ]);
  });

  it('kế toán công nợ thấy bù trừ, điều chỉnh, thanh toán tạm ứng, đối chiếu, kiểm kê quỹ', () => {
    const groups = visibleNavGroups((permission) =>
      ['debt-offset:view', 'debt-adjustment:view', 'debt-reconciliation:view', 'cash-count:view'].includes(
        permission,
      ),
    );

    expect(groups.find((group) => group.key === 'finance')?.items.map((item) => item.label)).toEqual([
      'Kiểm kê quỹ',
      'Bù trừ công nợ',
      'Điều chỉnh công nợ',
      'Thanh toán tạm ứng',
      'Đối chiếu công nợ',
    ]);
  });

  it('nhóm kế toán hiện theo quyền xem bút toán, tài khoản, quy tắc hạch toán', () => {
    const groups = visibleNavGroups((permission) => ['account:view', 'journal:view'].includes(permission));

    expect(groups.find((group) => group.key === 'accounting')?.items.map((item) => item.label)).toEqual([
      'Bút toán',
      'Hệ thống tài khoản',
    ]);
  });

  it('ẩn cả nhóm khi không có quyền nào trong nhóm; việc chờ duyệt luôn hiện', () => {
    expect(visibleNavGroups(() => false).map((group) => group.key)).toEqual(['work']);
  });
  it('nhóm mua hàng hiện theo quyền xem đề nghị mua, đơn mua', () => {
    const groups = visibleNavGroups((permission) =>
      [
        'purchase-order:view',
        'purchase-receipt:view',
        'purchase-invoice:view',
        'purchase-return:view',
        'purchase-cost-allocation:view',
      ].includes(permission),
    );

    const purchasing = groups.find((group) => group.key === 'purchasing');
    expect(purchasing?.label).toBe('Mua hàng');
    expect(purchasing?.items.map((item) => item.label)).toEqual([
      'Đơn mua hàng',
      'Phiếu nhận hàng',
      'Hóa đơn mua',
      'Trả hàng / giảm giá',
      'Phân bổ chi phí mua',
    ]);
  });
  it('nhóm bán hàng hiện theo quyền bán hàng', () => {
    const groups = visibleNavGroups((permission) =>
      ['sales-quote:view', 'sales-order:view', 'sales-delivery:view', 'sales-report:view'].includes(
        permission,
      ),
    );

    const sales = groups.find((group) => group.key === 'sales');
    expect(sales?.label).toBe('Bán hàng');
    expect(sales?.items.map((item) => item.label)).toEqual([
      'Báo giá',
      'Đơn bán hàng',
      'Giao hàng',
      'Báo cáo doanh số',
    ]);
  });
});
