import { Alert, App, Button, DatePicker, Form, Input, Modal, Space } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { newIdempotencyKey } from '../../../shared/api/httpClient';
import { ApiError } from '../../../shared/api/apiError';
import { validated } from '../../../shared/forms';
import { ACTION_LABELS, REASON_ACTIONS } from '../labels';

interface Props {
  actions: string[];
  /** Hành động hiển thị theo thứ tự này; hành động không có trong {@code actions} bị ẩn. */
  order?: string[];
  onAction(action: string, body: object | undefined, idempotencyKey: string): Promise<void>;
}

interface Pending {
  action: string;
  key: string;
}

const DEFAULT_ORDER = [
  'start',
  'submit',
  'approve',
  'reject',
  'post',
  'receive',
  'reverse',
  'close',
  'cancel',
];
const DATE_ACTIONS = new Set(['reverse', 'receive', 'fail']);

/**
 * Nút thao tác chứng từ theo trạng thái và quyền (server trả {@code allowedActions}; server vẫn kiểm tra lại).
 * Mọi hành động cần xác nhận; từ chối / hủy / đảo / đóng bắt buộc lý do. Một Idempotency-Key cho mỗi lần mở hộp
 * thoại, giữ nguyên khi gửi lại sau lỗi (frontend.md mục 5).
 */
export function DocumentActions({ actions, order = DEFAULT_ORDER, onAction }: Props) {
  const { message } = App.useApp();
  const [pending, setPending] = useState<Pending | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<{ reason?: string; postingDate?: Dayjs }>();

  const open = (action: string) => {
    form.resetFields();
    setError(null);
    setPending({ action, key: newIdempotencyKey() });
  };

  const confirm = async () => {
    if (!pending) {
      return;
    }
    const values = await validated(form);
    if (!values) {
      return;
    }
    const body: Record<string, unknown> = {};
    if (values.reason) {
      body.reason = values.reason.trim();
    }
    if (values.postingDate) {
      body.postingDate = values.postingDate.format('YYYY-MM-DD');
    }
    setSubmitting(true);
    setError(null);
    try {
      await onAction(pending.action, body, pending.key);
      message.success(`${ACTION_LABELS[pending.action] ?? pending.action}: thành công.`);
      setPending(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Đã xảy ra lỗi không xác định.');
    } finally {
      setSubmitting(false);
    }
  };

  const visible = order.filter((action) => actions.includes(action));
  if (visible.length === 0) {
    return null;
  }
  return (
    <>
      <Space wrap>
        {visible.map((action) => (
          <Button
            key={action}
            type={action === 'post' || action === 'approve' ? 'primary' : 'default'}
            danger={action === 'cancel' || action === 'reject' || action === 'reverse'}
            onClick={() => open(action)}
          >
            {ACTION_LABELS[action] ?? action}
          </Button>
        ))}
      </Space>
      <Modal
        open={pending !== null}
        title={pending ? `Xác nhận: ${ACTION_LABELS[pending.action] ?? pending.action}` : ''}
        okText="Xác nhận"
        cancelText="Đóng"
        okButtonProps={{ loading: submitting }}
        onOk={confirm}
        onCancel={() => setPending(null)}
        destroyOnHidden
      >
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
        <Form form={form} layout="vertical" disabled={submitting}>
          {pending && REASON_ACTIONS.has(pending.action) && (
            <Form.Item
              name="reason"
              label="Lý do"
              rules={[{ required: true, whitespace: true, message: 'Nhập lý do' }]}
            >
              <Input.TextArea rows={3} maxLength={500} />
            </Form.Item>
          )}
          {pending && DATE_ACTIONS.has(pending.action) && (
            <Form.Item name="postingDate" label="Ngày hạch toán" extra="Để trống: ngày hiện tại.">
              <DatePicker format="DD/MM/YYYY" />
            </Form.Item>
          )}
          {pending && !REASON_ACTIONS.has(pending.action) && !DATE_ACTIONS.has(pending.action) && (
            <p>Thực hiện thao tác này với chứng từ?</p>
          )}
        </Form>
      </Modal>
    </>
  );
}
