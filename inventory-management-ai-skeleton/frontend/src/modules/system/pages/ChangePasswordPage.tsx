import { Alert, App, Button, Card, Form, Input, Space, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../shared/auth/authContext';
import { applyServerErrors } from '../../../shared/forms';

interface ChangePasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/** Đổi mật khẩu; bắt buộc ở lần đăng nhập đầu hoặc sau khi được đặt lại (docs/07 mục 1.2). */
export function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<ChangePasswordForm>();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const forced = user?.mustChangePassword ?? false;

  const submit = async (values: ChangePasswordForm) => {
    setSubmitting(true);
    setError(null);
    try {
      await changePassword(values.currentPassword, values.newPassword);
      message.success('Đã đổi mật khẩu. Các phiên đăng nhập khác đã bị đăng xuất.');
      navigate('/', { replace: true });
    } catch (err) {
      setError(applyServerErrors(form, err, ['currentPassword', 'newPassword']));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{ minHeight: forced ? '100vh' : undefined, display: 'grid', placeItems: 'center', padding: 16 }}
    >
      <Card style={{ width: '100%', maxWidth: 420 }}>
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          Đổi mật khẩu
        </Typography.Title>
        {forced && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Bạn cần đổi mật khẩu trước khi sử dụng hệ thống."
          />
        )}
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form form={form} layout="vertical" onFinish={submit} disabled={submitting}>
          <Form.Item
            name="currentPassword"
            label="Mật khẩu hiện tại"
            rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="Mật khẩu mới"
            extra="Tối thiểu 8 ký tự, có chữ và số, không trùng tên đăng nhập."
            rules={[{ required: true, message: 'Nhập mật khẩu mới' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="Nhập lại mật khẩu mới"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Nhập lại mật khẩu mới' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || value === getFieldValue('newPassword')
                    ? Promise.resolve()
                    : Promise.reject(new Error('Mật khẩu nhập lại không khớp')),
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={submitting}>
              Đổi mật khẩu
            </Button>
            {forced ? (
              <Button onClick={() => logout()}>Đăng xuất</Button>
            ) : (
              <Button onClick={() => navigate(-1)}>Quay lại</Button>
            )}
          </Space>
        </Form>
      </Card>
    </div>
  );
}
