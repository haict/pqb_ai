import type { ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { AccountsPage } from '../modules/accounting/pages/AccountsPage';
import { JournalEntriesPage } from '../modules/accounting/pages/JournalEntriesPage';
import { PostingRulesPage } from '../modules/accounting/pages/PostingRulesPage';
import { CashAccountsPage } from '../modules/finance/pages/CashAccountsPage';
import { CashBookPage } from '../modules/finance/pages/CashBookPage';
import { CashCountsPage } from '../modules/finance/pages/CashCountsPage';
import { CashDocumentsPage } from '../modules/finance/pages/CashDocumentsPage';
import { DebtAdjustmentsPage } from '../modules/finance/pages/DebtAdjustmentsPage';
import { DebtOffsetsPage } from '../modules/finance/pages/DebtOffsetsPage';
import { DebtOpeningsPage } from '../modules/finance/pages/DebtOpeningsPage';
import { DebtReconciliationsPage } from '../modules/finance/pages/DebtReconciliationsPage';
import { DebtsPage } from '../modules/finance/pages/DebtsPage';
import { CostAllocationsPage } from '../modules/purchasing/pages/CostAllocationsPage';
import { PurchaseOrdersPage } from '../modules/purchasing/pages/PurchaseOrdersPage';
import { PurchaseReturnsPage } from '../modules/purchasing/pages/PurchaseReturnsPage';
import { PurchaseLedgerPage } from '../modules/purchasing/pages/PurchaseLedgerPage';
import { InventoryMovementPage } from '../modules/reporting/pages/InventoryMovementPage';
import { OrderProgressPage } from '../modules/reporting/pages/OrderProgressPage';
import { PurchaseAnalysisPage } from '../modules/reporting/pages/PurchaseAnalysisPage';
import { SalesJournalPage } from '../modules/reporting/pages/SalesJournalPage';
import { StockBacklogPage } from '../modules/reporting/pages/StockBacklogPage';
import { CashForecastPage } from '../modules/reporting/pages/CashForecastPage';
import { StockAlertsPage } from '../modules/reporting/pages/StockAlertsPage';
import { StockCardPage } from '../modules/reporting/pages/StockCardPage';
import { TraceabilityPage } from '../modules/reporting/pages/TraceabilityPage';
import { SalesComplaintsPage } from '../modules/sales/pages/SalesComplaintsPage';
import { SalesOrdersPage } from '../modules/sales/pages/SalesOrdersPage';
import { SalesReportPage } from '../modules/sales/pages/SalesReportPage';
import { SalesLedgerPage } from '../modules/sales/pages/SalesLedgerPage';
import { SalesReturnsPage } from '../modules/sales/pages/SalesReturnsPage';
import { registerInventoryDocumentRoutes } from '../modules/inventory/documentRoutes';
import { InventoryDocumentDetailPage } from '../modules/inventory/pages/InventoryDocumentDetailPage';
import { InventoryDocumentsPage } from '../modules/inventory/pages/InventoryDocumentsPage';
import { LotsSerialsPage } from '../modules/inventory/pages/LotsSerialsPage';
import { RecallsPage } from '../modules/inventory/pages/RecallsPage';
import { StockCountsPage } from '../modules/inventory/pages/StockCountsPage';
import { StockIssuePoliciesPage } from '../modules/inventory/pages/StockIssuePoliciesPage';
import { StockPage } from '../modules/inventory/pages/StockPage';
import { CatalogsPage } from '../modules/masterdata/pages/CatalogsPage';
import { CategoriesPage } from '../modules/masterdata/pages/CategoriesPage';
import { ComplianceDocumentsPage } from '../modules/masterdata/pages/ComplianceDocumentsPage';
import { PartnersPage } from '../modules/masterdata/pages/PartnersPage';
import { PriceListsPage } from '../modules/masterdata/pages/PriceListsPage';
import { ProductsPage } from '../modules/masterdata/pages/ProductsPage';
import { WarehousesPage } from '../modules/masterdata/pages/WarehousesPage';
import { ApprovalsPage } from '../modules/system/pages/ApprovalsPage';
import { ApprovalWorkflowsPage } from '../modules/system/pages/ApprovalWorkflowsPage';
import { BranchesPage } from '../modules/system/pages/BranchesPage';
import { ChangePasswordPage } from '../modules/system/pages/ChangePasswordPage';
import { CompaniesPage } from '../modules/system/pages/CompaniesPage';
import { DelegationsPage } from '../modules/system/pages/DelegationsPage';
import { DepartmentsPage } from '../modules/system/pages/DepartmentsPage';
import { DocumentTypesPage } from '../modules/system/pages/DocumentTypesPage';
import { DocumentNumberFormatsPage } from '../modules/system/pages/DocumentNumberFormatsPage';
import { FiscalPeriodsPage } from '../modules/system/pages/FiscalPeriodsPage';
import { LoginLogsPage } from '../modules/system/pages/LoginLogsPage';
import { LoginPage } from '../modules/system/pages/LoginPage';
import { ParametersPage } from '../modules/system/pages/ParametersPage';
import { RolesPage } from '../modules/system/pages/RolesPage';
import { UsersPage } from '../modules/system/pages/UsersPage';
import { RequireAuth, RequirePermission } from '../shared/auth/RouteGuards';
import { AppLayout } from './AppLayout';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';

registerInventoryDocumentRoutes();

/** Xem chứng từ kho: một trong các quyền xem theo loại chứng từ (server kiểm tra quyền theo loại). */
const INVENTORY_DOCUMENT_VIEW = 'inventory:view';

const guarded = (permission: string, page: ReactNode) => (
  <RequirePermission permission={permission}>{page}</RequirePermission>
);

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/change-password',
    element: (
      <RequireAuth>
        <ChangePasswordPage />
      </RequireAuth>
    ),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: 'approvals', element: <ApprovalsPage /> },
      { path: 'inventory/stock', element: guarded('inventory:view', <StockPage />) },
      { path: 'purchasing/orders', element: guarded('purchase-order:view', <PurchaseOrdersPage />) },
      { path: 'purchasing/returns', element: guarded('purchase-return:view', <PurchaseReturnsPage />) },
      {
        path: 'purchasing/cost-allocations',
        element: guarded('purchase-cost-allocation:view', <CostAllocationsPage />),
      },
      { path: 'purchasing/ledger', element: guarded('purchase-ledger:view', <PurchaseLedgerPage />) },
      { path: 'reports/stock-card', element: guarded('inventory-report:view', <StockCardPage />) },
      {
        path: 'reports/inventory-movement',
        element: guarded('inventory-report:view', <InventoryMovementPage />),
      },
      { path: 'reports/stock-alerts', element: guarded('inventory-report:view', <StockAlertsPage />) },
      { path: 'reports/traceability', element: guarded('inventory-report:view', <TraceabilityPage />) },
      {
        path: 'reports/purchase-order-progress',
        element: guarded('purchase-report:view', <OrderProgressPage kind="purchase" />),
      },
      {
        path: 'reports/purchase-analysis',
        element: guarded('purchase-report:view', <PurchaseAnalysisPage />),
      },
      {
        path: 'reports/sales-order-progress',
        element: guarded('sales-order-report:view', <OrderProgressPage kind="sales" />),
      },
      { path: 'reports/sales-journal', element: guarded('sales-order-report:view', <SalesJournalPage />) },
      { path: 'reports/stock-backlog', element: guarded('sales-order-report:view', <StockBacklogPage />) },
      { path: 'reports/cash-flow-forecast', element: guarded('cash-forecast:view', <CashForecastPage />) },
      { path: 'sales/orders', element: guarded('sales-order:view', <SalesOrdersPage />) },
      { path: 'sales/returns', element: guarded('sales-return:view', <SalesReturnsPage />) },
      { path: 'sales/complaints', element: guarded('sales-complaint:view', <SalesComplaintsPage />) },
      { path: 'sales/report', element: guarded('sales-report:view', <SalesReportPage />) },
      { path: 'sales/ledger', element: guarded('sales-ledger:view', <SalesLedgerPage />) },
      { path: 'inventory/documents', element: guarded(INVENTORY_DOCUMENT_VIEW, <InventoryDocumentsPage />) },
      {
        path: 'inventory/documents/:id',
        element: guarded(INVENTORY_DOCUMENT_VIEW, <InventoryDocumentDetailPage />),
      },
      {
        path: 'inventory/opening-stock',
        element: guarded('inventory-receipt:view', <InventoryDocumentsPage opening />),
      },
      { path: 'inventory/lots', element: guarded('inventory:view', <LotsSerialsPage />) },
      { path: 'inventory/stock-counts', element: guarded('stock-count:view', <StockCountsPage />) },
      { path: 'inventory/recalls', element: guarded('recall:view', <RecallsPage />) },
      {
        path: 'inventory/stock-issue-policies',
        element: guarded('stock-policy:view', <StockIssuePoliciesPage />),
      },
      {
        path: 'finance/receivables',
        element: guarded('debt:view', <DebtsPage key="ar" debtType="RECEIVABLE" />),
      },
      { path: 'finance/payables', element: guarded('debt:view', <DebtsPage key="ap" debtType="PAYABLE" />) },
      { path: 'finance/debt-openings', element: guarded('debt-opening:view', <DebtOpeningsPage />) },
      { path: 'finance/cash-documents', element: guarded('cash-document:view', <CashDocumentsPage />) },
      { path: 'finance/cash-book', element: guarded('cash-book:view', <CashBookPage />) },
      { path: 'finance/cash-accounts', element: guarded('cash-account:view', <CashAccountsPage />) },
      { path: 'finance/cash-counts', element: guarded('cash-count:view', <CashCountsPage />) },
      {
        path: 'finance/advances',
        element: guarded('debt:view', <DebtsPage key="advance" debtType="ADVANCE" />),
      },
      { path: 'finance/debt-offsets', element: guarded('debt-offset:view', <DebtOffsetsPage />) },
      {
        path: 'finance/debt-adjustments',
        element: guarded('debt-adjustment:view', <DebtAdjustmentsPage key="DCCN" documentType="DCCN" />),
      },
      {
        path: 'finance/advance-settlements',
        element: guarded('debt-adjustment:view', <DebtAdjustmentsPage key="TTTU" documentType="TTTU" />),
      },
      {
        path: 'finance/reconciliations',
        element: guarded('debt-reconciliation:view', <DebtReconciliationsPage />),
      },
      { path: 'accounting/journal-entries', element: guarded('journal:view', <JournalEntriesPage />) },
      { path: 'accounting/accounts', element: guarded('account:view', <AccountsPage />) },
      { path: 'accounting/posting-rules', element: guarded('posting-rule:view', <PostingRulesPage />) },
      { path: 'masterdata/products', element: guarded('product:view', <ProductsPage />) },
      { path: 'masterdata/categories', element: guarded('catalog:view', <CategoriesPage />) },
      { path: 'masterdata/partners', element: guarded('partner:view', <PartnersPage />) },
      { path: 'masterdata/warehouses', element: guarded('warehouse:view', <WarehousesPage />) },
      { path: 'masterdata/price-lists', element: guarded('price:view', <PriceListsPage />) },
      {
        path: 'masterdata/compliance-documents',
        element: guarded('compliance:view', <ComplianceDocumentsPage />),
      },
      { path: 'masterdata/catalogs', element: guarded('catalog:view', <CatalogsPage />) },
      { path: 'approval-delegations', element: <DelegationsPage /> },
      { path: 'system/document-types', element: guarded('approval-workflow:view', <DocumentTypesPage />) },
      {
        path: 'system/approval-workflows',
        element: guarded('approval-workflow:view', <ApprovalWorkflowsPage />),
      },
      { path: 'system/companies', element: guarded('company:view', <CompaniesPage />) },
      { path: 'system/branches', element: guarded('branch:view', <BranchesPage />) },
      { path: 'system/departments', element: guarded('department:view', <DepartmentsPage />) },
      { path: 'system/users', element: guarded('user:view', <UsersPage />) },
      { path: 'system/roles', element: guarded('role:view', <RolesPage />) },
      { path: 'system/login-logs', element: guarded('login-log:view', <LoginLogsPage />) },
      { path: 'system/fiscal-periods', element: guarded('period:view', <FiscalPeriodsPage />) },
      { path: 'system/parameters', element: guarded('parameter:view', <ParametersPage />) },
      {
        path: 'system/document-number-formats',
        element: guarded('document-number:view', <DocumentNumberFormatsPage />),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
