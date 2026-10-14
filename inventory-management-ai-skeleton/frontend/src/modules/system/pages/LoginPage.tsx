import {
  LockOutlined,
  LoginOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/apiError';
import { useAuth } from '../../../shared/auth/authContext';
import { BrandLogo } from '../../../app/BrandLogo';
import { BRAND } from '../../../app/theme';

const HIGHLIGHTS = [
  { icon: <SafetyCertificateOutlined />, text: 'Quản lý lô, hạn dùng, serial thiết bị' },
  { icon: <TeamOutlined />, text: 'Mua hàng, bán hàng, đấu thầu trên một hệ thống' },
  { icon: <LockOutlined />, text: 'Phân quyền theo vai trò, chi nhánh, kho' },
];

interface LoginForm {
  username: string;
  password: string;
}

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  if (status === 'authenticated') {
    return <Navigate to={from} replace />;
  }

  const submit = async (values: LoginForm) => {
    setSubmitting(true);
    setError(null);
    try {
      const user = await login(values.username.trim(), values.password);
      navigate(user.mustChangePassword ? '/change-password' : from, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không đăng nhập được, vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 16,
        background: `linear-gradient(135deg, ${BRAND.primarySoft} 0%, #f4f7f9 60%)`,
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 860,
          display: 'flex',
          flexWrap: 'wrap',
          borderRadius: 14,
          overflow: 'hidden',
          boxShadow: '0 12px 32px rgba(10, 92, 100, 0.15)',
          background: '#fff',
        }}
      >
        <div
          style={{
            flex: '1 1 300px',
            padding: 32,
            color: '#fff',
            background: `linear-gradient(160deg, ${BRAND.primary}, ${BRAND.primaryDark})`,
          }}
        >
          <BrandLogo inverse />
          <Typography.Title level={3} style={{ color: '#fff', marginTop: 32 }}>
            Quản trị doanh nghiệp thiết bị, vật tư y tế
          </Typography.Title>
          {HIGHLIGHTS.map((item) => (
            <div key={item.text} style={{ display: 'flex', gap: 10, marginTop: 14, opacity: 0.92 }}>
              {item.icon}
              <span>{item.text}</span>
            </div>
          ))}
        </div>
        <Card variant="borderless" style={{ flex: '1 1 320px', padding: 8 }}>
          <Typography.Title level={3} style={{ marginTop: 0 }}>
            ERP Thiết bị - Vật tư y tế
          </Typography.Title>
          <Typography.Paragraph type="secondary">Đăng nhập để tiếp tục</Typography.Paragraph>
          {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
          <Form<LoginForm> layout="vertical" onFinish={submit} disabled={submitting} requiredMark={false}>
            <Form.Item
              name="username"
              label="Tên đăng nhập"
              rules={[{ required: true, message: 'Nhập tên đăng nhập' }]}
            >
              <Input
                prefix={<UserOutlined />}
                autoComplete="username"
                autoFocus
                maxLength={50}
                size="large"
              />
            </Form.Item>
            <Form.Item
              name="password"
              label="Mật khẩu"
              rules={[{ required: true, message: 'Nhập mật khẩu' }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                autoComplete="current-password"
                maxLength={200}
                size="large"
              />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              block
              size="large"
              icon={<LoginOutlined />}
              loading={submitting}
            >
              Đăng nhập
            </Button>
          </Form>
        </Card>
      </div>
    </div>
  );
}
