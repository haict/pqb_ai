import type { ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';
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
      { path: 'inventory/documents', element: guarded(INVENTORY_DOCUMENT_VIEW, <InventoryDocumentsPage />) },
      {
        path: 'inventory/documents/:id',
        element: guarded(INVENTORY_DOCUMENT_VIEW, <InventoryDocumentDetailPage />),
      },
      { path: 'inventory/lots', element: guarded('inventory:view', <LotsSerialsPage />) },
      { path: 'inventory/stock-counts', element: guarded('stock-count:view', <StockCountsPage />) },
      { path: 'inventory/recalls', element: guarded('recall:view', <RecallsPage />) },
      {
        path: 'inventory/stock-issue-policies',
        element: guarded('stock-policy:view', <StockIssuePoliciesPage />),
      },
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
