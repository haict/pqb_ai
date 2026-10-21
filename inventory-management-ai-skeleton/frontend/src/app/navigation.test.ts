import { BRANCH_VISIBLE } from '../shared/branch';
import { visibleNavGroups } from './navigation';

describe('visibleNavGroups', () => {
  it('chỉ hiện mục người dùng có quyền xem', () => {
    const groups = visibleNavGroups((permission) => ['company:view', 'department:view'].includes(permission));

    expect(groups.map((group) => group.key)).toEqual(['system']);
    expect(groups.find((group) => group.key === 'system')?.items.map((item) => item.label)).toEqual([
      'Công ty',
      'Phòng ban',
    ]);
  });

  it('menu Chi nhánh ẩn khi tạm ẩn thông tin chi nhánh', () => {
    const groups = visibleNavGroups((permission) => permission === 'branch:view');

    expect(groups.find((group) => group.key === 'system')?.items.map((item) => item.path) ?? []).toEqual(
      BRANCH_VISIBLE ? ['/system/branches'] : [],
    );
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

  it('ẩn cả nhóm khi không có quyền nào trong nhóm; không còn mục việc chờ duyệt (D-61)', () => {
    expect(visibleNavGroups(() => false)).toEqual([]);
  });
  it('nhóm mua hàng chỉ còn đơn mua, trả hàng, phân bổ chi phí (D-54)', () => {
    const groups = visibleNavGroups((permission) =>
      ['purchase-order:view', 'purchase-return:view', 'purchase-cost-allocation:view'].includes(permission),
    );

    const purchasing = groups.find((group) => group.key === 'purchasing');
    expect(purchasing?.label).toBe('Mua hàng');
    expect(purchasing?.items.map((item) => item.label)).toEqual([
      'Đơn mua hàng',
      'Trả hàng / giảm giá',
      'Phân bổ chi phí mua',
    ]);
  });
  it('nhóm bán hàng chỉ còn đơn bán, trả hàng, hàng lỗi, báo cáo (D-54)', () => {
    const groups = visibleNavGroups((permission) =>
      ['sales-order:view', 'sales-return:view', 'sales-complaint:view', 'sales-report:view'].includes(
        permission,
      ),
    );

    const sales = groups.find((group) => group.key === 'sales');
    expect(sales?.label).toBe('Bán hàng');
    expect(sales?.items.map((item) => item.label)).toEqual([
      'Đơn bán hàng',
      'Trả hàng / giảm giá bán',
      'Hàng lỗi khách báo',
      'Báo cáo doanh số',
    ]);
  });

  it('BR-RPT-26: sổ chi tiết bán hàng hiện theo quyền xem sổ riêng', () => {
    const only = visibleNavGroups((permission) => permission === 'sales-ledger:view');
    expect(only.find((group) => group.key === 'sales')?.items.map((item) => item.label)).toEqual([
      'Sổ chi tiết bán hàng',
    ]);
    const without = visibleNavGroups((permission) => permission === 'sales-report:view');
    expect(without.find((group) => group.key === 'sales')?.items.map((item) => item.path)).not.toContain(
      '/sales/ledger',
    );
  });

  it('sổ chi tiết mua hàng hiện theo quyền xem sổ riêng', () => {
    const groups = visibleNavGroups((permission) => permission === 'purchase-ledger:view');
    expect(groups.find((group) => group.key === 'purchasing')?.items.map((item) => item.label)).toEqual([
      'Sổ chi tiết mua hàng',
    ]);
  });

  it('nhóm Báo cáo (M10) hiện theo quyền xem báo cáo kho', () => {
    const groups = visibleNavGroups((permission) => permission === 'inventory-report:view');
    expect(groups.find((group) => group.key === 'reporting')?.items.map((item) => item.label)).toEqual([
      'Thẻ kho',
      'Nhập - xuất - tồn',
      'Tồn lâu, ngoại lệ, thiếu hồ sơ',
      'Truy xuất lô / serial',
    ]);
  });

  it('báo cáo mua / bán (10B) hiện theo quyền báo cáo mua, báo cáo bán', () => {
    const labels = (permission: string) =>
      visibleNavGroups((code) => code === permission)
        .find((group) => group.key === 'reporting')
        ?.items.map((item) => item.label);
    expect(labels('purchase-report:view')).toEqual([
      'Tình hình thực hiện đơn mua',
      'Giá mua, hàng lỗi theo NCC',
    ]);
    expect(labels('sales-order-report:view')).toEqual([
      'Tình hình thực hiện đơn bán',
      'Sổ nhật ký bán hàng',
      'Tồn so với đặt hàng chưa giao',
    ]);
    expect(labels('cash-forecast:view')).toEqual(['Dòng tiền dự kiến']);
  });
});
