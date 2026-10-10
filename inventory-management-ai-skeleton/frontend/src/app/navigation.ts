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
    key: 'work',
    label: 'Công việc',
    items: [
      { path: '/approvals', label: 'Việc chờ duyệt' },
      { path: '/approval-delegations', label: 'Ủy quyền duyệt' },
    ],
  },
  {
    key: 'inventory',
    label: 'Kho',
    items: [
      { path: '/inventory/stock', label: 'Tồn kho', permission: 'inventory:view' },
      { path: '/inventory/documents', label: 'Chứng từ kho', permission: 'inventory:view' },
      { path: '/inventory/lots', label: 'Lô, serial', permission: 'inventory:view' },
      { path: '/inventory/stock-counts', label: 'Kiểm kê', permission: 'stock-count:view' },
      { path: '/inventory/recalls', label: 'Thu hồi', permission: 'recall:view' },
      {
        path: '/inventory/stock-issue-policies',
        label: 'Chính sách xuất vượt tồn',
        permission: 'stock-policy:view',
      },
    ],
  },
  {
    key: 'masterdata',
    label: 'Danh mục',
    items: [
      { path: '/masterdata/products', label: 'Hàng hóa', permission: 'product:view' },
      { path: '/masterdata/categories', label: 'Nhóm hàng', permission: 'catalog:view' },
      { path: '/masterdata/partners', label: 'Đối tác', permission: 'partner:view' },
      { path: '/masterdata/warehouses', label: 'Kho, vị trí', permission: 'warehouse:view' },
      { path: '/masterdata/price-lists', label: 'Bảng giá', permission: 'price:view' },
      { path: '/masterdata/compliance-documents', label: 'Hồ sơ, giấy phép', permission: 'compliance:view' },
      { path: '/masterdata/catalogs', label: 'Danh mục chung', permission: 'catalog:view' },
    ],
  },
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
      { path: '/system/fiscal-periods', label: 'Kỳ kế toán, kỳ kho', permission: 'period:view' },
      { path: '/system/parameters', label: 'Tham số hệ thống', permission: 'parameter:view' },
      { path: '/system/document-number-formats', label: 'Số chứng từ', permission: 'document-number:view' },
      { path: '/system/document-types', label: 'Loại chứng từ', permission: 'approval-workflow:view' },
      { path: '/system/approval-workflows', label: 'Luồng duyệt', permission: 'approval-workflow:view' },
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
