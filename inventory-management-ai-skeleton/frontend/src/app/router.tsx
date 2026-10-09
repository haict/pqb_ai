import type { ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { BranchesPage } from '../modules/system/pages/BranchesPage';
import { ChangePasswordPage } from '../modules/system/pages/ChangePasswordPage';
import { CompaniesPage } from '../modules/system/pages/CompaniesPage';
import { DepartmentsPage } from '../modules/system/pages/DepartmentsPage';
import { LoginLogsPage } from '../modules/system/pages/LoginLogsPage';
import { LoginPage } from '../modules/system/pages/LoginPage';
import { RolesPage } from '../modules/system/pages/RolesPage';
import { UsersPage } from '../modules/system/pages/UsersPage';
import { RequireAuth, RequirePermission } from '../shared/auth/RouteGuards';
import { AppLayout } from './AppLayout';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';

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
      { path: 'system/companies', element: guarded('company:view', <CompaniesPage />) },
      { path: 'system/branches', element: guarded('branch:view', <BranchesPage />) },
      { path: 'system/departments', element: guarded('department:view', <DepartmentsPage />) },
      { path: 'system/users', element: guarded('user:view', <UsersPage />) },
      { path: 'system/roles', element: guarded('role:view', <RolesPage />) },
      { path: 'system/login-logs', element: guarded('login-log:view', <LoginLogsPage />) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
