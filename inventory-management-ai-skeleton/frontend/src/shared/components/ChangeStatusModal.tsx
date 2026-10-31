import { Alert, Form, Input, Modal } from 'antd';
import { useState, type ReactNode } from 'react';
import type { RecordStatus } from './StatusTag';

interface Props {
  open: boolean;
  /** Tên bản ghi hiển thị trong câu hỏi xác nhận. */
  label: string;
  currentStatus: RecordStatus;
  onConfirm(nextStatus: RecordStatus, reason: string): Promise<void>;
  onCancel(): void;
  /** Lưu ý thêm khi ngừng hoạt động (ví dụ nhóm hàng chưa dùng sẽ bị xóa hẳn). */
  deactivateNote?: ReactNode;
}

/** Xác nhận kích hoạt / ngừng hoạt động; ngừng hoạt động bắt buộc lý do (frontend.md mục 3). */
export function ChangeStatusModal({
  open,
  label,
  currentStatus,
  onConfirm,
  onCancel,
  deactivateNote,
}: Props) {
  const [form] = Form.useForm<{ reason: string }>();
  const [submitting, setSubmitting] = useState(false);
  const nextStatus: RecordStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  const deactivating = nextStatus === 'INACTIVE';

  const submit = async () => {
    const { reason } = await form.validateFields();
    setSubmitting(true);
    try {
      await onConfirm(nextStatus, reason ?? '');
      form.resetFields();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={deactivating ? 'Ngừng hoạt động' : 'Kích hoạt lại'}
      okText={deactivating ? 'Ngừng hoạt động' : 'Kích hoạt'}
      okButtonProps={{ danger: deactivating, loading: submitting }}
      cancelText="Hủy"
      onOk={submit}
      onCancel={() => {
        form.resetFields();
        onCancel();
      }}
      destroyOnHidden
    >
      <p>
        {deactivating ? 'Ngừng hoạt động' : 'Kích hoạt lại'} <b>{label}</b>?
      </p>
      {deactivating && deactivateNote && (
        <Alert type="warning" showIcon message={deactivateNote} style={{ marginBottom: 12 }} />
      )}
      <Form form={form} layout="vertical">
        <Form.Item
          name="reason"
          label="Lý do"
          rules={
            deactivating ? [{ required: true, whitespace: true, message: 'Nhập lý do ngừng hoạt động' }] : []
          }
        >
          <Input.TextArea rows={2} maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
