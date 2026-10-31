import {
  BookOutlined,
  CarOutlined,
  CheckOutlined,
  DeleteOutlined,
  ImportOutlined,
  InboxOutlined,
  LockOutlined,
  PlayCircleOutlined,
  PushpinOutlined,
  RollbackOutlined,
  StopOutlined,
  SwapOutlined,
  UndoOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import { Alert, App, Button, DatePicker, Form, Input, Modal, Space, Tooltip } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState, type ReactNode } from 'react';
import { newIdempotencyKey } from '../../../shared/api/httpClient';
import { ApiError } from '../../../shared/api/apiError';
import { validated } from '../../../shared/forms';
import { ACTION_LABELS, REASON_ACTIONS } from '../labels';

interface Props {
  actions: string[];
  /** Hành động hiển thị theo thứ tự này; hành động không có trong {@code actions} bị ẩn. */
  order?: string[];
  onAction(action: string, body: object | undefined, idempotencyKey: string): Promise<void>;
  /** Chỉ hiện biểu tượng, tên thao tác ở tooltip (dùng trong cột thao tác của danh sách). */
  iconOnly?: boolean;
}

interface Pending {
  action: string;
  key: string;
}

const DEFAULT_ORDER = ['start', 'activate', 'post', 'receive', 'reverse', 'close', 'cancel'];
const DATE_ACTIONS = new Set(['reverse', 'receive', 'fail']);
const DANGER_ACTIONS = new Set(['cancel', 'delete', 'reverse', 'unpost']);

/** Biểu tượng theo ý nghĩa hành động; aria-hidden để tên nút (đọc màn hình, test) chỉ là nhãn tiếng Việt. */
const ACTION_ICONS: Record<string, ReactNode> = {
  start: <PlayCircleOutlined aria-hidden />,
  activate: <CheckOutlined aria-hidden />,
  post: <BookOutlined aria-hidden />,
  receive: <InboxOutlined aria-hidden />,
  reverse: <RollbackOutlined aria-hidden />,
  unpost: <UndoOutlined aria-hidden />,
  close: <LockOutlined aria-hidden />,
  cancel: <StopOutlined aria-hidden />,
  delete: <DeleteOutlined aria-hidden />,
  'stock-in': <ImportOutlined aria-hidden />,
  reserve: <PushpinOutlined aria-hidden />,
  release: <UnlockOutlined aria-hidden />,
  ship: <CarOutlined aria-hidden />,
  'create-exchange-order': <SwapOutlined aria-hidden />,
};

/**
 * Nút thao tác chứng từ theo trạng thái và quyền (server trả {@code allowedActions}; server vẫn kiểm tra lại).
 * Mọi hành động cần xác nhận; từ chối / hủy / đảo / đóng bắt buộc lý do. Một Idempotency-Key cho mỗi lần mở hộp
 * thoại, giữ nguyên khi gửi lại sau lỗi (frontend.md mục 5).
 */
export function DocumentActions({ actions, order = DEFAULT_ORDER, onAction, iconOnly }: Props) {
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
      <Space wrap={!iconOnly} size={iconOnly ? 0 : undefined}>
        {visible.map((action) =>
          iconOnly ? (
            <Tooltip key={action} title={ACTION_LABELS[action] ?? action}>
              <Button
                type="text"
                size="small"
                danger={DANGER_ACTIONS.has(action)}
                icon={ACTION_ICONS[action]}
                aria-label={ACTION_LABELS[action] ?? action}
                onClick={() => open(action)}
              />
            </Tooltip>
          ) : (
            <Button
              key={action}
              type={action === 'post' || action === 'activate' ? 'primary' : 'default'}
              danger={DANGER_ACTIONS.has(action)}
              icon={ACTION_ICONS[action]}
              onClick={() => open(action)}
            >
              {ACTION_LABELS[action] ?? action}
            </Button>
          ),
        )}
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
        {pending?.action === 'delete' && (
          <Alert
            type="warning"
            showIcon
            message="Chứng từ sẽ bị xóa hẳn, không khôi phục được."
            style={{ marginBottom: 12 }}
          />
        )}
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
