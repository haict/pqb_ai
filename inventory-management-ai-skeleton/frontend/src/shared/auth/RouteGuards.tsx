import { Button, Result, Spin } from 'antd';
import type { ReactNode } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, useCan } from './authContext';

/** Chỉ cho người đã đăng nhập; phải đổi mật khẩu thì chuyển sang trang đổi mật khẩu. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" tip="Đang tải phiên làm việc...">
          <div style={{ width: 200 }} />
        </Spin>
      </div>
    );
  }
  if (status === 'anonymous' || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  return <>{children}</>;
}

/** Ẩn trang khi thiếu quyền xem; server vẫn trả 403 nếu gọi API trực tiếp. */
export function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const can = useCan();
  const navigate = useNavigate();
  if (!can(permission)) {
    return (
      <Result
        status="403"
        title="Không có quyền truy cập"
        subTitle="Bạn không có quyền xem chức năng này. Liên hệ quản trị hệ thống nếu cần."
        extra={
          <Button type="primary" onClick={() => navigate('/')}>
            Về trang chủ
          </Button>
        }
      />
    );
  }
  return <>{children}</>;
}
