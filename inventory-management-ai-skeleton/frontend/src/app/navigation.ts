import { whenBranchVisible } from '../shared/branch';
import { ALL_ENABLED, type UiFeatures } from '../shared/ui/uiFeatures';

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

/**
 * Nhóm và màn hình không cho ẩn bằng cấu hình (D-72) để quản trị không tự khóa mình khỏi hệ thống; bỏ qua dòng
 * tương ứng nếu có trong sys_ui_features.
 */
const UNHIDEABLE_GROUPS = new Set(['system']);
const UNHIDEABLE_PATHS = new Set([
  '/system/companies',
  '/system/branches',
  '/system/users',
  '/system/roles',
  '/system/license',
]);

export const groupFeatureKey = (groupKey: string) => `group:${groupKey}`;
export const menuFeatureKey = (path: string) => `menu:${path}`;

function groupEnabled(group: NavGroup, features: UiFeatures): boolean {
  return UNHIDEABLE_GROUPS.has(group.key) || features.isEnabled(groupFeatureKey(group.key));
}

function itemEnabled(item: NavItem, features: UiFeatures): boolean {
  return UNHIDEABLE_PATHS.has(item.path) || features.isEnabled(menuFeatureKey(item.path));
}

/**
 * Nhóm menu người dùng được thấy: theo quyền, rồi theo cấu hình ẩn / đổi tên của khách hàng (D-72); nhóm không còn
 * mục nào thì ẩn.
 */
export function visibleNavGroups(
  can: (permission: string) => boolean,
  features: UiFeatures = ALL_ENABLED,
): NavGroup[] {
  return NAV_GROUPS.filter((group) => groupEnabled(group, features))
    .map((group) => ({
      ...group,
      label: features.label(groupFeatureKey(group.key), group.label),
      items: group.items
        .filter((item) => !item.permission || can(item.permission))
        .filter((item) => itemEnabled(item, features))
        .map((item) => ({ ...item, label: features.label(menuFeatureKey(item.path), item.label) })),
    }))
    .filter((group) => group.items.length > 0);
}

/**
 * Màn hình (kể cả trang chi tiết có đường dẫn con) bị ẩn theo cấu hình (D-72): mục menu có đường dẫn là tiền tố dài
 * nhất của {@code pathname} bị tắt, hoặc nhóm chứa nó bị tắt. Đường dẫn không thuộc menu: không bị ẩn.
 */
export function isPathHidden(pathname: string, features: UiFeatures): boolean {
  const match = NAV_GROUPS.flatMap((group) => group.items.map((item) => ({ group, item })))
    .filter(({ item }) => pathname === item.path || pathname.startsWith(`${item.path}/`))
    .sort((a, b) => b.item.path.length - a.item.path.length)[0];
  return !!match && (!groupEnabled(match.group, features) || !itemEnabled(match.item, features));
}
