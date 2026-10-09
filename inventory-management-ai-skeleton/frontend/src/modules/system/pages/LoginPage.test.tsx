import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApiError } from '../../../shared/api/apiError';
import { authValue, testUser, WithAuth } from '../../../test/authTestUtils';
import { LoginPage } from './LoginPage';

function renderLogin(login: ReturnType<typeof vi.fn>) {
  return render(
    <WithAuth value={authValue({ status: 'anonymous', user: null, login })}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div>Trang chủ đã mở</div>} />
          <Route path="/change-password" element={<div>Trang đổi mật khẩu</div>} />
        </Routes>
      </MemoryRouter>
    </WithAuth>,
  );
}

function submit(username: string, password: string) {
  fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: username } });
  fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: /Đăng nhập/ }));
}

describe('LoginPage', () => {
  it('đăng nhập thành công thì vào trang chủ', async () => {
    const login = vi.fn().mockResolvedValue(testUser);
    renderLogin(login);

    submit(' admin ', 'Erp@2026');

    expect(await screen.findByText('Trang chủ đã mở')).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith('admin', 'Erp@2026');
  });

  it('người dùng phải đổi mật khẩu thì chuyển sang trang đổi mật khẩu', async () => {
    const login = vi.fn().mockResolvedValue({ ...testUser, mustChangePassword: true });
    renderLogin(login);

    submit('nv01', 'Erp@2026');

    expect(await screen.findByText('Trang đổi mật khẩu')).toBeInTheDocument();
  });

  it('sai thông tin thì hiện thông báo của máy chủ', async () => {
    const login = vi
      .fn()
      .mockRejectedValue(new ApiError('Tên đăng nhập hoặc mật khẩu không đúng.', 'INVALID_CREDENTIALS', 401));
    renderLogin(login);

    submit('admin', 'sai');

    await waitFor(() =>
      expect(screen.getByText('Tên đăng nhập hoặc mật khẩu không đúng.')).toBeInTheDocument(),
    );
  });
});
