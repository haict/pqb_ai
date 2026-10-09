/** Mục menu và quyền xem tương ứng. Chỉ liệt kê chức năng đã triển khai (frontend.md mục 4). */
export interface NavItem {
  path: string;
  label: string;
  permission?: string;
}

export interface NavGroup {
  key: string;
  label: string;
  items: NavItem[];
}

export const NAV_GROUPS: NavGroup[] = [
  {
    key: 'system',
    label: 'Quản trị hệ thống',
    items: [
      { path: '/system/companies', label: 'Công ty', permission: 'company:view' },
      { path: '/system/branches', label: 'Chi nhánh', permission: 'branch:view' },
      { path: '/system/departments', label: 'Phòng ban', permission: 'department:view' },
      { path: '/system/users', label: 'Người dùng', permission: 'user:view' },
      { path: '/system/roles', label: 'Vai trò và phân quyền', permission: 'role:view' },
      { path: '/system/login-logs', label: 'Nhật ký đăng nhập', permission: 'login-log:view' },
    ],
  },
];

/** Nhóm menu người dùng được thấy; nhóm không còn mục nào thì ẩn. */
export function visibleNavGroups(can: (permission: string) => boolean): NavGroup[] {
  return NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.permission || can(item.permission)),
  })).filter((group) => group.items.length > 0);
}
