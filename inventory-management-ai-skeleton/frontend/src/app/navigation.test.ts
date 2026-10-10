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

  it('ẩn cả nhóm khi không có quyền nào trong nhóm; việc chờ duyệt luôn hiện', () => {
    expect(visibleNavGroups(() => false).map((group) => group.key)).toEqual(['work']);
  });
});
