import { Alert, Form, Modal, type FormInstance } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { useIdempotencyKey } from '../api/useIdempotencyKey';
import { applyServerErrors, validated } from '../forms';

interface Props<T extends object> {
  open: boolean;
  title: string;
  okText?: string;
  width?: number;
  initialValues?: Partial<T>;
  /** Tên các trường trên form để gắn lỗi server đúng chỗ. */
  fieldNames: string[];
  onSubmit(values: T, idempotencyKey: string): Promise<void>;
  onClose(): void;
  children: ReactNode | ((form: FormInstance<T>) => ReactNode);
}

/**
 * Modal form tạo/sửa: vô hiệu nút khi đang gửi, giữ cùng Idempotency-Key khi gửi lại sau lỗi,
 * key mới mỗi lần mở (BR-GEN-50, frontend.md mục 5).
 */
export function FormModal<T extends object>({
  open,
  title,
  okText = 'Lưu',
  width = 560,
  initialValues,
  fieldNames,
  onSubmit,
  onClose,
  children,
}: Props<T>) {
  const [form] = Form.useForm<T>();
  const { key, renew } = useIdempotencyKey();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      renew();
      setError(null);
      form.resetFields();
    }
  }, [open, renew, form]);

  const submit = async () => {
    const values = await validated(form);
    if (!values) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(values, key);
      onClose();
    } catch (err) {
      setError(applyServerErrors(form, err, fieldNames));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      width={width}
      okText={okText}
      cancelText="Hủy"
      okButtonProps={{ loading: submitting }}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
      forceRender
      // forceRender tạo portal trước Drawer chi tiết (cùng z-index 1000) nên form "Sửa" mở từ Drawer bị che; đặt cao
      // hơn Drawer, vẫn dưới dropdown / date picker (1050).
      zIndex={1010}
      footer={(_, { OkBtn, CancelBtn }) => (
        <>
          {error && (
            <Alert type="error" showIcon message={error} style={{ marginBottom: 8, textAlign: 'left' }} />
          )}
          <CancelBtn />
          <OkBtn />
        </>
      )}
    >
      <Form form={form} layout="vertical" initialValues={initialValues} disabled={submitting}>
        {typeof children === 'function' ? children(form) : children}
      </Form>
    </Modal>
  );
}
