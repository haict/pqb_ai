import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { authValue, testUser, WithAuth } from '../../test/authTestUtils';
import type { AuthContextValue } from './authContext';
import { RequireAuth, RequirePermission } from './RouteGuards';

function renderAt(path: string, value: AuthContextValue) {
  return render(
    <WithAuth value={value}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<div>Trang đăng nhập</div>} />
          <Route path="/change-password" element={<div>Trang đổi mật khẩu</div>} />
          <Route
            path="/users"
            element={
              <RequireAuth>
                <RequirePermission permission="user:view">
                  <div>Danh sách người dùng</div>
                </RequirePermission>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>
    </WithAuth>,
  );
}

describe('RouteGuards', () => {
  it('chưa đăng nhập thì chuyển về trang đăng nhập', () => {
    renderAt('/users', authValue({ status: 'anonymous', user: null }));
    expect(screen.getByText('Trang đăng nhập')).toBeInTheDocument();
  });

  it('phải đổi mật khẩu thì chuyển sang trang đổi mật khẩu', () => {
    renderAt('/users', authValue({ user: { ...testUser, mustChangePassword: true } }));
    expect(screen.getByText('Trang đổi mật khẩu')).toBeInTheDocument();
  });

  it('thiếu quyền xem thì hiện trang 403, không hiện dữ liệu', () => {
    renderAt('/users', authValue());
    expect(screen.getByText('Không có quyền truy cập')).toBeInTheDocument();
    expect(screen.queryByText('Danh sách người dùng')).not.toBeInTheDocument();
  });

  it('đủ quyền thì hiện trang', () => {
    renderAt('/users', authValue({ user: { ...testUser, permissions: ['user:view'] } }));
    expect(screen.getByText('Danh sách người dùng')).toBeInTheDocument();
  });
});
