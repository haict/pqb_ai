import { visibleNavGroups } from './navigation';

describe('visibleNavGroups', () => {
  it('chỉ hiện mục người dùng có quyền xem', () => {
    const groups = visibleNavGroups((permission) => ['branch:view', 'department:view'].includes(permission));

    expect(groups).toHaveLength(1);
    expect(groups[0]?.items.map((item) => item.label)).toEqual(['Chi nhánh', 'Phòng ban']);
  });

  it('ẩn cả nhóm khi không có quyền nào trong nhóm', () => {
    expect(visibleNavGroups(() => false)).toEqual([]);
  });
});
