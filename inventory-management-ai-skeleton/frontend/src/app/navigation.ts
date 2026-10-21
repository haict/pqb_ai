import { whenBranchVisible } from '../shared/branch';

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
    key: 'purchasing',
    label: 'Mua hàng',
    items: [
      { path: '/purchasing/orders', label: 'Đơn mua hàng', permission: 'purchase-order:view' },
      { path: '/purchasing/returns', label: 'Trả hàng / giảm giá', permission: 'purchase-return:view' },
      {
        path: '/purchasing/cost-allocations',
        label: 'Phân bổ chi phí mua',
        permission: 'purchase-cost-allocation:view',
      },
      { path: '/purchasing/ledger', label: 'Sổ chi tiết mua hàng', permission: 'purchase-ledger:view' },
    ],
  },
  {
    key: 'sales',
    label: 'Bán hàng',
    items: [
      { path: '/sales/orders', label: 'Đơn bán hàng', permission: 'sales-order:view' },
      { path: '/sales/returns', label: 'Trả hàng / giảm giá bán', permission: 'sales-return:view' },
      { path: '/sales/complaints', label: 'Hàng lỗi khách báo', permission: 'sales-complaint:view' },
      { path: '/sales/report', label: 'Báo cáo doanh số', permission: 'sales-report:view' },
      { path: '/sales/ledger', label: 'Sổ chi tiết bán hàng', permission: 'sales-ledger:view' },
    ],
  },
  {
    key: 'inventory',
    label: 'Kho',
    items: [
      { path: '/inventory/stock', label: 'Tồn kho', permission: 'inventory:view' },
      { path: '/inventory/documents', label: 'Chứng từ kho', permission: 'inventory:view' },
      { path: '/inventory/opening-stock', label: 'Tồn kho đầu kỳ', permission: 'inventory-receipt:view' },
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
    key: 'finance',
    label: 'Công nợ, quỹ',
    items: [
      { path: '/finance/cash-documents', label: 'Thu chi, ngân hàng', permission: 'cash-document:view' },
      { path: '/finance/cash-book', label: 'Sổ quỹ, dòng tiền', permission: 'cash-book:view' },
      { path: '/finance/cash-counts', label: 'Kiểm kê quỹ', permission: 'cash-count:view' },
      { path: '/finance/receivables', label: 'Công nợ phải thu', permission: 'debt:view' },
      { path: '/finance/payables', label: 'Công nợ phải trả', permission: 'debt:view' },
      { path: '/finance/advances', label: 'Công nợ tạm ứng', permission: 'debt:view' },
      { path: '/finance/debt-offsets', label: 'Bù trừ công nợ', permission: 'debt-offset:view' },
      { path: '/finance/debt-adjustments', label: 'Điều chỉnh công nợ', permission: 'debt-adjustment:view' },
      {
        path: '/finance/advance-settlements',
        label: 'Thanh toán tạm ứng',
        permission: 'debt-adjustment:view',
      },
      {
        path: '/finance/reconciliations',
        label: 'Đối chiếu công nợ',
        permission: 'debt-reconciliation:view',
      },
      { path: '/finance/debt-openings', label: 'Công nợ đầu kỳ', permission: 'debt-opening:view' },
      { path: '/finance/cash-accounts', label: 'Quỹ, tài khoản ngân hàng', permission: 'cash-account:view' },
    ],
  },
  {
    key: 'accounting',
    label: 'Kế toán',
    items: [
      { path: '/accounting/journal-entries', label: 'Bút toán', permission: 'journal:view' },
      { path: '/accounting/accounts', label: 'Hệ thống tài khoản', permission: 'account:view' },
      { path: '/accounting/posting-rules', label: 'Quy tắc hạch toán', permission: 'posting-rule:view' },
    ],
  },
  {
    key: 'reporting',
    label: 'Báo cáo',
    items: [
      { path: '/reports/stock-card', label: 'Thẻ kho', permission: 'inventory-report:view' },
      {
        path: '/reports/inventory-movement',
        label: 'Nhập - xuất - tồn',
        permission: 'inventory-report:view',
      },
      {
        path: '/reports/stock-alerts',
        label: 'Tồn lâu, ngoại lệ, thiếu hồ sơ',
        permission: 'inventory-report:view',
      },
      { path: '/reports/traceability', label: 'Truy xuất lô / serial', permission: 'inventory-report:view' },
      {
        path: '/reports/purchase-order-progress',
        label: 'Tình hình thực hiện đơn mua',
        permission: 'purchase-report:view',
      },
      {
        path: '/reports/purchase-analysis',
        label: 'Giá mua, hàng lỗi theo NCC',
        permission: 'purchase-report:view',
      },
      {
        path: '/reports/sales-order-progress',
        label: 'Tình hình thực hiện đơn bán',
        permission: 'sales-order-report:view',
      },
      { path: '/reports/sales-journal', label: 'Sổ nhật ký bán hàng', permission: 'sales-order-report:view' },
      {
        path: '/reports/stock-backlog',
        label: 'Tồn so với đặt hàng chưa giao',
        permission: 'sales-order-report:view',
      },
      { path: '/reports/cash-flow-forecast', label: 'Dòng tiền dự kiến', permission: 'cash-forecast:view' },
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
      ...whenBranchVisible<NavItem>({
        path: '/system/branches',
        label: 'Chi nhánh',
        permission: 'branch:view',
      }),
      { path: '/system/departments', label: 'Phòng ban', permission: 'department:view' },
      { path: '/system/users', label: 'Người dùng', permission: 'user:view' },
      { path: '/system/roles', label: 'Vai trò và phân quyền', permission: 'role:view' },
      { path: '/system/login-logs', label: 'Nhật ký đăng nhập', permission: 'login-log:view' },
      { path: '/system/fiscal-periods', label: 'Kỳ kế toán, kỳ kho', permission: 'period:view' },
      { path: '/system/parameters', label: 'Tham số hệ thống', permission: 'parameter:view' },
      { path: '/system/license', label: 'Bản quyền phần mềm', permission: 'license:manage' },
      { path: '/system/document-number-formats', label: 'Số chứng từ', permission: 'document-number:view' },
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
